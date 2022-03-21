package open.source.streamingbox.http

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log
import libcore.net.NetworkSecurityPolicy
import open.source.streamingbox.R
import open.source.streamingbox.StreamingBox
import open.source.streamingbox.media.IMediaDataSource
import java.io.File
import java.io.FileNotFoundException

internal class HttpStreamProvider : ContentProvider(), IMediaDataSource.Provider {

    companion object {
        private const val KEY_FILE = "file"
        private const val SELECTION_FILE = "${KEY_FILE}=?"
        private const val TYPE = "video/mp4"
        private const val TAG = "HttpStreamProvider"

        fun getUriFrom(context: Context, file: File): Uri {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!android.security.NetworkSecurityPolicy.getInstance()
                        .isCleartextTrafficPermitted("localhost")
                ) {
                    try {
                        val delegate = NetworkSecurityPolicy.getInstance()
                        Log.d(TAG, "openLocalHttpUri: $delegate")
                        NetworkSecurityPolicy.setInstance(object : NetworkSecurityPolicy() {
                            override fun isCleartextTrafficPermitted(): Boolean {
                                return delegate.isCleartextTrafficPermitted
                            }

                            override fun isCleartextTrafficPermitted(hostname: String?): Boolean {
                                return delegate.isCleartextTrafficPermitted(hostname)
                                        || hostname.equals("localhost")
                            }
                        })
                    } catch (e: Throwable) {
                        val message = context.resources
                            .openRawResource(R.raw.streaming_box_network_security_error_message)
                            .use { it.reader().readText() }
                        throw AssertionError(message, e)
                    }
                }
            }
            val cursor = context.contentResolver.query(
                Uri.parse("content://${context.packageName}.streaming-box.http-stream-provider")
                    .buildUpon()
                    .appendQueryParameter(KEY_FILE, file.canonicalPath)
                    .build(),
                arrayOf(KEY_FILE),
                SELECTION_FILE,
                arrayOf(file.canonicalPath),
                null
            )
            if (cursor != null) {
                cursor.moveToFirst()
                val uri = Uri.parse(cursor.getString(0))
                cursor.close()
                return uri
            }
            throw FileNotFoundException(file.canonicalPath)
        }
    }

    private val extractor by lazy {
        HttpStreamExtractor(requireNotNull(context), this).apply { start() }
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
        sortOrder: String?,
    ): Cursor? {
        if (!projection.isNullOrEmpty()
            && projection.contains(KEY_FILE)
            && selection == SELECTION_FILE
            && !selectionArgs.isNullOrEmpty()
        ) {
            val index = projection.indexOf(KEY_FILE)
            val row = arrayOfNulls<Any>(projection.size)
            row[index] = Uri.parse("http://localhost:${extractor.listenPort}")
                .buildUpon()
                .appendQueryParameter(KEY_FILE, selectionArgs[index])
                .toString()
            return MatrixCursor(projection).apply { addRow(row) }
        }
        return null
    }

    override fun getType(uri: Uri): String = TYPE

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        throw UnsupportedOperationException()
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw UnsupportedOperationException()
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw UnsupportedOperationException()
    }

}