package open.source.streamingbox.media

import java.io.Closeable
import java.nio.ByteBuffer

interface IMediaDataSource : Closeable {

    fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int

    fun readAt(position: Long, buffer: ByteBuffer): Int

    fun getSize(): Long

    interface Provider {
        fun open(url: String): IMediaDataSource
    }
}