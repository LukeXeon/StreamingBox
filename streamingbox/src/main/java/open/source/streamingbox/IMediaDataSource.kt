package open.source.streamingbox

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

interface IMediaDataSource : Closeable {

    fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int
    ): Int {
        return readAt(position, ByteBuffer.wrap(buffer, offset, size))
    }

    fun readAt(position: Long, buffer: ByteBuffer): Int

    fun getSize(): Long

    interface Provider {
        fun open(url: String): IMediaDataSource
    }
}