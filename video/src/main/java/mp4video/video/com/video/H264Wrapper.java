package mp4video.video.com.video;

import android.media.MediaCodec;

public class H264Wrapper {

    private byte[] data;
    private MediaCodec.BufferInfo bufferInfo;
    private long frameMs;

    public H264Wrapper(byte[] data, MediaCodec.BufferInfo bufferInfo, long frameMs) {
        this.data = data;
        this.bufferInfo = bufferInfo;
        this.frameMs = frameMs;
    }

    public long getFrameMs() {
        return frameMs;
    }

    public void setFrameMs(long frameMs) {
        this.frameMs = frameMs;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return bufferInfo;
    }

    public void setBufferInfo(MediaCodec.BufferInfo bufferInfo) {
        this.bufferInfo = bufferInfo;
    }
}
