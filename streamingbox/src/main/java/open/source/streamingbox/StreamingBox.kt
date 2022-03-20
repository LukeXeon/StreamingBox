package open.source.streamingbox

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedMediaFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

object StreamingBox {

    private const val TAG = "StreamingBox"

    private fun newEncryptedFile(
        context: Context,
        file: File,
    ): EncryptedMediaFile {
        IoCompat.install(context)
        return EncryptedMediaFile.Builder(
            context, file,
            MasterKey.Builder(context)
                .setRequestStrongBoxBacked(true)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedMediaFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    private fun openChannel(
        context: Context,
        file: File,
    ): SeekableByteChannel {
        return newEncryptedFile(context, file).openSeekableByteChannel()
    }

    @JvmStatic
    @JvmOverloads
    fun openOutputStream(
        context: Context,
        file: File,
        append: Boolean = false,
    ): OutputStream {
        return newEncryptedFile(context, file).openOutputStream(append)
    }

    @JvmStatic
    fun openInputStream(
        context: Context,
        file: File,
    ): InputStream {
        return newEncryptedFile(context, file).openInputStream()
    }

    @JvmStatic
    fun openMediaDataSource(
        context: Context,
        file: File,
    ): IMediaDataSource {
        val impl = MediaDataSourceImpl(openChannel(context, file))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MediaDataSourceCompat(impl)
        } else {
            impl
        }
    }

    @JvmStatic
    fun openHTTPStream(context: Context, file: File): Uri {
        return HttpStreamProvider.mapping(context, file)
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