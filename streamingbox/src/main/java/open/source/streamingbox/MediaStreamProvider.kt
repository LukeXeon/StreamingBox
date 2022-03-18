package open.source.streamingbox

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.io.File
import java.io.FileNotFoundException

internal class MediaStreamProvider : ContentProvider(), IMediaDataSource.Provider {

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_FILE = "file"
        private const val TYPE = "video/mp4"
        private const val METHOD_MAPPING = "mapping"

        fun mapping(context: Context, file: File): Uri {
            val bundle = context.contentResolver.call(
                Uri.parse("content://${context.packageName}.streaming-box.media-stream-provider"),
                METHOD_MAPPING,
                null,
                Bundle().apply {
                    putParcelable(KEY_URI, Uri.parse(file.canonicalPath))
                }
            )
            return bundle?.getParcelable(KEY_URI) ?: throw FileNotFoundException()
        }
    }

    private val extractor by lazy {
        MediaStreamExtractor(requireNotNull(context), this).apply { start() }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == METHOD_MAPPING && extras != null && extras.containsKey(KEY_URI)) {
            val uri = requireNotNull(extras.getParcelable<Uri>(KEY_URI))
            return Bundle().apply {
                putParcelable(
                    KEY_URI, Uri.parse("http://localhost:${extractor.listenPort}")
                        .buildUpon()
                        .appendQueryParameter(KEY_FILE, uri.toString())
                        .build()
                )
            }
        }
        return null
    }

    override fun open(url: String): IMediaDataSource {
        return StreamingBox.openMediaDataSource(requireNotNull(context), File(url))
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        throw UnsupportedOperationException()
    }

    override fun getType(uri: Uri): String = TYPE

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException()
    }

}