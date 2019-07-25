package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import static android.media.MediaCodec.CONFIGURE_FLAG_ENCODE;
import static android.media.MediaFormat.KEY_BIT_RATE;
import static android.media.MediaFormat.KEY_COLOR_FORMAT;
import static android.media.MediaFormat.KEY_FRAME_RATE;
import static android.media.MediaFormat.KEY_I_FRAME_INTERVAL;

/**
 * Y420SP (NV12  yyyy uv) 和 (NV21 yyyy vu)
 * Y420P (YU12 yyyy u v)(I420) 和 (YV12 yyyy v u)
 */
public class VideoEncoder {

    private static final String TAG = VideoEncoder.class.getSimpleName();
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private MediaCodec encoder = null;
    private int mediaWidth = 1280;
    private int mediaHeight = 720;

    private byte[] yuv420sp;    //存储原始采样数据 yuv420
    private byte[] yuv;    //存储原始采样数据 yuv420
    private byte[] yuvI420;    //存储原始采样数据 yuv420
    private IMuxerCallback muxer;
    private int mColorFormat;
    private ByteBuffer mBuffer;

    public VideoEncoder(int width, int height) {
        mediaWidth = width;
        mediaHeight = height;
        yuv420sp = new byte[mediaWidth * mediaHeight * 3 / 2];
        yuv = new byte[mediaWidth * mediaHeight * 3 / 2];
        yuvI420 = new byte[mediaWidth * mediaHeight * 3 / 2];
        initVideoEncoder();
    }
    /**
     * color formats that we can use in this class
     */
    protected static int[] recognizedFormats;

