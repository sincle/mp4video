package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

import com.rejia.libyuv.YuvUtils;

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
    private final YuvUtils yuvUtils;
    private MediaCodec encoder = null;
    private int mediaWidth = 1280;
    private int mediaHeight = 720;

    private byte[] yuv420sp;    //存储原始采样数据 yuv420
    private byte[] yuv;    //存储原始采样数据 yuv420
    private byte[] yuvI420;    //存储原始采样数据 yuv420
    private int mColorFormat;
    private int mRotation;
    private IH264Listener encoderListener;
    private volatile boolean isStarting;

    public void setEncoderListener(IH264Listener listener){
        this.encoderListener = listener;
    }
    public VideoEncoder(int width, int height,int rotation) {
        yuvUtils = new YuvUtils();
        mediaWidth = width;
        mediaHeight = height;
        mRotation = rotation;
        yuv420sp = new byte[mediaWidth * mediaHeight * 3 / 2];
        yuv = new byte[mediaWidth * mediaHeight * 3 / 2];
        yuvI420 = new byte[mediaWidth * mediaHeight * 3 / 2];
    }

    public void start(){
        initVideoEncoder();
    }
    public MediaCodec getEncoder() {
        return encoder;
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
            if (mRotation % 180 != 0){
                //旋转90 或者 270 需要宽高互换
                mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,mediaHeight, mediaWidth);
            }else {
                mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, mediaWidth, mediaHeight);
            }
            mediaFormat.setInteger(KEY_BIT_RATE, 1200000); //比特率
            mediaFormat.setInteger(KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar); //
            mediaFormat.setInteger(KEY_FRAME_RATE, 20); //帧率
            mediaFormat.setInteger(KEY_I_FRAME_INTERVAL, 1); //I 帧间隔
            encoder = MediaCodec.createByCodecName(mediaCodecInfo.getName());
            encoder.configure(mediaFormat, null, null, CONFIGURE_FLAG_ENCODE);
            encoder.start();
            isStarting = true;
            Log.d(TAG,"video media codec ready");
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
        if (!isStarting){
            Log.d(TAG,"video encoder not ready");
            return false;
        }
        if (mRotation == 0){
            NV21toI420SemiPlanar(NV21, yuv420sp, mediaWidth, mediaHeight);
        }else {
            yuvUtils.nv21ToI420(NV21, yuvI420, mediaWidth, mediaHeight);
            yuvUtils.rotateI420(yuvI420, yuv, mediaWidth, mediaHeight, mRotation);
            yuvUtils.I420ToY420SP(yuv,yuv420sp,mediaWidth,mediaHeight);
        }
        //NV21toI420SemiPlanar(NV21, yuv420sp, mediaWidth, mediaHeight);
       // Log.d(TAG,"格式转换："+(System.currentTimeMillis() - start));
        try {

            //一直等待到输入队列中有空闲位置
            int inputBufferIndex = encoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(yuv420sp, 0, yuv420sp.length);
//                Log.e(TAG, "vedio inputBufferIndex: =--------------------------" + inputBufferIndex+","+inputBuffer.hashCode());
                encoder.queueInputBuffer(inputBufferIndex, 0, yuv420sp.length, getPts(), 0);
            }


            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            //等待直到从输出队列中取出编码的一帧
            while (true) {
                int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
                //Log.e(TAG, "vedio outputBufferIndex:" + outputBufferIndex);
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//                    Log.e(TAG, "vedio encoder output format changed: " + encoder.getOutputFormat());
                    break;
                }

                if (outputBufferIndex < 0) {

//                    Log.e(TAG, "vedio 没有可用的outbuffer"+outputBufferIndex);
                    break;
                }

                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                    byte[] outData = new byte[outputBuffer.remaining()];
                    outputBuffer.get(outData, 0, outData.length);
//                    Log.e(TAG, "vedio presentationTimeUs:" + bufferInfo.presentationTimeUs+",buffer.length:"+bufferInfo.size+",buffer:"+outData.hashCode());
                    if (encoderListener != null && bufferInfo.presentationTimeUs != 0) {
                        encoderListener.onH264(outData,bufferInfo.flags,bufferInfo.presentationTimeUs,bufferInfo.size,bufferInfo.offset);
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


    public void stop(){
        isStarting = false;
        releaseCodec();
    }
    private void releaseCodec() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            Log.d(TAG,"video codec stop and release");
            encoder = null;
        }
    }

    private long getPts() {
        return (System.nanoTime()) / 1000L;
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

    public interface IH264Listener{
        void onH264(byte[] outData, int flags, long pts, int size, int offset);
    }
}
