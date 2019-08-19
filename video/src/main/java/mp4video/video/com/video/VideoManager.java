package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;

import com.rejia.utils.Logger;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;


public class VideoManager implements IPreviewFrame, AudioEncoder.IAACListener, VideoEncoder.IH264Listener {
    private volatile boolean isStartRecord;
    private volatile boolean isVideoEncoding;
    private int mEventVideoAfter = VideoConfig.eventVideoAfter;
    private int mEventVideoBefore = 5000;
    private HandlerThread eventThread;
    private EventHandler eventHandler;
    private AudioEncoder audioEncoder;
    private ContinueRecordThread mContinueRecordThread;
    private EventRecordThread eventRecordThread;
    private boolean isAudioEnable;
    private IRecordListener1 statusListener;
    private int mRotation;

    public enum VideoRotation{
        DEGREE_0,DEGREE_90,DEGREE_180,DEGREE_270
    }
    public enum Mode {
        CONTINUE, EVENT
    }

    public enum VideoType{
        TYPE_DEFAUT,TYPE_OPENDOOR,TYPE_PERSON_NEARBY,TYPE_KEY_PRESS,TYPE_OTHER
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
     * @param openVideoProfile
     * @param width  视频宽
     * @param height 视频高
     * @param isAudioEnable 是否开启音频采集
     */
    public void setup(Mode mode,int rotation,String openVideoProfile, int width, int height, boolean isAudioEnable,IRecordListener1 listener1) {
        this.recordMode = mode;
        this.mRotation = rotation;
        this.mVideoWidth = width;
        this.mVideoHeight = height;
        this.isAudioEnable = isAudioEnable;
        this.statusListener = listener1;
        String[] split = openVideoProfile.split("\\|");
        mEventVideoBefore = Integer.parseInt(split[0])*1000;
        mEventVideoAfter = Integer.parseInt(split[1]) * 1000;
        H264CacheManager.getInstance().setAvailableTime(mEventVideoBefore+mEventVideoAfter+1000);
        AACCacheManager.getInstance().setAvailableTime(mEventVideoBefore+mEventVideoAfter+1000);
        mH264Buffer = ByteBuffer.allocateDirect(1024 * 800);
        mAACBuffer = ByteBuffer.allocateDirect(1024*100);
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
        videoEncoder = new VideoEncoder(mVideoWidth, mVideoHeight,mRotation);
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
    public void startContinueRecord(@NonNull IRecordListener listener) {
        if (recordMode != Mode.CONTINUE) {
            Logger.e(TAG, "仅持续录像模式支持");
            return;
        }

        if (isStartRecord){
            Logger.w(TAG,"录像开启错误:持续录像已开启");
            return;
        }

        isVideoEncoding = true;
        isStartRecord = true;
        startVideoEncoder();
        //音频开启
        if (isAudioEnable){
            startAudioEncoder();
        }
        //开始 muxer
        mContinueRecordThread = new ContinueRecordThread(listener);
        mContinueRecordThread.start();
        statusListener.onContinueRecordListener(true);
    }

    /**
     * 事件录像要提前开启
     * 音视频采集开始
     */
    public void startReadyRecord() {
        if (recordMode != Mode.EVENT) {
            Logger.e(TAG,"录像开启错误:仅事件视频模式支持");
            return;
        }

        if (isVideoEncoding){
            Logger.w(TAG,"录像开启错误:事件录像准备已开启");
            return;
        }
        isVideoEncoding = true;
        if (isAudioEnable){
            startAudioEncoder();
            audioEncoder.setEncoderListener(this);
        }
        startVideoEncoder();
        videoEncoder.setEncoderListener(this);
        statusListener.onEventRecordListener(true);
    }

    /**
     * 开启事件触发录像,仅 {@link Mode#EVENT} 支持
     * {@link VideoManager#startReadyRecord} 已调用
     * @param videoType
     * @param listener
     */
    public void eventRecord(@NonNull VideoType videoType,@NonNull  IRecordListener listener) {
        if (recordMode != Mode.EVENT) {
            Logger.e(TAG, "仅事件录像支持");
            return;
        }
        if (!isVideoEncoding) {
            Logger.e(TAG, "事件录像:当前录像已经停止");
            return;
        }
        //如果是开门事件 则继续
        if (videoType != VideoType.TYPE_OPENDOOR && eventRecordThread != null && hasCallBacks(eventRecordThread)){
            Logger.w(TAG,"其他事件视频任务等待中,不继续");
            return;
        }

        if (videoType != VideoType.TYPE_OPENDOOR && eventRecordThread != null && eventRecordThread.isRunning){
            Logger.w(TAG,"其他事件视频任务执行中,不继续");
            return;
        }

        int delay = mEventVideoAfter;
        long cnt = System.nanoTime() / 1000;//us
        long lastPts = H264CacheManager.getInstance().getLastVideoTime();
        Logger.d(TAG,"cnt ----------:"+cnt);//us
        Logger.d(TAG,"lastPts ------:"+lastPts); //us
        long space = (cnt - lastPts) / 1000;
        Logger.d(TAG,"cnt - last----:"+ space); //ms

        //开门事件 不计视频重复时间
        if (space < mEventVideoBefore && videoType != VideoType.TYPE_OPENDOOR){
            delay = mEventVideoAfter + mEventVideoBefore - (int)space;
        }
        Logger.d(TAG,"等待时间:"+delay);
        eventRecordThread = new EventRecordThread(listener);
        eventHandler.postDelayed(eventRecordThread, delay);
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
            Logger.e(TAG,"停止错误:非持续录像");
            return;
        }

        if (!isStartRecord) {
            Logger.w(TAG,"停止错误:持续录像已停止");
            return;
        }
        isVideoEncoding = false;
        isStartRecord = false;
        mH264Queue.add(new H264Data(null, null));
        if (isAudioEnable){
            stopAudioEncoder();
        }
        stopVideoEncoder();
        statusListener.onContinueRecordListener(false);
    }

