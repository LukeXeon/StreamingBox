package open.source.streamingbox

import android.annotation.SuppressLint
import android.content.Context
import android.os.*
import android.util.Log
import dalvik.system.DexFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.*

@SuppressLint("DiscouragedPrivateApi")
internal object IoCompat {

    private const val TAG = "IoCompat"

    private const val PATCH_CLASS_NAME = "java.nio.channels.SeekableByteChannel"

    private val patchLock = BooleanArray(1)

    // lazy：防止jvm拒绝加载FileChannelCompat
    private val wrap0 by lazy<(FileChannel) -> FileChannel> {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                it
            } else {
                FileChannelCompat(it)
            }
        }
    }

    fun install(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            synchronized(patchLock) {
                if (!patchLock[0] && runCatching { Class.forName(PATCH_CLASS_NAME) }.isFailure) {
                    val dexBytes = context.resources
                        .openRawResource(R.raw.java_nio_channels_seekable_byte_channel)
                        .use { it.readBytes() }
                    val outputFile = File(context.codeCacheDir, "${PATCH_CLASS_NAME}.dex")
                    outputFile.apply {
                        createNewFile()
                        outputStream().use { output ->
                            output.channel.lock().use {
                                if (dexBytes.size.toLong() != outputFile.length()) {
                                    output.write(dexBytes)
                                }
                                DexFile(outputFile).loadClass(
                                    PATCH_CLASS_NAME,
                                    null
                                )
                            }
                        }
                    }
                    Log.d(TAG, "installed !")
                    patchLock[0] = true
                }
            }
        }
    }

    fun wrap(channel: FileChannel): FileChannel {
        return wrap0(channel)
    }

    @SuppressLint("NewApi")
    private class FileChannelCompat(
        private val delegate: FileChannel,
    ) : FileChannel(), SeekableByteChannel {
        override fun implCloseChannel() {
            delegate.close()
        }

        override fun read(dst: ByteBuffer?): Int {
            return delegate.read(dst)
        }

        override fun read(dsts: Array<out ByteBuffer>?, offset: Int, length: Int): Long {
            return delegate.read(dsts, offset, length)
        }

        override fun read(dst: ByteBuffer?, position: Long): Int {
            return delegate.read(dst, position)
        }

        override fun write(src: ByteBuffer?): Int {
            return delegate.write(src)
        }

        override fun write(srcs: Array<out ByteBuffer>?, offset: Int, length: Int): Long {
            return delegate.write(srcs, offset, length)
        }

        override fun write(src: ByteBuffer?, position: Long): Int {
            return delegate.write(src, position)
        }

        override fun position(): Long {
            return delegate.position()
        }

        override fun position(newPosition: Long): FileChannel {
            delegate.position(newPosition)
            return this
        }

        override fun size(): Long {
            return delegate.size()
        }

        override fun truncate(size: Long): FileChannel {
            delegate.truncate(size)
            return this
        }

        override fun force(metaData: Boolean) {
            delegate.force(metaData)
        }

        override fun transferTo(position: Long, count: Long, target: WritableByteChannel?): Long {
            return delegate.transferTo(position, count, target)
        }

        override fun transferFrom(src: ReadableByteChannel?, position: Long, count: Long): Long {
            return delegate.transferFrom(src, position, count)
        }

        override fun map(mode: MapMode?, position: Long, size: Long): MappedByteBuffer {
            return delegate.map(mode, position, size)
        }

        override fun lock(position: Long, size: Long, shared: Boolean): FileLock {
            return delegate.lock(position, size, shared)
        }

        override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock {
            return delegate.tryLock(position, size, shared)
        }
    }

}