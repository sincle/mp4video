package mp4video.video.com.video;


import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import java.nio.ByteBuffer;

public class VideoRecoder implements IPreviewFrame, IMuxerCallback {

    private static final String TAG = VideoRecoder.class.getSimpleName();
    private EncodeHandler encodeHandler;
    private int mediaWidth = 1280;
    private int mediaHeight = 720;
    private VideoEncoder videoEncoder;
    private MuxerWrapper muxerWrapper;
    private final HandlerThread encodeThread;
    private boolean isStart;

    public VideoRecoder(int width, int height){
        this.mediaHeight = height;
        this.mediaWidth = width;
        encodeThread = new HandlerThread("encode");
        encodeThread.start();
        encodeHandler = new EncodeHandler(encodeThread.getLooper());
        initEncoder(width, height);
    }

    private void initEncoder(int width, int height) {

        videoEncoder = new VideoEncoder(width, height);
    }

    public void setMuxer(MuxerWrapper wrapper){
        this.muxerWrapper = wrapper;
    }

    @Override
    public void frame(byte[] NV21, int width, int height,int degree) {
        if (videoEncoder == null || NV21 == null) {
            return;
        }

        if( encodeHandler.hasMessages(100)){
            return;
        }

        if (!isStart){
            return;
        }
        //int len = NV21.length;
        //byte[] temp = new byte[len];
        //System.arraycopy(NV21, 0, temp, 0, len);
        //Log.e("onPreviewFrame", "" + len +",thread:"+Thread.currentThread().getName());

        Message message = new Message();
        message.what = 100;
        message.obj = NV21;
        message.arg1 = degree;
        encodeHandler.sendMessage(message);
    }
    public void start(){
        if(videoEncoder != null){
            isStart = true;
           // videoEncoder.start();
        }
    }

    public void stop(){
        if(videoEncoder != null){
            isStart =false;
          //  videoEncoder.stop();
        }
    }

    public void release(){
//        if(videoEncoder != null){
//            videoEncoder.releaseCodec();
//            videoEncoder = null;
//        }
//        if(encodeThread != null){
//            try {
//                encodeThread.quit();
//            }catch (Exception e){
//            }
//        }
    }

    @Override
    public void mux(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        synchronized (SyncFlag.syncObj) {
            if (muxerWrapper != null && buffer != null && info != null) {
                muxerWrapper.writeVideoData(buffer, info);
            }
        }
    }

    @Override
    public void onMediaFormat(MediaFormat format) {
        synchronized (SyncFlag.syncObj) {
            if (muxerWrapper != null) {
                muxerWrapper.setVideoFormat(format);
            }
        }
    }

    @Override
    public void onKeyFrame() {

    }

    private class EncodeHandler extends Handler {
        public EncodeHandler(Looper looper){
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
//            Log.e(TAG, "thread:"+Thread.currentThread().getName());
            long currentTime = System.currentTimeMillis();
            videoEncoder.encode((byte[])msg.obj,msg.arg1);
          //  Log.e("encode end", "花费时间:"+(System.currentTimeMillis() - currentTime));
        }
    }
}
