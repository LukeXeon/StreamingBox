package open.source.streamingbox.media

import android.os.*
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
internal class MediaProxyFdCallback(
    private val dataSource: IMediaDataSource
) : ProxyFileDescriptorCallback() {

    val handler = Handler(
        HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }.looper
    )

    override fun onGetSize(): Long {
        return dataSource.getSize()
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        return dataSource.readAt(offset, data, 0, data.size)
    }

    override fun onRelease() {
        dataSource.runCatching { close() }
        handler.looper.quit()
    }
}