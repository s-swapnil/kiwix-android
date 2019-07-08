/*
 * Copyright 2013 Rashiq Ahmad <rashiq.z@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU  General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.kiwix.kiwixmobile.utils.files

import android.content.Context
import android.os.Environment
import android.provider.MediaStore.Files
import android.provider.MediaStore.MediaColumns
import eu.mhutti1.utils.storage.StorageDeviceUtils
import io.reactivex.Flowable
import io.reactivex.functions.BiFunction
import org.kiwix.kiwixmobile.extensions.forEachRow
import org.kiwix.kiwixmobile.extensions.get
import java.io.File
import javax.inject.Inject

class FileSearch @Inject constructor(private val context: Context) {

  val zimFileExtensions = arrayOf("zim", "zimaa")

  fun scan(defaultPath: String) =
    Flowable.combineLatest(
        Flowable.fromCallable { scanFileSystem(defaultPath) },
        Flowable.fromCallable(this::scanMediaStore),
        BiFunction<List<File>, List<File>, List<File>> { filesSystemFiles, mediaStoreFiles ->
          filesSystemFiles + mediaStoreFiles
        }
    )

  private fun scanMediaStore() = mutableListOf<File>().apply {
    queryMediaStore()
        ?.forEachRow { cursor ->
          File(cursor.get<String>(MediaColumns.DATA)).takeIf(File::canRead)
              ?.also { add(it) }
        }
  }

  private fun queryMediaStore() = context.contentResolver
      .query(
          Files.getContentUri("external"),
          arrayOf(MediaColumns.DATA),
          MediaColumns.DATA + " like ? or " + MediaColumns.DATA + " like ? ",
          arrayOf("%." + zimFileExtensions[0], "%." + zimFileExtensions[1]),
          null
      )

  private fun scanFileSystem(defaultPath: String) =
    directoryRoots(defaultPath)
        .minus(Environment.getExternalStorageDirectory().absolutePath)
        .fold(mutableListOf<File>(), { acc, root ->
          acc.apply { addAll(scanDirectory(root)) }
        })

  private fun directoryRoots(defaultPath: String) = listOf(
      "/mnt",
      defaultPath,
      *StorageDeviceUtils.getStorageDevices(context, false).map { it.name }.toTypedArray()
  )

  private fun scanDirectory(directory: String) = filesMatchingExtensions(directory) ?: emptyList()

  private fun filesMatchingExtensions(directory: String) = File(directory)
      .listFiles { _, name -> name.endsWithAny(*zimFileExtensions) }
      ?.toList()

}

private fun String.endsWithAny(vararg suffixes: String) =
  suffixes.fold(false, { acc, s -> acc or endsWith(s) })