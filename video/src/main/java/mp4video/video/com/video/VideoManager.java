package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;


public class VideoManager implements IPreviewFrame, AudioEncoder.IAACListener, VideoEncoder.IH264Listener {
    private volatile boolean isStartRecord;
    private volatile boolean isVideoEncoding;
    private int mEventVideoAfter = VideoConfig.eventVideoAfter;
    private HandlerThread eventThread;
    private EventHandler eventHandler;
    private AudioEncoder audioEncoder;
    private ContinueRecordThread mContinueRecordThread;
    private EventRecordThread eventRecordThread;
    private boolean isAudioEnable;

    public enum Mode {
        CONTINUE, EVENT
    }

    public enum VideoType{
        OPENDOOR,OTHER
    }
    private static VideoManager mInstance;
    private String TAG = VideoManager.class.getSimpleName();
    private int mVideoWidth;
    private int mVideoHeight;
    private VideoEncoder videoEncoder;
    private ByteBuffer mH264Buffer;
    private ByteBuffer mAACBuffer;
    private Mode recordMode;
    private LinkedBlockingQueue<H264Data> mH264Queue = new LinkedBlockingQueue<H264Data>(900);

    private VideoManager() {
    }

    /**
     * @param mode
     * @param width  视频宽
     * @param height 视频高
     * @param isAudioEnable 是否开启音频采集
     */
    public void setup(Mode mode, int width, int height, boolean isAudioEnable) {
        this.recordMode = mode;
        this.mVideoWidth = width;
        this.mVideoHeight = height;
        this.isAudioEnable = isAudioEnable;
        mH264Buffer = ByteBuffer.allocateDirect(1024 * 100);
        mAACBuffer = ByteBuffer.allocateDirect(1024);
        eventThread = new HandlerThread("event record");
        eventThread.start();
        eventHandler = new EventHandler(eventThread.getLooper());
    }

    public static VideoManager getInstance() {
        synchronized (VideoManager.class) {
            if (mInstance == null) {
                mInstance = new VideoManager();
            }
        }
        return mInstance;
    }

    /**
     * 开启视频编码
     */
    private void startVideoEncoder() {
        videoEncoder = new VideoEncoder(mVideoWidth, mVideoHeight);
        videoEncoder.start();
    }

    /**
     * 开启音频编码
     */
    private void startAudioEncoder() {
        audioEncoder = new AudioEncoder();
        audioEncoder.start();
    }

    /**
     * 停止音频编码
     */
    private void stopAudioEncoder() {
        audioEncoder.stop();
    }

    /**
     * 停止视频编码
     */
    private void stopVideoEncoder() {
        videoEncoder.stop();
    }

    @Override
    public void frame(byte[] NV21, int width, int height, int degree) {
        if (!isVideoEncoding) return;
        videoEncoder.encode(NV21, degree);
    }

    /**
     * 开启录像 仅 {@link Mode#CONTINUE} 支持
     */
    public void startContinueRecord(IRecordListener listener) {
        if (recordMode != Mode.CONTINUE) {
            Log.e(TAG, "仅持续录像模式支持");
            return;
        }

        if (isStartRecord){
            Log.w(TAG,"录像开启错误:持续录像已开启");
            return;
        }
        isStartRecord = true;
        isVideoEncoding = true;
        startVideoEncoder();
        //音频开启
        if (isAudioEnable){
            startAudioEncoder();
        }
        //开始 muxer
        mContinueRecordThread = new ContinueRecordThread(listener);
        mContinueRecordThread.start();
    }

    /**
     * 事件录像要提前开启
     * 音视频采集开始
     */
    public void startReadyRecord() {
        if (recordMode != Mode.EVENT) {
            Log.e(TAG,"录像开启错误:仅事件视频模式支持");
            return;
        }

        if (isVideoEncoding){
            Log.w(TAG,"录像开启错误:事件录像准备已开启");
        }
        isVideoEncoding = true;
        if (isAudioEnable){
            startAudioEncoder();
            audioEncoder.setEncoderListener(this);
        }
        startVideoEncoder();
        videoEncoder.setEncoderListener(this);
    }

