package open.source.streamingbox

import android.annotation.SuppressLint
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

internal class MediaDataSourceImpl(
    private val channel: SeekableByteChannel,
) : IMediaDataSource {

    private val lock = ByteBuffer.allocate(1)

    @SuppressLint("NewApi")
    override fun readAt(position: Long, buffer: ByteBuffer): Int {
        synchronized(lock) {
            channel.position(position)
            return channel.read(buffer)
        }
    }

    override fun close() {
        channel.close()
    }

    @SuppressLint("NewApi")
    override fun getSize(): Long {
        synchronized(lock) {
            val pos = channel.position()
            try {
                channel.position(0)
                return if (channel.read(lock) == -1) 0 else channel.size()
            } finally {
                channel.position(pos)
            }
        }
    }
}