package open.source.streamingbox

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import open.source.streamingbox.http.HttpStreamProvider
import open.source.streamingbox.media.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

object StreamingBox {

    private const val TAG = "StreamingBox"

    private fun openChannel(
        context: Context,
        file: File,
    ): SeekableByteChannel {
        return IoCompat.newEncryptedFile(context, file).openSeekableByteChannel()
    }

    @JvmStatic
    @JvmOverloads
    fun openOutputStream(
        context: Context,
        file: File,
        append: Boolean = false,
    ): OutputStream {
        return IoCompat.newEncryptedFile(context, file).openOutputStream(append)
    }

    @JvmStatic
    fun openInputStream(
        context: Context,
        file: File,
    ): InputStream {
        return IoCompat.newEncryptedFile(context, file).openInputStream()
    }

    @JvmStatic
    fun openMediaDataSource(
        context: Context,
        file: File,
    ): IMediaDataSource {
        val impl = EncryptedMediaDataSource(openChannel(context, file))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MediaDataSourceCompat(impl)
        } else {
            impl
        }
    }

    @JvmStatic
    fun openLocalHttpUri(context: Context, file: File): Uri {
        return HttpStreamProvider.getUri(context, file)
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun openFileDescriptor(context: Context, file: File): ParcelFileDescriptor {
        val m = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val callback = MediaProxyFdCallback(openMediaDataSource(context, file))
        return m.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            callback,
            callback.handler
        )
    }
}