    /**
     * 停止事件录像缓存 停止音视频 采集
     */
    public void stopReadyRecord() {
        if (recordMode != Mode.EVENT){
            Logger.e(TAG,"停止错误:非事件录像");
            return;
        }
        if (!isVideoEncoding){
            Logger.w(TAG,"停止错误:事件录像准备已停止");
            return;
        }
        isVideoEncoding = false;
        if (isAudioEnable){
            stopAudioEncoder();
            AACCacheManager.getInstance().clear();
        }
        stopVideoEncoder();
        H264CacheManager.getInstance().clear();
        statusListener.onEventRecordListener(false);
    }

    @Override
    public void onH264(byte[] bytes, int flags, long pts, int size, int offset) {
//         Logger.d(TAG, "h264 ------------------------size:" + mH264Queue.size());
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
        private String path;
        private long createTime;

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

            String[] info = listener.getVideoPathAndName();
            path = info[0];
            //新的文件名,时间戳名
            createTime = Long.parseLong(info[1]);
            try {
                muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                MediaFormat videoFormat = null;
                byte[] header_sps;
                if (mRotation % 180 != 0){
                    videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VideoConfig.videoHeight, VideoConfig.videoWidth);
                    header_sps = new byte[]{0, 0, 0, 1, 103, 66, -128, 30, -38, 7, -127, 70, -128, 109, 10, 19, 80};

                }else {
                    videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VideoConfig.videoWidth, VideoConfig.videoHeight);
                    header_sps = new byte[]{0, 0, 0, 1, 103, 66, -128, 30, -38, 2, -128, -10, -128, 109, 10, 19, 80};
                }
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
                        listener.onRecordStart(path,startTime,isAudioEnable);
                    }
                }else {
                    if (videoTrack != -1) {
                        muxer.start();
                        startTime = System.nanoTime() / 1000;
                        isstop = false;
                        listener.onRecordStart(path,startTime,isAudioEnable);

                    }
                }
                Logger.d(TAG, "开启新一次录像");
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
                    //Logger.d(TAG, "h264 take :size:" + mH264Queue.size());
                    long frameUs = take.getPts();
                    byte[] data = take.getData();
