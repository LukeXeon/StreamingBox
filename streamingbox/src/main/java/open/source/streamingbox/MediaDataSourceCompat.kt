package open.source.streamingbox

import android.media.MediaDataSource
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
internal class MediaDataSourceCompat(
    private val impl: IMediaDataSource,
) : MediaDataSource(), IMediaDataSource by impl