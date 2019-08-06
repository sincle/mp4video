package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;


public class VideoManager implements IPreviewFrame, AudioEncoder.IAACListener, VideoEncoder.IH264Listener {
    private static final int MSG_EVENT_RECORD = 0x01;
    private volatile boolean isStartRecord;
    private volatile boolean isStartVideoEncoder;
    private volatile boolean isVideoEncoding;

    private HandlerThread eventThread;
    private EventHandler eventHandler;
    private AudioEncoder audioEncoder;
    private boolean isStartAudioEncoder;
    private ContinueRecordThread mContinueRecordThread;

    public enum Mode {
        CONTINUE, EVENT
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
     * @param width  视频宽
     * @param height 视频高
     */
    public void setup(Mode mode, int width, int height, boolean isAudioEnable) {
        this.recordMode = mode;
        this.mVideoWidth = width;
        this.mVideoHeight = height;
        H264CacheManager.getInstance().setAvailableTime(10000);
        AACCacheManager.getInstance().setAvailableTime(10000);
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

    private void startVideoEncoder() {
        if (isStartVideoEncoder) {
            return;
        }
        isStartVideoEncoder = true;
        videoEncoder = new VideoEncoder(mVideoWidth, mVideoHeight);
        videoEncoder.start();
//        videoEncoder.setEncoderListener(this);
    }

    private void startAudioEncoder() {
        if (isStartAudioEncoder) {
            return;
        }
        isStartAudioEncoder = true;
        audioEncoder = new AudioEncoder();
        audioEncoder.start();
//        audioEncoder.setEncoderListener(this);

    }

    private void stopAudioEncoder() {
        if (!isStartAudioEncoder){
            return;
        }
        isStartAudioEncoder = false;

        audioEncoder.stop();
    }

    private void stopVideoEncoder() {
        if (!isStartVideoEncoder){
            return;
        }
        isStartVideoEncoder = false;
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
    public void startRecord(IRecordListener listener) {
        if (recordMode != Mode.CONTINUE) {
            Log.e(TAG, "仅持续录像模式支持");
            return;
        }
        isStartRecord = true;
        isVideoEncoding = true;
        startVideoEncoder();
        startAudioEncoder();
        //开始 muxer
        mContinueRecordThread = new ContinueRecordThread(listener);
        mContinueRecordThread.start();
    }

    public void startReadyRecord(){
        startVideoEncoder();
        startAudioEncoder();

        if (recordMode == Mode.EVENT) {
            isVideoEncoding = true;
            audioEncoder.setEncoderListener(this);
            videoEncoder.setEncoderListener(this);
        }
    }
    /**
     * 开启事件触发录像,仅 {@link Mode#EVENT} 支持
     *
     * @param path
     */
    public void eventRecord(String path, IRecordListener listener) {
        if (recordMode != Mode.EVENT) {
            Log.e(TAG, "仅事件录像支持");
            return;
        }
        eventHandler.postDelayed(new EventRecordThread(path, listener), 5 * 1000);
    }

    /**
     * 在事件视频模式下,录制固定时长视频 仅 {@link Mode#EVENT} 支持
     */
    public void startRecord(String path, final int length, IRecordListener listener) {
        if (recordMode != Mode.EVENT) {
            Log.e(TAG, "仅事件录像支持");
            return;
        }

        if (isStartRecord) {
            return;
        }
        isStartRecord = true;
        eventHandler.post(new OnceRecordThread(path, length, listener));
    }

    public void stopRecord() {
        if (!isStartRecord){
            return;
        }
        isVideoEncoding = false;
        isStartRecord = false;
        mH264Queue.add(new H264Data(null,null));
        stopAudioEncoder();
        stopVideoEncoder();
    }

    @Override
    public void onH264(byte[] bytes,int flags, long pts, int size, int offset) {
//         Log.d(TAG, "h264 ------------------------size:" + mH264Queue.size());
        if (recordMode == Mode.EVENT) {
            H264Data h264Data = new H264Data(bytes, flags, pts, size, offset);
            if (isStartRecord) {
                boolean offer = mH264Queue.offer(h264Data);
                Log.d(TAG, "h264 offer:" + offer + ",size:" + mH264Queue.size());
            }
            //缓存 todo
            H264CacheManager.getInstance().add(h264Data);
        }
    }

    @Override
    public void onAAC(byte[] bytes,  int flag,long pts,int size,int offset) {
        if (recordMode == Mode.EVENT) {
            AACData aacData = new AACData(bytes, flag,pts,size,offset);
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
            audioEncoder.setEncoderListener(this);
            videoEncoder.setEncoderListener(this);
            startMuxer();
            setName("ContinueRecordThread");
        }

        private void startMuxer() {

            String name = Environment.getExternalStorageDirectory().getAbsolutePath() + "/kang/" + System.currentTimeMillis() + ".mp4";
            try {
                muxer = new MediaMuxer(name, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                MediaFormat videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480);
                byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 30, -38, 2, -128, -10, -128, 109, 10, 19, 80};
                byte[] header_pps = {0, 0, 0, 1, 104, -50, 6, -30};
                videoFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                videoFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

                videoTrack = muxer.addTrack(videoFormat);

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
                        listener.onStart();
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

                    if (frameUs - startTime > 10 * 1000 * 1000) {
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
                                    listener.onEnd();
                                }
                                Log.d(TAG,"结束一次录像");
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

            Log.d(TAG,"尝试结束最后一次");
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
                        listener.onEnd();
                    }
                    Log.d(TAG,"结束最后一次录像");
                }
            }

        }