//                    Logger.e(TAG,"data length:"+data.length);
                    mH264Buffer.put(data);
                    mH264Buffer.flip();

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = take.getFlags();
                    bufferInfo.presentationTimeUs = take.getPts();
                    bufferInfo.size = take.getSize();
                    bufferInfo.offset = take.getOffset();

                    // Logger.d(TAG, "buffer info :" + bufferInfo.presentationTimeUs);

                    synchronized (SyncFlag.syncObj) {
                        muxer.writeSampleData(videoTrack, mH264Buffer, bufferInfo);
                    }

                    mH264Buffer.clear();
                    listener.onRecording(frameUs - startTime);
                    if (frameUs - startTime > VideoConfig.continueVideoLength) {
                        isstop = true;
                        if (muxer != null) {
                            try {
                                muxer.stop();
                                muxer.release();
                            } catch (Exception e) {
                                listener.onError();
                                e.printStackTrace();
                            } finally {
                                muxer = null;
                                //更新上次视频信息
                                long endTime = System.currentTimeMillis();
                                listener.onRecordEnd(path, createTime,endTime);
                                Logger.d(TAG, "结束一次录像");
                            }
                        }
                        startMuxer();
                    }
                } catch (InterruptedException e) {
                    listener.onError();
                    e.printStackTrace();
                }
            }

            Logger.d(TAG, "尝试结束最后一次");
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
                    //更新上次视频信息
                    long endTime = System.currentTimeMillis();
                    listener.onRecordEnd(path, createTime,endTime);
                    Logger.d(TAG, "结束最后一次录像");
                }
            }
        }

        @Override
        public void onAAC(byte[] bytes, int flag, long pts, int size, int offset) {
            if (bytes.length < 50) {
                return;
            }
            if (isStartRecord && !isstop) {
                mAACBuffer.put(bytes);
                mAACBuffer.flip();
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                bufferInfo.flags = flag;
                bufferInfo.presentationTimeUs = pts;
                bufferInfo.size = size;
                bufferInfo.offset = offset;
                synchronized (SyncFlag.syncObj) {
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
//            Logger.d(TAG, "h264 offer:" + offer + ",size:" + mH264Queue.size());
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
        private String path;
        private long createTime;
        private long startVideoTime;
        private long endVideoTime;
        private boolean obtainIFrame;
        private boolean isRunning;
        public EventRecordThread(@NonNull IRecordListener listener) {
            setName("EventRecordThread");
            this.listener = listener;
            startMuxer();
        }

        private void startMuxer() {
            String[] info = listener.getVideoPathAndName();
            path = info[0];
            createTime = Long.parseLong(info[1]);

            try {
                muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                MediaFormat videoFormat = null;
                byte[] header_sps;
                if (mRotation % 180 != 0){
                    videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VideoConfig.videoHeight, VideoConfig.videoWidth);
                    header_sps = new byte[]{0, 0, 0, 1, 103, 66, -128, 30, -38, 7, -127, 70, -128, 109, 10, 19, 80};

                }else {
                    videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VideoConfig.videoWidth, VideoConfig.videoHeight);
                    header_sps = new byte[]{0, 0, 0, 1, 103, 66, -128, 30, -38, 2, -128, -10, -128, 109, 10, 19, 80};
                }
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
                        listener.onRecordStart(path, createTime,isAudioEnable);
                    } else {
                        throw new Exception("add track failed");
                    }
                }else {
                    if (videoTrack != -1) {
                        muxer.start();
                        startVideoTime = System.nanoTime()/1000;//us
                        Logger.d(TAG,"事件视频开始时间:"+startVideoTime);
                        listener.onRecordStart(path, createTime,isAudioEnable);
                    } else {
                        throw new Exception("add track failed");
                    }
                }
                Logger.d(TAG, "muxer ready");
            } catch (Exception e) {
                listener.onError();
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            isRunning = true;
            try {
                LinkedList<H264Data> h264Cache = H264CacheManager.getInstance().getCloneCache();
                LinkedList<AACData> aacCache = AACCacheManager.getInstance().getCloneCache();
                int size = h264Cache.size();
                Logger.d(TAG, "h264Cache.size:" + size);
                if (size == 0){
                    Logger.e(TAG,"视频帧缓存大小为0");
                    isRunning = false;
                    File file = new File(path);
                    if (file.isFile()) file.delete();
                    return;
                }
                listener.onRecording(0);
                long start = 0l;
                for (int i = 0; i < size; i++) {

                    H264Data peek = h264Cache.get(i);
                    if (i == 0){
                       start = peek.getPts();
                    }

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = peek.getFlags();
                    bufferInfo.presentationTimeUs = peek.getPts();
                    bufferInfo.size = peek.getSize();
                    bufferInfo.offset = peek.getOffset();

                    byte[] data = peek.getData();
                    if (!obtainIFrame){
                        if (bufferInfo.flags != 1){
                            continue;
                        }else {
                            //视频开始时间 us
                            startVideoTime = bufferInfo.presentationTimeUs;
                            obtainIFrame = true;
                            Logger.e(TAG, "IFrame h264 buffer info :" + bufferInfo.presentationTimeUs +"flags:"+bufferInfo.flags+ ",index:" + i + ",size:" + data.length );
                        }
                    }

                    if (mLastVideoTime >= bufferInfo.presentationTimeUs) {
                        Logger.e(TAG, "error h264 buffer info :" + bufferInfo.presentationTimeUs +"flags:"+bufferInfo.flags+ ",index:" + i + ",size:" + data.length );
                        continue;
                    }

                    if (data.length > 50*1024){
                        Logger.d(TAG,"data.length:"+data.length);
                    }
                    mH264Buffer.put(data);
                    mH264Buffer.flip();
//                    Logger.e(TAG, "h264 buffer info :" + bufferInfo.presentationTimeUs +"flags:"+bufferInfo.flags+ ",index:" + i + ",size:" + data.length );
                    mLastVideoTime = bufferInfo.presentationTimeUs;
                    muxer.writeSampleData(videoTrack, mH264Buffer, bufferInfo);
                    mH264Buffer.clear();

                    if (bufferInfo.presentationTimeUs - startVideoTime > (mEventVideoAfter+mEventVideoBefore)*1000){
                        endVideoTime = bufferInfo.presentationTimeUs;
                        H264CacheManager.getInstance().setLastVideoTime(bufferInfo.presentationTimeUs);
                        Logger.d(TAG,"h264 interval:"+(endVideoTime - startVideoTime));
                        Logger.d(TAG,"h264 time overflow,break loop");
                        break;
                    }

                    if (i == size - 1){
                        endVideoTime = bufferInfo.presentationTimeUs;
                        H264CacheManager.getInstance().setLastVideoTime(bufferInfo.presentationTimeUs);
                        Logger.d(TAG,"h264 interval:"+(endVideoTime - startVideoTime));
                        Logger.d(TAG,"h264 is not enough");
                    }
                }

                if (isAudioEnable){
                    int aacSize = aacCache.size();
                    Logger.e(TAG, "aacCache.size:" + aacSize);

                    for (int i = 0; i < aacSize; i++) {
                        AACData poll = aacCache.get(i);
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        bufferInfo.flags = poll.getFlag();
                        bufferInfo.presentationTimeUs = poll.getPts();
                        bufferInfo.size = poll.getSize();
                        bufferInfo.offset = poll.getOffset();
                        byte[] data = poll.getData();

                        if (bufferInfo.presentationTimeUs < startVideoTime){
                            Logger.d(TAG,"aac time not match");
                            continue;
                        }

                        mAACBuffer.put(data);
                        mAACBuffer.flip();
                        if (mLastAudioTime >= bufferInfo.presentationTimeUs) {
                            Logger.e(TAG, "error aac buffer info :" + bufferInfo.presentationTimeUs +"flags:"+bufferInfo.flags+ ",index:" + i + ",size:" + data.length );
                            continue;
                        }
//                        Logger.e(TAG, "aac buffer info :" + bufferInfo.presentationTimeUs +"flags:"+bufferInfo.flags+ ",index:" + i + ",size:" + data.length );
                        mLastAudioTime = bufferInfo.presentationTimeUs;
                        muxer.writeSampleData(audioTrack, mAACBuffer, bufferInfo);
                        mAACBuffer.clear();

                        if (bufferInfo.presentationTimeUs > endVideoTime){
                            Logger.d(TAG,"aac time overflow ,break loop");
                            break;
                        }
                    }
                }

                if (muxer != null) {
                    try {
                        muxer.stop();
                        muxer.release();
                    } catch (Exception e) {
                        listener.onError();
                        e.printStackTrace();
                    } finally {
                        long endTime = System.currentTimeMillis();
                        Logger.e(TAG,"更新触发视频信息 endTime:"+endTime);
                        listener.onRecordEnd(path, createTime,endTime);
                        isRunning = false;
                    }
                }
            } catch (Exception e) {
                listener.onError();
                e.printStackTrace();
            }
        }
    }
    public interface IRecordListener1{
        void onContinueRecordListener(boolean isRecording);
        void onEventRecordListener(boolean isReading);
    }
    public interface IRecordListener {

        String[] getVideoPathAndName();

        void onRecordStart(String path, long startTime, boolean isAudioEable);

        void onRecording(long us);

        void onRecordEnd(String path, long createTime, long endTime);

        void onError();
    }
}
