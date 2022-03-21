package open.source.streamingbox

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Build
import java.io.File

fun MediaPlayer.setEncryptedDataSource(context: Context, file: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        setDataSource(StreamingBox.openMediaDataSource(context, file) as MediaDataSource)
    } else {
        setDataSource(context, StreamingBox.openLocalHttpUri(context, file))
    }
}