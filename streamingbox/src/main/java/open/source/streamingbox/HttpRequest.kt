package open.source.streamingbox

import android.net.Uri
import android.util.ArrayMap
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

internal data class HttpRequest(
    val uri: Uri,
    val method: String,
    val headers: Map<String, String>
) {
    override fun toString(): String {
        return "uri=$uri\nmethod=$method\n${headers.entries.joinToString("\n")}"
    }

    companion object {

        private const val TAG = "HttpRequest"

        /**
         * Find byte index separating header from body. It must be the last byte of
         * the first two sequential new lines.
         */
        private fun findHeadersEnd(buf: ByteArray, len: Int): Int {
            var splitByte = 0
            while (splitByte + 3 < len) {
                if (buf[splitByte].toInt().toChar() == '\r'
                    && buf[splitByte + 1].toInt().toChar() == '\n'
                    && buf[splitByte + 2].toInt().toChar() == '\r'
                    && buf[splitByte + 3].toInt().toChar() == '\n'
                ) {
                    return splitByte + 4
                }
                splitByte++
            }
            return 0
        }

        fun parse(input: InputStream): HttpRequest {
            var buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var splitByte: Int
            var len = 0
            var read = input.read(buffer, 0, buffer.size)
            while (read > 0) {
                len += read
                splitByte = findHeadersEnd(buffer, len)
                if (splitByte > 0) {
                    break
                }
                if (buffer.size - len <= 0) {
                    buffer = buffer.copyOf(buffer.size * 2)
                }
                read = input.read(buffer, len, buffer.size - len)
            }
            val reader = ByteArrayInputStream(buffer, 0, len).bufferedReader()
            // Read the request line
            val inLine = reader.readLine()
            val st = StringTokenizer(inLine)
            if (!st.hasMoreTokens()) {
                Log.e(
                    TAG,
                    "BAD REQUEST: Syntax error. Usage: GET /example/file.html"
                )
            }
            val method = st.nextToken()
            if (!st.hasMoreTokens()) {
                Log.e(
                    TAG,
                    "BAD REQUEST: Missing URI. Usage: GET /example/file.html"
                )
            }
            val uri = Uri.parse(st.nextToken())
            // If there's another token, it's protocol version,
            // followed by HTTP headers. Ignore version but parse headers.
            // NOTE: this now forces header names lowercase since they are
            // case insensitive and vary by client.
            val headers = ArrayMap<String, String>()
            if (st.hasMoreTokens()) {
                var line = reader.readLine()
                while (line != null && line.trim { it <= ' ' }.isNotEmpty()) {
                    val p = line.indexOf(':')
                    if (p >= 0) {
                        val key = line.substring(0, p).trim { it <= ' ' }.lowercase()
                        val value = line.substring(p + 1).trim { it <= ' ' }
                        headers[key] = value
                    }
                    line = reader.readLine()
                }
            }
            return HttpRequest(uri, method, headers)
        }
    }
}