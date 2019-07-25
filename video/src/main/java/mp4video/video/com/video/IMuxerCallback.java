package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

public interface IMuxerCallback {
    void mux(ByteBuffer buffer, MediaCodec.BufferInfo info);
    void onMediaFormat(MediaFormat format);
    void onKeyFrame();
}
