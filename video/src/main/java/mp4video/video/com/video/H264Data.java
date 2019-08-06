package mp4video.video.com.video;

import android.media.MediaCodec;

public class H264Data {

    private byte[] data;
    private int flags;
    private long pts;
    private int size;
    private int offset;

    private MediaCodec.BufferInfo bufferInfo;

    public H264Data(byte[] data, MediaCodec.BufferInfo bufferInfo) {
        this.data = data;
        this.bufferInfo = bufferInfo;
    }

    public H264Data(byte[] bytes, int flags, long pts, int size, int offset) {
        this.data = bytes;
        this.flags = flags;
        this.pts = pts;
        this.size = size;
        this.offset = offset;
    }

    public byte[] getData() {
        return data;
    }

    public int getFlags() {
        return flags;
    }

    public long getPts() {
        return pts;
    }

    public int getSize() {
        return size;
    }

    public int getOffset() {
        return offset;
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
