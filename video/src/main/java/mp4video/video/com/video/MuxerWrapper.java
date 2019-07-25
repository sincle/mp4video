package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;

public class MuxerWrapper {

    private boolean isAudioEnable;
    private String TAG = MuxerWrapper.class.getSimpleName();
    private MediaMuxer mediaMuxer;
    private boolean isStarted = false;
    private int mVideoTrack = -1;
    private int mAudioTrack = -1;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;

    public MuxerWrapper(String path, int format,boolean isAudioEnable){
        this.isAudioEnable = isAudioEnable;
        initMediaMuxer(path,format);
    }

    private void initMediaMuxer(String path, int format) {
        try{
            mediaMuxer = new MediaMuxer(path, format);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public MediaFormat getVideoFormat(){
        return videoFormat;
    }

    public MediaFormat getAudioFormat(){
        return audioFormat;
    }

    public void setVideoFormat(MediaFormat format){
        if(videoFormat != null){
            return;
        }
        videoFormat = format;
        if(videoFormat != null){
            mVideoTrack = mediaMuxer.addTrack(videoFormat);
            Log.e(TAG,"mvtrack:"+mVideoTrack);
            if (mVideoTrack != -1) {
                start();
            }
        }
    }

    public void setAudioFormat(MediaFormat format){
        if(audioFormat != null){
            return;
        }
        audioFormat = format;
        if(audioFormat != null){
            mAudioTrack = mediaMuxer.addTrack(audioFormat);
            Log.e(TAG,"matrack:"+mAudioTrack);
            if (mAudioTrack != -1) {
                start();
            }
        }
    }

    public void start(){
        Log.e(TAG, "video:" + mVideoTrack + ", audio：" + mAudioTrack);
        if(isStarted) return;
        if (isAudioEnable){
            if(mediaMuxer != null && (mVideoTrack != -1 && mAudioTrack != -1)){
                mediaMuxer.start();
                isStarted = true;
            }
        }else {
            if(mediaMuxer != null && (mVideoTrack != -1)){
                mediaMuxer.start();
                isStarted = true;
            }
        }

    }
    public void writeVideoData(ByteBuffer buffer, MediaCodec.BufferInfo info){
        if(mediaMuxer != null && mVideoTrack != -1 && isStarted){
           // Log.e("writevideo", "" + mediaMuxer);
            mediaMuxer.writeSampleData(mVideoTrack, buffer, info);
        }
    }

    public void writeAudioData(ByteBuffer buffer, MediaCodec.BufferInfo info){
        if(mediaMuxer != null && mAudioTrack != -1 && isStarted){
            mediaMuxer.writeSampleData(mAudioTrack, buffer, info);
        }
    }

    public void release(){
        isStarted = false;
        if(mediaMuxer != null){
            try {
                mVideoTrack = -1;
                mAudioTrack = -1;
                mediaMuxer.stop();
                mediaMuxer.release();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                mediaMuxer = null;
            }
        }
    }
}
