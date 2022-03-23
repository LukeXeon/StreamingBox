package open.source.streamingbox.media

import android.annotation.SuppressLint
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import kotlin.math.min

internal class EncryptedMediaDataSource(
    private val channel: SeekableByteChannel,
) : IMediaDataSource {

    private val buf1 = ByteBuffer.allocate(1)
    private var temp: WeakReference<ByteBuffer>? = null

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        synchronized(buf1) {
            var tmp = temp?.get()
            var changed = false
            tmp = if (tmp != null && tmp.hasArray() && tmp.array() === buffer) {
                tmp.apply {
                    clear()
                    position(offset)
                    limit(min(offset + size, capacity()))
                }
            } else {
                changed = true
                requireNotNull(ByteBuffer.wrap(buffer, offset, size))
            }
            val count = readAt(position, tmp)
            if (changed) {
                temp = WeakReference(tmp)
            }
            return count
        }
    }

    @SuppressLint("NewApi")
    override fun readAt(position: Long, buffer: ByteBuffer): Int {
        synchronized(buf1) {
            channel.position(position)
            return channel.read(buffer)
        }
    }

    override fun close() {
        channel.close()
    }

    @SuppressLint("NewApi")
    override fun getSize(): Long {
        synchronized(buf1) {
            val pos = channel.position()
            try {
                channel.position(0)
                return if (channel.read(buf1) == -1) 0 else channel.size()
            } finally {
                channel.position(pos)
            }
        }
    }
}