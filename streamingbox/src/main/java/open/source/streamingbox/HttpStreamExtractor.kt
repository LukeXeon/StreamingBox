package open.source.streamingbox

import android.content.Context
import android.os.Process
import android.system.OsConstants
import android.util.Log
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.*
import kotlin.math.min

internal class HttpStreamExtractor(
    private val context: Context,
    private val provider: IMediaDataSource.Provider
) : Runnable, ThreadFactory, RejectedExecutionHandler {
    private val executor = ThreadPoolExecutor(
        0, min(4, Runtime.getRuntime().availableProcessors()),
        60L, TimeUnit.SECONDS,
        SynchronousQueue(), this, this
    )
    private val server = ServerSocket(0)
    private val isRunning: Boolean
        get() {
            val t = thread
            return t != null && !t.isInterrupted
        }
    val listenPort: Int
        get() = server.localPort

    @Volatile
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (isRunning) {
            return
        }
        thread = Thread(this).apply {
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning) {
            return
        }
        thread?.interrupt()
        thread = null
    }

    override fun newThread(r: Runnable): Thread {
        return Thread(r)
    }

    override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
        if (r is Closeable) {
            r.close()
        }
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        while (isRunning) {
            try {
                val client = server.accept() ?: continue
                Log.i(TAG, "client connected at $listenPort")
                executor.execute(Task(client))
            } catch (e: InterruptedException) {
                // Do nothing
            } catch (e: SocketTimeoutException) {
                // Do nothing
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to client", e)
                // break;
            }
        }
    }

    private inner class Task(private val client: Socket) : Runnable, Closeable {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                Log.i(TAG, "Processing request: " + client.inetAddress.hostName)
                if (!NetworkCompat.isLocalHost(context, client.inetAddress.hostName)) {
                    Log.i(TAG, "Reject, ${client.inetAddress}")
                    return
                }
                val uid = NetworkCompat.getConnectionOwnerUid(
                    context,
                    OsConstants.IPPROTO_TCP,
                    client.remoteSocketAddress as InetSocketAddress,
                    client.localSocketAddress as InetSocketAddress
                )
                if (uid != 0 && uid != Process.myUid()) {
                    Log.i(TAG, "Reject, uid1: ${Process.myUid()}, uid2: $uid")
                    return
                }
                val clientInput = client.getInputStream()
                val request = HttpRequest.parse(clientInput)
                var range = request.headers["range"]
                var skip = 0L
                if (!range.isNullOrEmpty()) {
                    Log.i(TAG, "range is: $range")
                    range = range.substring(6)
                    val index = range.indexOf('-')
                    if (index > 0) {
                        range = range.substring(0, index)
                    }
                    skip = range.toLong()
                    Log.i(TAG, "range found!! $skip")
                }
                val filePath = request.uri.getQueryParameter("file")
                if (filePath.isNullOrEmpty()) {
                    return
                }
                provider.open(filePath).use { dataSource ->
                    val size = dataSource.getSize()
                    val responseHeaders = StringBuilder(128)
                    if (skip > 0) {
                        // It is a seek or skip request if there's a Range
                        // header
                        responseHeaders.append("HTTP/1.1 206 Partial Content\r\n")
                        responseHeaders.append("Content-Length: ${size - 1 - skip}\r\n")
                        responseHeaders.append("Content-Range: bytes $skip-${(size - 1)}/${size}\r\n")
                    } else {
                        responseHeaders.append("HTTP/1.1 200 OK\r\n")
                        responseHeaders.append("Content-Length: ${size}\r\n")
                    }
                    val format = SimpleDateFormat(
                        "EEE, dd MMM yyyy HH:mm:ss zzz",
                        Locale.getDefault()
                    )
                    responseHeaders.append("Server: streaming-box/1.0.0\r\n")
                    responseHeaders.append("Connection: keep-alive\r\n")
                    responseHeaders.append("Content-Type: video/mp4\r\n")
                    responseHeaders.append("Accept-Ranges: bytes\r\n")
                    responseHeaders.append("Last-Modified: ${format.format(Date(File(filePath).lastModified()))}\r\n")
                    responseHeaders.append("\r\n")
                    val responseHeadersString = responseHeaders.toString()
                    Log.i(
                        TAG,
                        "Http Status: **********\nRequest:\n${request}\n\nResponse:\n$responseHeadersString"
                    )
                    val output = client.getOutputStream()
                    output.write(responseHeadersString.toByteArray())
                    val buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)
                    while (isRunning) {
                        buffer.clear()
                        val readCount = dataSource.readAt(skip, buffer)
                        if (readCount <= 0) {
                            Log.w(TAG, "EOF: **********")
                            break
                        }
                        output.write(buffer.array(), 0, readCount)
                        output.flush()
                        skip += readCount
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Exception: ", e)
            } finally {
                client.runCatching { close() }
            }
        }

        override fun close() {
            client.runCatching { close() }
        }
    }

    companion object {
        private const val TAG = "MediaStreamExtractor"
    }

}