/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.core.search.viewmodel

import android.content.Intent
import com.jraska.livedata.test
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.TestScheduler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.base.SideEffect
import org.kiwix.kiwixmobile.core.dao.NewRecentSearchDao
import org.kiwix.kiwixmobile.core.reader.ZimReaderContainer
import org.kiwix.kiwixmobile.core.search.adapter.SearchListItem.RecentSearchListItem
import org.kiwix.kiwixmobile.core.search.adapter.SearchListItem.ZimSearchResultListItem
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ActivityResultReceived
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ClickedSearchInText
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ConfirmedDelete
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.CreatedWithIntent
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ExitedSearch
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.Filter
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.OnItemClick
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.OnItemLongClick
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ReceivedPromptForSpeechInput
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.StartSpeechInputFailed
import org.kiwix.kiwixmobile.core.search.viewmodel.State.NoResults
import org.kiwix.kiwixmobile.core.search.viewmodel.State.Results
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.DeleteRecentSearch
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.Finish
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.OpenSearchItem
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.ProcessActivityResult
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.SaveSearchToRecents
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.SearchInPreviousScreen
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.SearchIntentProcessing
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.ShowDeleteSearchDialog
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.ShowToast
import org.kiwix.kiwixmobile.core.search.viewmodel.effects.StartSpeechInput
import org.kiwix.sharedFunctions.InstantExecutorExtension
import org.kiwix.sharedFunctions.resetSchedulers
import org.kiwix.sharedFunctions.setScheduler
import java.util.concurrent.TimeUnit.MILLISECONDS

@ExtendWith(InstantExecutorExtension::class)
internal class SearchViewModelTest {
  private val recentSearchDao: NewRecentSearchDao = mockk()
  private val zimReaderContainer: ZimReaderContainer = mockk()
  private val searchResultGenerator: SearchResultGenerator = mockk()

  lateinit var viewModel: SearchViewModel

  private val testScheduler = TestScheduler()

  init {
    setScheduler(testScheduler)
  }

  @AfterAll
  fun teardown() {
    resetSchedulers()
  }

  private val recentsFromDb: PublishProcessor<List<RecentSearchListItem>> =
    PublishProcessor.create()

  @BeforeEach
  fun init() {
    clearAllMocks()
    every { zimReaderContainer.id } returns "id"
    every { recentSearchDao.recentSearches("id") } returns recentsFromDb
    viewModel = SearchViewModel(recentSearchDao, zimReaderContainer, searchResultGenerator)
  }

  @Nested
  inner class StateTests {
    @Test
    fun `initial state is Initialising`() {
      viewModel.state.test().assertValue(NoResults(""))
    }

    @Test
    fun `non empty search term with search results shows Results`() {
      val item = ZimSearchResultListItem("")
      val searchTerm = "searchTerm"
      emissionOf(
        searchTerm = searchTerm,
        searchResults = listOf(item),
        databaseResults = listOf(RecentSearchListItem(""))
      )
      resultsIn(Results(searchTerm, listOf(item)))
    }

    @Test
    fun `non empty search string with no search results is NoResults`() {
      emissionOf(
        searchTerm = "a",
        searchResults = emptyList(),
        databaseResults = listOf(RecentSearchListItem(""))
      )
      resultsIn(NoResults("a"))
    }

    @Test
    fun `empty search string with database results shows Results`() {
      val item = RecentSearchListItem("")
      emissionOf(
        searchTerm = "",
        searchResults = listOf(ZimSearchResultListItem("")),
        databaseResults = listOf(item)
      )
      resultsIn(Results("", listOf(item)))
    }

    @Test
    fun `empty search string with no database results is NoResults`() {
      emissionOf(
        searchTerm = "",
        searchResults = listOf(ZimSearchResultListItem("")),
        databaseResults = emptyList()
      )
      resultsIn(NoResults(""))
    }

    @Test
    fun `duplicate search terms are ignored`() {
      val searchString = "a"
      val item = ZimSearchResultListItem("")
      emissionOf(
        searchTerm = searchString,
        searchResults = listOf(item),
        databaseResults = emptyList()
      )
      viewModel.actions.offer(Filter(searchString))
      viewModel.state.test()
        .also { testScheduler.advanceTimeBy(100, MILLISECONDS) }
        .assertValueHistory(NoResults(""), Results(searchString, listOf(item)))
    }

    @Test
    fun `only latest search term is used`() {
      val item = ZimSearchResultListItem("")
      emissionOf(
        searchTerm = "a",
        searchResults = listOf(item),
        databaseResults = emptyList()
      )
      emissionOf(
        searchTerm = "b",
        searchResults = listOf(item),
        databaseResults = emptyList()
      )
      viewModel.state.test()
        .also { testScheduler.advanceTimeBy(100, MILLISECONDS) }
        .assertValueHistory(NoResults(""), Results("b", listOf(item)))
    }
  }

