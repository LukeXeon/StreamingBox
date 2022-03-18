package open.source.videobox

import android.media.MediaPlayer
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import open.source.streamingbox.StreamingBox
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var en: Button
    private lateinit var de: Button
    private lateinit var v: SurfaceView
    private lateinit var seek: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        en = findViewById(R.id.en)
        de = findViewById(R.id.de)
        v = findViewById(R.id.v)
        seek = findViewById(R.id.seek)
        val file = File(filesDir, "灵梦.mp4")
        en.setOnClickListener {
            AsyncTask.THREAD_POOL_EXECUTOR.execute {
                assets.open("Reimu 灵梦无声_适用于Mac13'_1159_709.mp4")
                    .use { input ->
                        StreamingBox.openOutputStream(this, file)
                            .use { output ->
                                input.copyTo(output)
                            }
                    }
                runOnUiThread {
                    Toast.makeText(this, "加密完成", Toast.LENGTH_SHORT).show()
                }
            }
        }
        de.setOnClickListener {
            val m = IjkMediaPlayer()
            val uri = StreamingBox.openHTTPStream(this, file)

            m.isLooping = true
            m.setDataSource(this, uri, mapOf("Connection" to "keep-alive"))
            m.prepareAsync()
            m.setOnPreparedListener {
                it.start()
                m.setSurface(v.holder.surface)
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val duration = m.duration
                    val p = progress / 100f
                    val seekTo = (duration * p).toLong()
                    Log.d(TAG, "onProgressChanged: $seekTo $duration $p")
                    m.seekTo(seekTo)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {

                }

            })
        }


    }

    companion object {
        private const val TAG = "MainActivity"
    }


}