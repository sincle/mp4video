package mp4video.video.com.video;

import android.media.MediaCodec;

public class AACData {

    private int size;
    private int offset;
    private long pts;
    private int flag;
    private byte[] data;
    private MediaCodec.BufferInfo bufferInfo;

    public AACData(byte[] data, MediaCodec.BufferInfo bufferInfo) {
        this.data = data;
        this.bufferInfo = bufferInfo;
    }

    public AACData(byte[] bytes, int flag, long pts, int size, int offset) {
        this.data = bytes;
        this.flag = flag;
        this.size = size;
        this.pts = pts;
        this.offset = offset;
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

    public int getOffset() {
        return offset;
    }

    public long getPts() {
        return pts;
    }

    public int getSize() {
        return size;
    }

    public int getFlag() {
        return flag;
    }

    public void setBufferInfo(MediaCodec.BufferInfo bufferInfo) {
        this.bufferInfo = bufferInfo;
    }
}
