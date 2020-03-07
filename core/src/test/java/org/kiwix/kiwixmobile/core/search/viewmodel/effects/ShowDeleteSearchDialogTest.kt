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

package org.kiwix.kiwixmobile.core.search.viewmodel.effects

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.processors.PublishProcessor
import org.junit.jupiter.api.Test
import org.kiwix.kiwixmobile.core.search.SearchActivity
import org.kiwix.kiwixmobile.core.search.adapter.SearchListItem.RecentSearchListItem
import org.kiwix.kiwixmobile.core.search.viewmodel.Action
import org.kiwix.kiwixmobile.core.search.viewmodel.Action.ConfirmedDelete
import org.kiwix.kiwixmobile.core.utils.DialogShower
import org.kiwix.kiwixmobile.core.utils.KiwixDialog.DeleteSearch

internal class ShowDeleteSearchDialogTest {

  @Test
  fun `invoke with shows dialog that offers ConfirmedDelete action`() {
    val actions = mockk<PublishProcessor<Action>>(relaxed = true)
    val searchListItem = RecentSearchListItem("")
    val activity = mockk<SearchActivity>()
    val showDeleteSearchDialog = ShowDeleteSearchDialog(searchListItem, actions)
    val dialogShower = mockk<DialogShower>()
    every { activity.activityComponent.inject(showDeleteSearchDialog) } answers {
      showDeleteSearchDialog.dialogShower = dialogShower
      Unit
    }
    val lambdaSlot = slot<() -> Unit>()
    showDeleteSearchDialog.invokeWith(activity)
    verify {
      dialogShower.show(DeleteSearch, capture(lambdaSlot))
    }
    lambdaSlot.captured.invoke()
    verify { actions.offer(ConfirmedDelete(searchListItem)) }
  }
}