  @Nested
  inner class ActionMapping {
    @Test
    fun `ExitedSearch offers Finish`() {
      actionResultsInEffects(ExitedSearch, Finish)
    }

    @Test
    fun `OnItemClick offers Saves and Opens`() {
      val searchListItem = RecentSearchListItem("")
      actionResultsInEffects(
        OnItemClick(searchListItem),
        SaveSearchToRecents(recentSearchDao, searchListItem, "id"),
        OpenSearchItem(searchListItem)
      )
    }

    @Test
    fun `OnItemLongClick offers Saves and Opens`() {
      val searchListItem = RecentSearchListItem("")
      actionResultsInEffects(
        OnItemLongClick(searchListItem),
        ShowDeleteSearchDialog(searchListItem, viewModel.actions)
      )
    }

    @Test
    fun `ClickedSearchInText offers SearchInPreviousScreen`() {
      actionResultsInEffects(ClickedSearchInText, SearchInPreviousScreen(""))
    }

    @Test
    fun `ConfirmedDelete offers Delete and Toast`() {
      val searchListItem = RecentSearchListItem("")
      actionResultsInEffects(
        ConfirmedDelete(searchListItem),
        DeleteRecentSearch(searchListItem, recentSearchDao),
        ShowToast(R.string.delete_specific_search_toast)
      )
    }

    @Test
    fun `CreatedWithIntent offers SearchIntentProcessing`() {
      val intent = mockk<Intent>()
      actionResultsInEffects(
        CreatedWithIntent(intent),
        SearchIntentProcessing(intent, viewModel.actions)
      )
    }

    @Test
    fun `ReceivedPromptForSpeechInput offers SearchIntentProcessing`() {
      actionResultsInEffects(
        ReceivedPromptForSpeechInput,
        StartSpeechInput(viewModel.actions)
      )
    }

    @Test
    fun `StartSpeechInputFailed offers ShowToast`() {
      actionResultsInEffects(
        StartSpeechInputFailed,
        ShowToast(string.speech_not_supported)
      )
    }

    @Test
    fun `ActivityResultReceived offers ProcessActivityResult`() {
      actionResultsInEffects(
        ActivityResultReceived(0, 1, null),
        ProcessActivityResult(0, 1, null, viewModel.actions)
      )
    }

    private fun actionResultsInEffects(
      action: Action,
      vararg effects: SideEffect<*>
    ) {
      viewModel.effects
        .test()
        .also { viewModel.actions.offer(action) }
        .assertValues(*effects)
    }
  }

  private fun resultsIn(st: State) {
    viewModel.state.test()
      .also { testScheduler.advanceTimeBy(100, MILLISECONDS) }
      .assertValue(st)
  }

  private fun emissionOf(
    searchTerm: String,
    searchResults: List<ZimSearchResultListItem>,
    databaseResults: List<RecentSearchListItem>
  ) {
    every { searchResultGenerator.generateSearchResults(searchTerm) } returns searchResults
    viewModel.actions.offer(Filter(searchTerm))
    recentsFromDb.offer(databaseResults)
  }
}
