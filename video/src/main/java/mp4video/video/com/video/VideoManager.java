package mp4video.video.com.video;

import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


public class VideoManager implements IPreviewFrame {
    private static final int CHANGE = 0x01;
    private static final int ONE = 0x02;
    private static final int RECORD_STOP = 0x03;
    private static VideoManager mInstance;
    private HandlerThread intervalThread;
    private IntervalHandler intervalHandler;
    private String TAG = VideoManager.class.getSimpleName();
    private int mVideoWidth;
    private int mVideoHeight;
    private VideoRecoder videoRecoder;
    private MuxerWrapper muxerWrapper;
    private long lastTime;
    private int mVideoLength = 30;
    private long videoName;
    private long lastVideoName;
    private boolean isStartRecord;
    private boolean isContinue;
    private IRecordListener listener;
    private boolean isAudioEnable;

    private VideoManager() {
    }

    /**
     * @param videoLenght 视频间隔
     * @param width       视频宽
     * @param height      视频高
     */
    public void setup(int videoLenght, int width, int height,boolean isAudioEnable){
        this.mVideoLength = videoLenght;
        this.mVideoWidth = width;
        this.mVideoHeight = height;
        this.listener = listener;
        this.isAudioEnable = isAudioEnable;
        init();
    }
    public static VideoManager getInstance(){
        synchronized (VideoManager.class){
            if (mInstance == null){
                mInstance = new VideoManager();
            }
        }
        return mInstance;
    }
    private void init() {
        intervalThread = new HandlerThread("control");
        intervalThread.start();
        intervalHandler = new IntervalHandler(intervalThread.getLooper());

        videoRecoder = new VideoRecoder(mVideoWidth, mVideoHeight);
//        if (isAudioEnable){
//            audioRecoder = new AudioRecoder();
//        }
    }

    /**
     *
     * @param isContinue
     * @return
     */
    public void startRecord(boolean isContinue) {

        if (!isStartRecord){
            this.isContinue = isContinue;
            isStartRecord = true;
            videoRecoder.start();
            intervalHandler.sendEmptyMessage(CHANGE);
        }
    }

    public void stopRecord() {
        if (isStartRecord){
            isStartRecord = false;
            intervalHandler.sendEmptyMessage(RECORD_STOP);
            videoRecoder.stop();
//            audioRecoder.stop();
            listener.onRecordListener(isStartRecord);
        }
       // videoRecoder.release();
    }
    @Override
    public void frame(byte[] NV21, int width, int height,int degree) {
        videoRecoder.frame(NV21, width, height,degree);
    }

    private class IntervalHandler extends Handler {

        public IntervalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what){
                case RECORD_STOP:
                    synchronized (SyncFlag.syncObj) {
                        intervalHandler.removeMessages(CHANGE);
                        if (muxerWrapper != null) {
                            Log.d(TAG, "最后一次更新视频信息");
                            //更新上次视频信息
                            long endTime = System.currentTimeMillis();
                            Log.e(TAG, "endTime:" + endTime);
                            muxerWrapper.release();
                            muxerWrapper = null;
                        }
                    }
                    break;
                case CHANGE:
                    synchronized (SyncFlag.syncObj) {
                        if (muxerWrapper != null) {
                            //更新上次视频信息
                            long endTime = System.currentTimeMillis();
                            Log.e(TAG,"更新上一次视频信息 endTime:"+endTime);

                            muxerWrapper.release();
                            muxerWrapper = null;
                        }

                        //新的文件名,时间戳名
                        videoName = System.currentTimeMillis();
                        //格式化名
                        String videoPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/kang/"+videoName+".mp4";
                        Log.d(TAG, "新增加一条视频记录:" + videoName + ".mp4");

                        muxerWrapper = new MuxerWrapper(
                                videoPath,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,isAudioEnable);
                        videoRecoder.setMuxer(muxerWrapper);
                        //记录当前视频name 用于更新视频信息操作
                        lastVideoName = videoName;

                        if (isContinue){
                            intervalHandler.sendEmptyMessageDelayed(CHANGE, mVideoLength * 1000);
                        }else {
                            intervalHandler.sendEmptyMessageDelayed(ONE, mVideoLength * 1000);
                        }
                    }
                    break;
                case ONE:
                    stopRecord();
                    break;
            }
        }
    }
    public interface IRecordListener{
        void onRecordListener(boolean isRecording);
    }
}
