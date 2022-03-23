package open.source.streamingbox

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import androidx.security.crypto.EncryptedMediaFile
import androidx.security.crypto.MasterKey
import open.source.streamingbox.http.HttpStreamProvider
import open.source.streamingbox.media.*
import open.source.streamingbox.crypto.EncryptedMediaDataSource
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
        return EncryptedMediaFile.Builder(
            context, file,
            MasterKey.Builder(context)
                .setRequestStrongBoxBacked(true)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedMediaFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    @JvmStatic
    fun openOutputStream(
        context: Context,
        file: File,
    ): OutputStream {
        return newEncryptedFile(context, file).openOutputStream()
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
        val impl = EncryptedMediaDataSource(openSeekableByteChannel(context, file))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MediaDataSourceCompat(impl)
        } else {
            impl
        }
    }

    @JvmStatic
    fun openLocalHttpStream(context: Context, file: File): Uri {
        return HttpStreamProvider.getUriFrom(context, file)
    }

    @JvmStatic
    fun openSeekableByteChannel(
        context: Context,
        file: File,
    ): SeekableByteChannel {
        return newEncryptedFile(context, file).openSeekableByteChannel()
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.M)
    fun openAndroidMediaDataSource(
        context: Context,
        file: File,
    ): MediaDataSource {
        return MediaDataSourceCompat(EncryptedMediaDataSource(openSeekableByteChannel(context,
            file)))
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun openParcelFileDescriptor(context: Context, file: File): ParcelFileDescriptor {
        val m = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val callback = MediaProxyFdCallback(openMediaDataSource(context, file))
        return m.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            callback,
            callback.handler
        )
    }
}