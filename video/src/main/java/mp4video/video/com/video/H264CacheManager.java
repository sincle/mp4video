package mp4video.video.com.video;

import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;

public class H264CacheManager {

    private static final String TAG = H264CacheManager.class.getSimpleName();
    private static H264CacheManager instance = new H264CacheManager();
    private int time = 5000;
    private Queue<H264Wrapper> cache = new LinkedList<>();

    public static H264CacheManager getInstance(){
        return instance;
    }

    public void add(H264Wrapper h264){
        if (isTimeout(cache.peek())){
            H264Wrapper remove = cache.remove();
            Log.e(TAG,"remove:"+remove.hashCode());
        }
        boolean offer = cache.offer(h264);
        if (offer){
            Log.e(TAG,"offer:"+h264.hashCode());
        }
    }

    private boolean isTimeout(H264Wrapper h264){
        if (h264 == null){
            return false;
        }

        if (System.currentTimeMillis() - h264.getFrameMs() > time){
            return true;
        }
        return false;
    }
}
