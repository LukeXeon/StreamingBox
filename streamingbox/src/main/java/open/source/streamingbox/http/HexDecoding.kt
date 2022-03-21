/*
 * Copyright 2012 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package open.source.streamingbox.http

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hexadecimal encoding where each byte is represented by two hexadecimal digits.
 */
internal object HexDecoding {

    /**
     * Decodes the provided hexadecimal string into an array of bytes.
     */
    fun decode(encoded: String): ByteArray {
        // IMPLEMENTATION NOTE: Special care is taken to permit odd number of hexadecimal digits.
        val resultLengthBytes = (encoded.length + 1) / 2
        val result = ByteArray(resultLengthBytes)
        var resultOffset = 0
        var encodedCharOffset = 0
        if (encoded.length % 2 != 0) {
            // Odd number of digits -- the first digit is the lower 4 bits of the first result byte.
            result[resultOffset++] = getHexadecimalDigitValue(encoded[encodedCharOffset]).toByte()
            encodedCharOffset++
        }
        val len = encoded.length
        while (encodedCharOffset < len) {
            result[resultOffset++] =
                (getHexadecimalDigitValue(encoded[encodedCharOffset]) shl 4 or getHexadecimalDigitValue(
                    encoded[encodedCharOffset + 1])).toByte()
            encodedCharOffset += 2
        }
        return result
    }

    private fun getHexadecimalDigitValue(c: Char): Int {
        return when (c) {
            in 'a'..'f' -> {
                c - 'a' + 0x0a
            }
            in 'A'..'F' -> {
                c - 'A' + 0x0a
            }
            in '0'..'9' -> {
                c - '0'
            }
            else -> {
                throw IllegalArgumentException(
                    "Invalid hexadecimal digit at position : '$c' (0x" + Integer.toHexString(
                        c.toInt()
                    ) + ")"
                )
            }
        }
    }

    fun toNetworkOrder(input: ByteArray): ByteArray {
        val inBuffer = ByteBuffer.wrap(input)
        inBuffer.order(ByteOrder.nativeOrder())
        val result = ByteArray(input.size)
        val outBuffer = ByteBuffer.wrap(result)
        outBuffer.order(ByteOrder.BIG_ENDIAN)
        for (word in 0 until input.size / 4) {
            outBuffer.putInt(inBuffer.int)
        }
        return result
    }
}