        @Override
        public void interrupt() {

        }

        @Override
        public void onAAC(byte[] bytes,  int flag,long pts,int size,int offset) {
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
            H264Data h264Data = new H264Data(bytes, flags,pts,size,offset);
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

                MediaFormat audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1);
                audioFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
                byte[] data = new byte[]{(byte) 0x14, (byte) 0x08};
                audioFormat.setByteBuffer("csd-0", ByteBuffer.wrap(data));

                audioTrack = muxer.addTrack(audioFormat);

                if (videoTrack != -1 && audioTrack != -1) {
                    muxer.start();
                    if (listener != null) {
                        listener.onStart();
                    }
                } else {
                    throw new Exception("add track failed");
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

                    if (mLastVideoTime == bufferInfo.presentationTimeUs){
                        Log.e(TAG, "h264 buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i + ",h254Cache:" + data.hashCode());
                    }
                    mLastVideoTime = bufferInfo.presentationTimeUs;
                    muxer.writeSampleData(videoTrack, mH264Buffer, bufferInfo);
                    mH264Buffer.clear();
                }

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

                    if (mLastAudioTime == bufferInfo.presentationTimeUs){
                        Log.e(TAG, "aac buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i +",size:"+data.length+ ",aacCache:" + data.hashCode());
                    }
                    mLastAudioTime = bufferInfo.presentationTimeUs;
//                    Log.e(TAG, "aac buffer info :" + bufferInfo.presentationTimeUs + ",index:" + i +",size:"+data.length+ ",aacCache:" + data.hashCode());
                    muxer.writeSampleData(audioTrack, mAACBuffer, bufferInfo);
                    mAACBuffer.clear();
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
                            listener.onEnd();
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

    private class OnceRecordThread extends Thread {

        private String path;
        private int length;
        private IRecordListener listener;
        private boolean flag;

        public OnceRecordThread(String path, int length, IRecordListener listener) {
            this.path = path;
            this.length = length;
            this.listener = listener;
            setName("OnceRecordThread");
        }

        @Override
        public void run() {

            try {
                MediaMuxer muxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int track = muxer.addTrack(videoEncoder.getEncoder().getOutputFormat());
                Log.e(TAG, "mvtrack:" + track);
                if (track != -1) {
                    muxer.start();
                    if (listener != null) {
                        listener.onStart();
                    }
                } else {
                    throw new Exception("add track failed");
                }

                long start = System.currentTimeMillis();
                if (listener != null) {
                    listener.onRecording();
                }
                while (!flag) {
                    H264Data take = mH264Queue.take();
                    Log.d(TAG, "h264 take :size:" + mH264Queue.size());
                    long frameMs = take.getPts();
                    byte[] data = take.getData();
//                    Log.e(TAG,"data length:"+data.length);
                    mH264Buffer.put(data);
                    mH264Buffer.flip();
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = take.getFlags();
                    bufferInfo.presentationTimeUs = take.getPts();
                    bufferInfo.size = take.getSize();
                    bufferInfo.offset = take.getOffset();

                    Log.d(TAG, "buffer info :" + bufferInfo.presentationTimeUs);
                    muxer.writeSampleData(track, mH264Buffer, bufferInfo);
                    mH264Buffer.clear();
                    if (frameMs - start > length * 1000) {
                        if (muxer != null) {
                            try {
                                muxer.stop();
                                muxer.release();
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                muxer = null;
                                isStartRecord = false;
                                if (listener != null) {
                                    listener.onEnd();
                                }
                                flag = true;
                                mH264Queue.clear();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (listener != null) {
                    listener.onError();
                }
                e.printStackTrace();
            } catch (InterruptedException e) {
                if (listener != null) {
                    listener.onError();
                }
                e.printStackTrace();
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError();
                }
                e.printStackTrace();
            }
        }
    }

    public interface IRecordListener {
        void onStart();

        void onRecording();

        void onEnd();

        void onError();
    }
}