    /**
     * 开启事件触发录像,仅 {@link Mode#EVENT} 支持
     * {@link VideoManager#startReadyRecord} 已调用
     * @param path
     * @param videoType
     * @param listener
     */
    public void eventRecord(String path, VideoType videoType,IRecordListener listener) {
        if (recordMode != Mode.EVENT) {
            Log.e(TAG, "仅事件录像支持");
            return;
        }
        if (!isVideoEncoding) {
            Log.e(TAG, "事件录像:当前录像已经停止");
            return;
        }
        //如果是开门事件 则继续
        if (videoType != VideoType.OPENDOOR && eventRecordThread != null && hasCallBacks(eventRecordThread)){
            Log.w(TAG,"存在其他事件视频任务,不继续");
            return;
        }

        eventRecordThread = new EventRecordThread(path, listener);
        eventHandler.postDelayed(eventRecordThread, mEventVideoAfter);
    }

    private boolean hasCallBacks(Runnable runnable){
        Class<? extends EventHandler> eventHandlerClass = eventHandler.getClass();
        try {
            Method hasCallbacks = eventHandlerClass.getMethod("hasCallbacks", Runnable.class);
            return (boolean) hasCallbacks.invoke(eventHandler, runnable);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 停止持续录像 停止音视频 采集
     */
    public void stopContinueRecord() {
        if (recordMode != Mode.CONTINUE){
            Log.e(TAG,"停止错误:非持续录像");
            return;
        }

        if (!isStartRecord) {
            Log.w(TAG,"停止错误:持续录像已停止");
            return;
        }

        isVideoEncoding = false;
        isStartRecord = false;
        mH264Queue.add(new H264Data(null, null));
        if (isAudioEnable){
            stopAudioEncoder();
        }
        stopVideoEncoder();
    }

    /**
     * 停止事件录像缓存 停止音视频 采集
     */
    public void stopReadyRecord() {
        if (recordMode != Mode.EVENT){
            Log.e(TAG,"停止错误:非事件录像");
            return;
        }
        if (!isVideoEncoding){
            Log.w(TAG,"停止错误:事件录像准备已停止");
            return;
        }
        isVideoEncoding = false;
        if (isAudioEnable){
            stopAudioEncoder();
            AACCacheManager.getInstance().clear();
        }
        stopVideoEncoder();
        H264CacheManager.getInstance().clear();
    }

    @Override
    public void onH264(byte[] bytes, int flags, long pts, int size, int offset) {
//         Log.d(TAG, "h264 ------------------------size:" + mH264Queue.size());
        if (recordMode == Mode.EVENT) {
            H264Data h264Data = new H264Data(bytes, flags, pts, size, offset);
            //缓存 todo
            H264CacheManager.getInstance().add(h264Data);
        }
    }

    @Override
    public void onAAC(byte[] bytes, int flag, long pts, int size, int offset) {
        if (recordMode == Mode.EVENT) {
            AACData aacData = new AACData(bytes, flag, pts, size, offset);
            AACCacheManager.getInstance().add(aacData);
        }
    }

    private class ContinueRecordThread extends Thread implements VideoEncoder.IH264Listener, AudioEncoder.IAACListener {

        private long startTime;
        private IRecordListener listener;
        private MediaMuxer muxer;
        private int videoTrack;
        private int audioTrack;
        private volatile boolean isstop;

        public ContinueRecordThread(IRecordListener listener) {
            this.listener = listener;
            if (isAudioEnable){
                audioEncoder.setEncoderListener(this);
            }
            videoEncoder.setEncoderListener(this);
            startMuxer();
            setName("ContinueRecordThread");
        }

        private void startMuxer() {

            String name = Environment.getExternalStorageDirectory().getAbsolutePath() + "/kang/" + System.currentTimeMillis() + ".mp4";
            try {
                muxer = new MediaMuxer(name, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VideoConfig.videoWidth, VideoConfig.videoHeight);
                byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 30, -38, 2, -128, -10, -128, 109, 10, 19, 80};
                byte[] header_pps = {0, 0, 0, 1, 104, -50, 6, -30};
                videoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                videoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

                videoTrack = muxer.addTrack(videoFormat);

                if (isAudioEnable){
                    MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1);
                    audioFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                    byte[] data = new byte[]{(byte) 0x14, (byte) 0x08};
                    audioFormat.setByteBuffer("csd-0", ByteBuffer.wrap(data));

                    audioTrack = muxer.addTrack(audioFormat);

                    if (videoTrack != -1 && audioTrack != -1) {
                        muxer.start();
                        startTime = System.nanoTime() / 1000;
                        isstop = false;
                        if (listener != null) {
                            listener.onRecordStart();
                        }
                    }
                }else {
                    if (videoTrack != -1) {
                        muxer.start();
                        startTime = System.nanoTime() / 1000;
                        isstop = false;
                        if (listener != null) {
                            listener.onRecordStart();
                        }
                    }
                }
                Log.d(TAG, "开启新一次录像");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    H264Data take = mH264Queue.take();
                    if (take.getData() == null)
                        break;
                    //Log.d(TAG, "h264 take :size:" + mH264Queue.size());
                    long frameUs = take.getPts();
                    byte[] data = take.getData();
//                    Log.e(TAG,"data length:"+data.length);
                    mH264Buffer.put(data);
                    mH264Buffer.flip();

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = take.getFlags();
                    bufferInfo.presentationTimeUs = take.getPts();
                    bufferInfo.size = take.getSize();
                    bufferInfo.offset = take.getOffset();

                    // Log.d(TAG, "buffer info :" + bufferInfo.presentationTimeUs);

                    synchronized (SyncFlag.syncObj) {
                        muxer.writeSampleData(videoTrack, mH264Buffer, bufferInfo);
                    }

                    mH264Buffer.clear();

                    if (frameUs - startTime > VideoConfig.continueVideoLength) {
                        isstop = true;
                        if (muxer != null) {
                            try {
                                muxer.stop();
                                muxer.release();
                            } catch (Exception e) {
                                if (listener != null) {
                                    listener.onError();
                                }
                                e.printStackTrace();
                            } finally {
                                muxer = null;
                                if (listener != null) {
                                    listener.onRecordEnd();
                                }
                                Log.d(TAG, "结束一次录像");
                            }
                        }
                        startMuxer();
                    }
                } catch (InterruptedException e) {
                    if (listener != null) {
                        listener.onError();
                    }
                    e.printStackTrace();
                }
            }

            Log.d(TAG, "尝试结束最后一次");
            //持续录像结束,保证最后的视频可播放
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError();
                    }
                    e.printStackTrace();
                } finally {
                    muxer = null;
                    if (listener != null) {
                        listener.onRecordEnd();
                    }
                    Log.d(TAG, "结束最后一次录像");
                }
            }

        }

        @Override
        public void interrupt() {

        }

        @Override
        public void onAAC(byte[] bytes, int flag, long pts, int size, int offset) {
            if (bytes.length < 50) {
                return;
            }
            if (isStartRecord && !isstop) {
                mAACBuffer.put(bytes);
                mAACBuffer.flip();
                synchronized (SyncFlag.syncObj) {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = flag;
                    bufferInfo.presentationTimeUs = pts;
                    bufferInfo.size = size;
                    bufferInfo.offset = offset;
                    muxer.writeSampleData(audioTrack, mAACBuffer, bufferInfo);
                }

                mAACBuffer.clear();
            }
        }

        @Override
        public void onH264(byte[] bytes, int flags, long pts, int size, int offset) {
            H264Data h264Data = new H264Data(bytes, flags, pts, size, offset);
            if (recordMode == Mode.CONTINUE && isStartRecord) {
                boolean offer = mH264Queue.offer(h264Data);
//            Log.d(TAG, "h264 offer:" + offer + ",size:" + mH264Queue.size());
            }
        }
    }

    private class EventHandler extends Handler {
        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

    private class EventRecordThread extends Thread {

        private final IRecordListener listener;
        private MediaMuxer muxer;
        private int videoTrack;
        private int audioTrack;
        private long mLastVideoTime;
        private long mLastAudioTime;

        public EventRecordThread(String path, IRecordListener listener) {
            setName("EventRecordThread");
            this.listener = listener;
            startMuxer(path);
        }

        private void startMuxer(String path) {

            try {
                muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480);
                byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 30, -38, 2, -128, -10, -128, 109, 10, 19, 80};
                byte[] header_pps = {0, 0, 0, 1, 104, -50, 6, -30};
                videoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                videoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

                videoTrack = muxer.addTrack(videoFormat);

                if (isAudioEnable){
                    MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1);
                    audioFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                    byte[] data = new byte[]{(byte) 0x14, (byte) 0x08};
                    audioFormat.setByteBuffer("csd-0", ByteBuffer.wrap(data));

                    audioTrack = muxer.addTrack(audioFormat);

                    if (videoTrack != -1 && audioTrack != -1) {
                        muxer.start();
                        if (listener != null) {
                            listener.onRecordStart();
                        }
                    } else {
                        throw new Exception("add track failed");
                    }
                }else {
                    if (videoTrack != -1) {
                        muxer.start();
                        if (listener != null) {
                            listener.onRecordStart();
                        }
                    } else {
                        throw new Exception("add track failed");
                    }
                }
                Log.d(TAG, "--------------------------------------");
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError();
                }
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                LinkedList<H264Data> h264Cache = H264CacheManager.getInstance().getCloneCache();
                LinkedList<AACData> aacCache = AACCacheManager.getInstance().getCloneCache();
                int size = h264Cache.size();
                Log.d(TAG, "h264Cache.size:" + size);
                if (size == 0){
                    Log.e(TAG,"视频帧缓存大小为0");
                    return;
                }
                if (listener != null) {
                    listener.onRecording();
                }
                for (int i = 0; i < size; i++) {
                    H264Data peek = h264Cache.poll();
                    byte[] data = peek.getData();
//                    Log.e(TAG,"data length:"+data.length);
                    mH264Buffer.put(data);
                    mH264Buffer.flip();
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = peek.getFlags();
                    bufferInfo.presentationTimeUs = peek.getPts();
                    bufferInfo.size = peek.getSize();
                    bufferInfo.offset = peek.getOffset();

                    if (mLastVideoTime == bufferInfo.presentationTimeUs) {
                        Log.e(TAG, "h264 buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i + ",h254Cache:" + data.hashCode());
                    }
                    Log.e(TAG, "h264- buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i + ",h254Cache:" + data.hashCode());
                    mLastVideoTime = bufferInfo.presentationTimeUs;
                    muxer.writeSampleData(videoTrack, mH264Buffer, bufferInfo);
                    mH264Buffer.clear();
                }

                if (isAudioEnable){
                    int aacSize = aacCache.size();
                    Log.e(TAG, "aacCache.size:" + aacSize);

                    for (int i = 0; i < aacSize; i++) {
                        AACData poll = aacCache.get(i);
                        byte[] data = poll.getData();
                        mAACBuffer.put(data);
                        mAACBuffer.flip();
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        bufferInfo.flags = poll.getFlag();
                        bufferInfo.presentationTimeUs = poll.getPts();
                        bufferInfo.size = poll.getSize();
                        bufferInfo.offset = poll.getOffset();

                        if (mLastAudioTime == bufferInfo.presentationTimeUs) {
                            Log.e(TAG, "aac buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i + ",size:" + data.length + ",aacCache:" + data.hashCode());
                        }
                        Log.e(TAG, "aac- buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i + ",size:" + data.length + ",aacCache:" + data.hashCode());
                        mLastAudioTime = bufferInfo.presentationTimeUs;
//                    Log.e(TAG, "aac buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i +",size:"+data.length+ ",aacCache:" + data.hashCode());
                        muxer.writeSampleData(audioTrack, mAACBuffer, bufferInfo);
                        mAACBuffer.clear();
                    }
                }

                if (muxer != null) {
                    try {
                        muxer.stop();
                        muxer.release();
                    } catch (Exception e) {
                        if (listener != null) {
                            listener.onError();
                        }
                        e.printStackTrace();
                    } finally {
                        if (listener != null) {
                            listener.onRecordEnd();
                        }
                    }
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError();
                }
                e.printStackTrace();
            }
        }
    }

    public interface IRecordListener {
        void onRecordStart();

        void onRecording();

        void onRecordEnd();

        void onError();
    }
}
