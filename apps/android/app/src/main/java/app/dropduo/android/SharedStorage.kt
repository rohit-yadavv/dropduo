package app.dropduo.android

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/** Puts received files where people look for them: photos and videos in the gallery, everything else in Downloads › DropDuo. */
object SharedStorage {
    private const val FOLDER = "DropDuo"
    private const val VOLUME = MediaStore.VOLUME_EXTERNAL_PRIMARY

    fun mime(name: String): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

    /** Where a received row was saved, for telling the user. */
    fun place(row: Transfer) = when {
        row.saved == null -> null
        row.folder -> "Downloads › " + row.saved.removePrefix("${Environment.DIRECTORY_DOWNLOADS}/").replace("/", " › ")
        mime(row.name)?.let { it.startsWith("image/") || it.startsWith("video/") } == true -> "your gallery"
        else -> "Downloads › $FOLDER"
    }

    /**
     * Copies a received file or folder into shared storage; returns a content URI for a file, or the folder's path, like "Download/DropDuo/Photos".
     * Saves run one at a time; two at once can pick the same name and mix their files together.
     */
    @Synchronized fun save(context: Context, source: File, name: String, folder: Boolean): String {
        if (!folder) return copy(context, source, name, null).toString()
        val base = unique(context, name)
        val saved = mutableListOf<Uri>()
        try {
            // Shared storage has no empty folders; each file brings its folders along.
            source.walkTopDown().onFail { _, e -> throw e }.filter { it.isFile }.forEach { file ->
                saved += copy(context, file, file.name, listOf(base, file.parentFile!!.relativeTo(source).path).filter { it.isNotEmpty() }.joinToString("/"))
            }
        } catch (e: Exception) { saved.forEach { runCatching { context.contentResolver.delete(it, null, null) } }; throw e }
        return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$base"
    }

    /** [dir] is a folder inside Downloads › DropDuo; without one, photos and videos go to the gallery. */
    private fun copy(context: Context, source: File, name: String, dir: String?): Uri {
        val resolver = context.contentResolver
        val mime = mime(name)
        val (collection, top) = when {
            dir == null && mime?.startsWith("image/") == true -> MediaStore.Images.Media.getContentUri(VOLUME) to Environment.DIRECTORY_PICTURES
            dir == null && mime?.startsWith("video/") == true -> MediaStore.Video.Media.getContentUri(VOLUME) to Environment.DIRECTORY_MOVIES
            else -> MediaStore.Downloads.getContentUri(VOLUME) to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            mime?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
            put(MediaStore.MediaColumns.RELATIVE_PATH, listOfNotNull(top, FOLDER, dir).joinToString("/"))
            // Other apps see the file only once it's complete.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("Couldn't save $name to this phone")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out, 1 shl 16) } } ?: error("Couldn't save $name to this phone")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) { runCatching { resolver.delete(uri, null, null) }; throw e }
        return uri
    }

    /** "Photos", or "Photos (2)" when DropDuo already saved a folder by that name. */
    private fun unique(context: Context, name: String) = generateSequence(1) { it + 1 }.map { if (it == 1) name else "$name ($it)" }.first { candidate ->
        context.contentResolver.query(MediaStore.Downloads.getContentUri(VOLUME), arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$candidate/%"), null)?.use { it.count == 0 } ?: true
    }
}
