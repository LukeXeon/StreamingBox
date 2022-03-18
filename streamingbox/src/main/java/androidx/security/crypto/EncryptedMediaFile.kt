/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.security.crypto

import android.annotation.SuppressLint
import android.content.Context
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.AesGcmHkdfStreamingKeyManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import open.source.streamingbox.NioCompat
import java.io.*
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException

/**
 * Class used to create and read encrypted files.
 *
 * <pre>
 * String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
 *
 * File file = new File(context.getFilesDir(), "secret_data");
 * EncryptedFile encryptedFile = EncryptedFile.Builder(
 * file,
 * context,
 * masterKeyAlias,
 * EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
 * ).build();
 *
 * // write to the encrypted file
 * FileOutputStream encryptedOutputStream = encryptedFile.openFileOutput();
 *
 * // read the encrypted file
 * FileInputStream encryptedInputStream = encryptedFile.openFileInput();
</pre> *
 */
internal class EncryptedMediaFile internal constructor(
    private val file: File,
    private val streamingAead: StreamingAead
) {
    /**
     * The encryption scheme to encrypt files.
     */
    enum class FileEncryptionScheme(val keyTemplate: KeyTemplate) {
        /**
         * The file content is encrypted using
         * [StreamingAead](https://google.github.io/tink/javadoc/tink/1.4.0/com/google/crypto/tink/streamingaead/StreamingAead.html) with AES-GCM, with the
         * file name as associated data.
         *
         *
         * For more information please see the Tink documentation:
         *
         * [AesGcmHkdfStreamingKeyManager](https://google.github.io/tink/javadoc/tink/1.4.0/com/google/crypto/tink/streamingaead/AesGcmHkdfStreamingKeyManager.html).aes256GcmHkdf4KBTemplate()
         */
        AES256_GCM_HKDF_4KB(AesGcmHkdfStreamingKeyManager.aes256GcmHkdf4KBTemplate());

    }

    /**
     * Builder class to configure EncryptedFile
     */
    class Builder
    /**
     * Builder for an EncryptedFile.
     */
    // [StreamFiles]: Because the contents of EncryptedFile are encrypted the use of
    // a FileDescriptor or Streams are intentionally not supported for the following reasons:
    // - The encrypted content is tightly coupled to the current installation of the app. If
    // the app is uninstalled, even if the data remained (such as being stored in a public
    // directory or another DocumentProvider) it would be (intentionally) unrecoverable.
    // - If the API did accept either an already opened FileDescriptor or a stream, then it
    // would be possible for the developer to inadvertently commingle encrypted and plain
    // text data, which, due to the way the API is structured, could render both encrypted
    // and unencrypted data irrecoverable.
    constructor(
        context: Context,
        // Required parameters
        private var file: File,
        masterKey: MasterKey,
        private val fileEncryptionScheme: FileEncryptionScheme
    ) {

        private val context: Context = context.applicationContext
        private val masterKeyAlias: String = masterKey.keyAlias

        // Optional parameters

        /**
         *  [keysetPrefName] The SharedPreferences file to store the keyset.
         */
        var keysetPrefName = KEYSET_PREF_NAME

        /**
         *  [keysetAlias] The alias in the SharedPreferences file to store the keyset.
         */
        var keysetAlias = KEYSET_ALIAS

        /**
         * @return An EncryptedFile with the specified parameters.
         */
        @Throws(GeneralSecurityException::class, IOException::class)
        fun build(): EncryptedMediaFile {
            StreamingAeadConfig.register()
            val streadmingAeadKeysetHandle = AndroidKeysetManager.Builder()
                .withKeyTemplate(fileEncryptionScheme.keyTemplate)
                .withSharedPref(context, keysetAlias, keysetPrefName)
                .withMasterKeyUri(MasterKey.KEYSTORE_PATH_URI + masterKeyAlias)
                .build().keysetHandle
            val streamingAead = streadmingAeadKeysetHandle.getPrimitive(
                StreamingAead::class.java
            )
            return EncryptedMediaFile(file, streamingAead)
        }

    }

    @SuppressLint("NewApi")
    @Throws(GeneralSecurityException::class, IOException::class)
    fun openSeekableByteChannel(): SeekableByteChannel {
        if (!file.exists()) {
            throw IOException("file doesn't exist: " + file.name)
        }
        return streamingAead.newSeekableDecryptingChannel(
            NioCompat.wrap(FileInputStream(file).channel),
            file.name.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Opens a FileInputStream that reads encrypted files based on the previous settings.
     *
     * Please ensure that the same master key and keyset are  used to decrypt or it
     * will cause failures.
     *
     * @return The input stream to read previously encrypted data.
     * @throws GeneralSecurityException when a bad master key or keyset has been used
     * @throws IOException              when the file was not found
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun openInputStream(): InputStream {
        if (!file.exists()) {
            throw IOException("file doesn't exist: " + file.name)
        }
        val fileInputStream = FileInputStream(file)
        return streamingAead.newDecryptingStream(
            fileInputStream,
            file.name.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Opens a FileOutputStream for writing that automatically encrypts the data based on the
     * provided settings.
     *
     *
     * Please ensure that the same master key and keyset are  used to decrypt or it
     * will cause failures.
     *
     * @return The FileOutputStream that encrypts all data.
     * @throws GeneralSecurityException when a bad master key or keyset has been used
     * @throws IOException              when the file already exists or is not available for writing
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun openOutputStream(append: Boolean): OutputStream {
        if (file.exists() && !append) {
            throw IOException(
                "output file already exists, please use a new file: "
                        + file.name
            )
        }
        val fileOutputStream = FileOutputStream(file, append)
        return streamingAead.newEncryptingStream(
            fileOutputStream,
            file.name.toByteArray(StandardCharsets.UTF_8)
        )
    }

    companion object {
        private const val KEYSET_PREF_NAME = "__androidx_security_crypto_encrypted_file_pref__"
        private const val KEYSET_ALIAS = "__androidx_security_crypto_encrypted_file_keyset__"
    }
}