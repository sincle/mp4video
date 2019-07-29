package mp4video.video.com.video;

import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.os.HandlerThread;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Queue;


public class VideoManager implements IPreviewFrame, VideoEncoder.IH264Listener {
    private static VideoManager mInstance;
    private String TAG = VideoManager.class.getSimpleName();
    private int mVideoWidth;
    private int mVideoHeight;
    private VideoEncoder videoEncoder;
    private MediaMuxer mediaMuxer;
    private int mVideoTrack;
    private ByteBuffer mBuffer;

    private VideoManager() {
    }

    /**
     * @param videoLenght 视频间隔
     * @param width       视频宽
     * @param height      视频高
     */
    public void setup(int videoLenght, int width, int height,boolean isAudioEnable){
        this.mVideoWidth = width;
        this.mVideoHeight = height;
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
        videoEncoder = new VideoEncoder(mVideoWidth, mVideoHeight);
        videoEncoder.setEncoderListener(this);
    }
    @Override
    public void frame(byte[] NV21, int width, int height,int degree) {
        videoEncoder.encode(NV21,degree);

    }

    @Override
    public void onH264(byte[] bytes, MediaCodec.BufferInfo bufferInfo) {
        //bytes 为 编码后数据
        H264Wrapper h264Wrapper = new H264Wrapper(bytes, bufferInfo,System.currentTimeMillis());
        H264CacheManager.getInstance().add(h264Wrapper);
    }

    public void startMerge(String name) {
        try{
            mediaMuxer = new MediaMuxer(name, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoTrack = mediaMuxer.addTrack(videoEncoder.encoder.getOutputFormat());
            Log.e(TAG,"mvtrack:"+mVideoTrack);
            if (mVideoTrack != -1) {
                start();
            }

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void start() {
        new Thread(){
            @Override
            public void run() {

                Queue<H264Wrapper> cache = H264CacheManager.getInstance().getCache();

                for (int i = 0; i < cache.size(); i++) {
                    H264Wrapper peek = cache.peek();
                    byte[] data = peek.getData();
                    mBuffer = ByteBuffer.allocateDirect(data.length);
                    mBuffer.put(data);
                    mBuffer.flip();
                    mediaMuxer.writeSampleData(mVideoTrack,mBuffer,peek.getBufferInfo());
                    mBuffer.clear();
                }
            }
        }.start();
    }
}
