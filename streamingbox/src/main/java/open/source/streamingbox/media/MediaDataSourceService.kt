package open.source.streamingbox.media

import android.annotation.SuppressLint
import android.media.IMediaHTTPConnection
import android.media.IMediaHTTPService
import android.media.MediaHTTPConnection
import android.os.IBinder
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

@SuppressLint("DiscouragedPrivateApi")
class MediaDataSourceService(
    private val dataSource: IMediaDataSource,
) : IMediaHTTPService.Stub() {

    companion object {
        private val mURL by lazy {
            MediaHTTPConnection::class.java
                .getDeclaredField("mURL")
                .apply {
                    isAccessible = true
                }
        }

        private const val TYPE = "video/mp4"
        const val PATH = "http://www.streaming-box.tv"
    }

    private val target = URL(null, PATH, URLHandler())

    private inner class URLHandler : URLStreamHandler() {
        override fun openConnection(u: URL?): URLConnection {
            return DataConnection(u).apply {
                connect()
            }
        }

        override fun openConnection(u: URL?, p: java.net.Proxy?): URLConnection {
            return openConnection(u)
        }
    }

    private inner class DataConnection(u: URL?) : HttpURLConnection(u) {

        private var skip: Long = 0

        override fun connect() {
            connected = true
            responseCode = HTTP_OK
        }

        override fun disconnect() {
            connected = false
            responseCode = -1
            skip = 0
        }

        override fun getResponseCode(): Int {
            return responseCode
        }

        override fun setRequestProperty(key: String?, value: String?) {
            if (key.equals("Range", true) && !value.isNullOrEmpty()) {
                var range = value.substring(6)
                val index = range.indexOf('-')
                if (index > 0) {
                    range = range.substring(0, index)
                }
                skip = range.toLong()
                responseCode = if (skip > 0) HTTP_PARTIAL else HTTP_OK
            }
        }

        override fun getContentLength(): Int {
            return contentLengthLong.toInt()
        }

        override fun getContentLengthLong(): Long {
            return dataSource.getSize() - skip
        }

        override fun getHeaderField(name: String?): String? {
            if (name.equals("Content-Range", true)) {
                val size = contentLength
                return "bytes $skip-${(size - 1)}/${size}"
            }
            return null
        }

        override fun usingProxy(): Boolean = true

        override fun getInputStream(): InputStream {
            return DataStream()
        }

        private inner class DataStream : InputStream() {

            private var readCount: Long = 0
            private var buf1: ByteArray? = null

            @Synchronized
            override fun read(): Int {
                if (buf1 == null) buf1 = ByteArray(1)
                val n = this.read(buf1)
                return if (n == 1) requireNotNull(buf1)[0].toInt() and 0xff else -1
            }

            @Synchronized
            override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
                val count = dataSource.readAt((skip + readCount), buffer, offset, size)
                if (count > 0) {
                    readCount += count
                }
                return count
            }
        }
    }

    private inner class MediaConnection : MediaHTTPConnection() {
        @Synchronized
        override fun connect(uri: String?, headers: String?): IBinder {
            val binder = super.connect(uri, headers)
            mURL.set(this, target)
            return binder
        }

        @Synchronized
        override fun readAt(offset: Long, size: Int): Int {
            mURL.set(this, target)
            return super.readAt(offset, size)
        }

        override fun getMIMEType(): String {
            return TYPE
        }

        @Synchronized
        override fun getSize(): Long {
            mURL.set(this, target)
            return dataSource.getSize()
        }
    }

    override fun makeHTTPConnection(): IMediaHTTPConnection {
        return MediaConnection()
    }
}