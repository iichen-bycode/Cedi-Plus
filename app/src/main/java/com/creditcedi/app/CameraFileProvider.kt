package com.creditcedi.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Grants the system camera temporary write access to an app-cache photo without storage permissions. */
class CameraFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fileFor(uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = fileFor(uri)
        val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns.toTypedArray()).apply {
            addRow(columns.map { column ->
                when (column) {
                    OpenableColumns.DISPLAY_NAME -> file.name
                    OpenableColumns.SIZE -> file.length()
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun getType(uri: Uri) = "image/jpeg"

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        if (fileFor(uri).delete()) 1 else 0

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("Camera files are created by the app.")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = throw UnsupportedOperationException("Camera files are immutable metadata entries.")

    private fun fileFor(uri: Uri): File {
        if (uri.pathSegments.size != 2 || uri.pathSegments[0] != "camera") {
            throw FileNotFoundException("Invalid camera file URI.")
        }
        val name = uri.pathSegments[1]
        if (!name.matches(Regex("cedi_[0-9]+\\.jpg"))) throw FileNotFoundException("Invalid camera file name.")
        val root = File(requireNotNull(context).cacheDir, "camera-photos").canonicalFile
        val file = File(root, name).canonicalFile
        if (file.parentFile != root || !file.isFile) throw FileNotFoundException("Camera file does not exist.")
        return file
    }
}
