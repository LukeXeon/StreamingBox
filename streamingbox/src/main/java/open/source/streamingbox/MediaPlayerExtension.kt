package open.source.streamingbox

import android.media.MediaDataSource
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import open.source.streamingbox.media.MediaDataSourceService
import open.source.streamingbox.media.IMediaDataSource

private val nativeSetDataSource by lazy {
    MediaPlayer::class.java
        .getDeclaredMethod(
            "nativeSetDataSource",
            IBinder::class.java,
            String::class.java,
            Array<String>::class.java,
            Array<String>::class.java
        ).apply {
            isAccessible = true
        }
}

fun MediaPlayer.setDataSource(dataSource: IMediaDataSource) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        setDataSource(dataSource as MediaDataSource)
    } else {
        nativeSetDataSource(
            this,
            MediaDataSourceService(dataSource),
            MediaDataSourceService.PATH,
            null,
            null
        )
    }
}