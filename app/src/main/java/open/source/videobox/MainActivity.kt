package open.source.videobox

import android.app.ProgressDialog
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.liulishuo.filedownloader.BaseDownloadTask
import com.liulishuo.filedownloader.FileDownloadListener
import com.liulishuo.filedownloader.FileDownloader
import open.source.streamingbox.StreamingBox
import open.source.streamingbox.setEncryptedDataSource
import tv.danmaku.ijk.media.player.AndroidMediaPlayer
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.IMediaDataSource
import java.io.File
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private lateinit var en: Button
    private lateinit var de: Button
    private lateinit var de_ijk: Button
    private lateinit var v: SurfaceView
    private lateinit var seek: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileDownloader.setup(this)
        setContentView(R.layout.activity_main)
        en = findViewById(R.id.en)
        de = findViewById(R.id.de)
        v = findViewById(R.id.v)
        de_ijk = findViewById(R.id.de_ijk)
        seek = findViewById(R.id.seek)
        val file = File(cacheDir, "encode.mp4")
        en.setOnClickListener {
            val dialog = ProgressDialog(this)
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            dialog.setTitle("下载加密中")
            dialog.setMessage("下载中")
            dialog.show()
            file.delete()
            val raw = File(cacheDir, "raw.mp4")
            FileDownloader.getImpl()
                .create("https://android-test-resources.oss-cn-beijing.aliyuncs.com/oceans.mp4")
                .setPath(raw.canonicalPath)
                .setListener(object : FileDownloadListener() {
                    override fun pending(
                        task: BaseDownloadTask,
                        soFarBytes: Int,
                        totalBytes: Int,
                    ) {
                    }

                    override fun connected(
                        task: BaseDownloadTask,
                        etag: String,
                        isContinue: Boolean,
                        soFarBytes: Int,
                        totalBytes: Int,
                    ) {
                    }

                    override fun progress(
                        task: BaseDownloadTask,
                        soFarBytes: Int,
                        totalBytes: Int,
                    ) {
                        Log.d(TAG, "onCreate: download($soFarBytes/${totalBytes})")
                        dialog.progress = ((soFarBytes.toFloat() / totalBytes) * 100).toInt()
                    }

                    override fun blockComplete(task: BaseDownloadTask) {}
                    override fun retry(
                        task: BaseDownloadTask,
                        ex: Throwable,
                        retryingTimes: Int,
                        soFarBytes: Int,
                    ) {
                    }

                    override fun completed(task: BaseDownloadTask) {
                        dialog.setMessage("加密中")
                        dialog.progress = 0
                        thread {
                            val input = raw.inputStream()
                            val buffer = ByteArray((raw.length() / 10).toInt())
                            var bytesCopied: Long = 0
                            val output = StreamingBox.openOutputStream(this@MainActivity, file)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                val progress =
                                    ((bytesCopied / raw.length().toFloat()) * 100).toInt()
                                runOnUiThread {
                                    Log.d(TAG, "onCreate: encode($bytesCopied/${raw.length()})")
                                    dialog.progress = progress
                                }
                                bytes = input.read(buffer)
                            }
                            input.close()
                            output.close()
                            runOnUiThread {
                                dialog.dismiss()
                                Log.d(TAG, "onCreate: 下载加密完成")
                                Toast.makeText(this@MainActivity, "下载加密完成", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }

                    override fun paused(task: BaseDownloadTask, soFarBytes: Int, totalBytes: Int) {}
                    override fun error(task: BaseDownloadTask, e: Throwable) {}
                    override fun warn(task: BaseDownloadTask) {}
                }).start()
        }
        MediaPlayer::class.java.runCatching {
            getDeclaredField("DEBUG").apply {
                isAccessible = true
            }.setBoolean(null, true)
        }
        var m = AndroidMediaPlayer()
        val ijk = IjkMediaPlayer()
        de.setOnClickListener {
            m.release()
            m = AndroidMediaPlayer()
            m.isLooping = true
            m.internalMediaPlayer.setEncryptedDataSource(this, file)
            m.prepareAsync()
            m.setOnPreparedListener {
                it.start()
                m.setSurface(v.holder.surface)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val duration = m.duration
                    val p = progress / 100f
                    val seekTo = (duration * p)
                    Log.d(TAG, "onProgressChanged: $seekTo $duration $p")
                    m.seekTo(seekTo.toLong())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {

                }

            })
        }
        de_ijk.setOnClickListener {
            val ds = StreamingBox.openMediaDataSource(this, file)
            ijk.isLooping = true
            ijk.setDataSource(object : IMediaDataSource {
                override fun readAt(p0: Long, p1: ByteArray, p2: Int, p3: Int): Int {
                    return ds.readAt(p0, p1, p2, p3)
                }

                override fun getSize(): Long {
                    return ds.getSize()
                }

                override fun close() {
                    ds.close()
                }
            })
            ijk.prepareAsync()
            ijk.setOnPreparedListener {
                it.start()
                ijk.setSurface(v.holder.surface)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    val duration = ijk.duration
                    val p = progress / 100f
                    val seekTo = (duration * p)
                    Log.d(TAG, "onProgressChanged: $seekTo $duration $p")
                    ijk.seekTo(seekTo.toLong())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {

                }

            })
        }
    }

    private fun start(m: IMediaPlayer, file: File) {

    }

    companion object {
        private const val TAG = "MainActivity"
    }


}