    static {
        recognizedFormats = new int[]{
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
//                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
        };
    }
    private void initVideoEncoder() {

        MediaCodecInfo mediaCodecInfo = selectCodec(MIME_TYPE);
        //colorFormat = getColorFormat(mediaCodecInfo);
        if (mediaCodecInfo == null) {
            Log.e(TAG, "vedio Unable to find an appropriate codec for " + MIME_TYPE);
            return;
        }
        MediaFormat mediaFormat = null;
        try {


            mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mediaWidth, mediaHeight);
            mediaFormat.setInteger(KEY_BIT_RATE, 1200000); //比特率
            mediaFormat.setInteger(KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar); //
            mediaFormat.setInteger(KEY_FRAME_RATE, 20); //帧率
            mediaFormat.setInteger(KEY_I_FRAME_INTERVAL, 1); //I 帧间隔
            encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            encoder.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
            encoder.start();
            mBuffer = ByteBuffer.allocateDirect(1024*100);

            Log.d(TAG, "视频编码器:" + mediaCodecInfo.getName() + "创建完成!");
        } catch (IOException e) {
            Log.e(TAG, "视频编码器 创建失败");
            e.printStackTrace();
        }
    }
    /**
     * select the first codec that match a specific MIME type
     *
     * @param mimeType
     * @return null if no codec matched
     */
    @SuppressWarnings("deprecation")
    protected final MediaCodecInfo selectVideoCodec(final String mimeType) {

        // get the list of available codecs
        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {    // skipp decoder
                continue;
            }
            // select first codec that match a specific MIME type and color format
            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    final int format = selectColorFormat(codecInfo, mimeType);
                    if (format > 0) {
                        mColorFormat = format;
                        return codecInfo;
                    }
                }
            }
        }
        return null;
    }
    /**
     * select color format available on specific codec and we can use.
     *
     * @return 0 if no colorFormat is matched
     */
    protected static final int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
        int result = 0;
        final MediaCodecInfo.CodecCapabilities caps;
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            caps = codecInfo.getCapabilitiesForType(mimeType);
        } finally {
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        }
        int colorFormat;
        for (int i = 0; i < caps.colorFormats.length; i++) {
            colorFormat = caps.colorFormats[i];
            if (isRecognizedViewoFormat(colorFormat)) {
                if (result == 0)
                    result = colorFormat;
                break;
            }
        }
        if (result == 0)
            Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return result;
    }
    private static final boolean isRecognizedViewoFormat(final int colorFormat) {
        final int n = recognizedFormats != null ? recognizedFormats.length : 0;
        for (int i = 0; i < n; i++) {
            if (recognizedFormats[i] == colorFormat) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对一帧数据编码
     *
     * @param NV21
     * @return
     */
    public boolean encode(byte[] NV21,int degree) {
        if (encoder == null) {
            return false;
        }

        NV21toI420SemiPlanar(NV21, yuv420sp, mediaWidth, mediaHeight);
       // Log.d(TAG,"格式转换："+(System.currentTimeMillis() - start));
        try {

            //一直等待到输入队列中有空闲位置
            int inputBufferIndex = encoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(yuv420sp);
                Log.e(TAG, "vedio inputBufferIndex: =--------------------------" + inputBufferIndex+","+inputBuffer.hashCode());
                encoder.queueInputBuffer(inputBufferIndex, 0, yuv420sp.length, System.currentTimeMillis(), 0);
            }


            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (true) {
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
                //Log.e(TAG, "vedio outputBufferIndex:" + outputBufferIndex);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxer != null) {
                     //   Log.e(TAG, "vedio 调用muxer 设置format");
                        muxer.onMediaFormat(encoder.getOutputFormat());
                    }
                    //Log.e(TAG, "vedio encoder output format changed: " + encoder.getOutputFormat());
                    break;
                }

                if (outputBufferIndex < 0) {

                  //  Log.e(TAG, "vedio 没有可用的outbuffer");
                    break;
                }

                if (outputBufferIndex >= 0) {
                    if (muxer != null) {
                        muxer.onMediaFormat(encoder.getOutputFormat());
                    }
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

//                    ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                    if (bufferInfo.size != 0) {
                        // adjust the ByteBuffer values to match BufferInfo (not
                        // needed?)
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                        mBuffer.put(outputBuffer);
                        mBuffer.flip();
                        //buffer.put(outputBuffer);

//                        Log.e(TAG, "vedio bufferInfo:" + bufferInfo.flags);
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0){
                            Log.e(TAG,"关键帧输出");
                        }
                        bufferInfo.presentationTimeUs = getPts();
                        if (muxer != null && bufferInfo.presentationTimeUs != 0l) {
                            Log.e(TAG, "vedio presentationTimeUs:" + bufferInfo.presentationTimeUs+",buffer.length:"+bufferInfo.size+",buffer:"+mBuffer.array().length);
                            muxer.mux(mBuffer, bufferInfo);
                        }
                        mBuffer.clear();
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
//            Log.d(TAG,"处理一帧："+(System.currentTimeMillis() - start));
        } catch (Exception e) {
           // Log.e("video error", "" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    //顺时针旋转90
    private void YUV420spRotate90(byte[] src, byte[] dst, int srcWidth, int srcHeight) {
        int wh = srcWidth * srcHeight;
        int uvHeight = srcHeight >> 1;

        //旋转Y
        int k = 0;
        for (int i = 0; i < srcWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < srcHeight; j++) {
                dst[k] = src[nPos + i];
                k++;
                nPos += srcWidth;
            }
        }

        for (int i = 0; i < srcWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                dst[k] = src[nPos + i];
                dst[k + 1] = src[nPos + i + 1];
                k += 2;
                nPos += srcWidth;
            }
        }

    }


    // YYYYYYYY UVUV(nv12)--> YYYYYYYY VUVU(nv21)
    private byte[] nv12ToNV21(byte[] nv12, int width, int height) {
        byte[] ret = new byte[width * height * 3 /2];
        int framesize = width * height;
        int i = 0, j = 0;
        // 拷贝Y分量
        System.arraycopy(nv12, 0,ret , 0, framesize);
        // 拷贝UV分量
        for (j = framesize; j < nv12.length; j += 2) {
            ret[j] = nv12[j+1];
            ret[j+1] = nv12[j];
        }
        return ret;
    }
    public void start() {
//        if (encoder != null) {
//            encoder.start();
//        }
    }

    public void stop() {
//        if (encoder != null) {
//            encoder.stop();
//        }
    }

    public void releaseCodec() {
        if (encoder != null) {
            stop();
            encoder.release();
            encoder = null;
        }
    }
    /**
     * Generates the presentation time for frame N, in nanoseconds.
     */
    private static long computePresentationTimeNsec(int frameIndex) {
        final long ONE_BILLION = 1000000;
        return frameIndex * ONE_BILLION / 25;
    }
    private long getPts() {
        return (System.nanoTime()) / 1000L;
    }

    public void setMuxer(IMuxerCallback muxer) {
        this.muxer = muxer;
    }

    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        System.arraycopy(nv21bytes, 0, i420bytes, 0, width * height);
        for (int i = width * height; i < nv21bytes.length; i += 2) {
            i420bytes[i] = nv21bytes[i + 1];
            i420bytes[i + 1] = nv21bytes[i];
        }
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}
