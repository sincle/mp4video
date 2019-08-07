package mp4video.video.com.video;

import java.util.LinkedList;

public class H264CacheManager {

    private static final String TAG = H264CacheManager.class.getSimpleName();
    private static H264CacheManager instance = new H264CacheManager();
    private int time = VideoConfig.h264CacheTime;
    private LinkedList<H264Data> cache = new LinkedList<>();

    public static H264CacheManager getInstance(){
        return instance;
    }

    public synchronized void add(H264Data h264){
        if (isTimeout(cache.peek())){
            H264Data remove = cache.remove();
//            Log.e(TAG,"remove:"+remove.hashCode());
        }
        boolean offer = cache.offer(h264);
    }

    private boolean isTimeout(H264Data h264){
        if (h264 == null){
            return false;
        }

        if (System.nanoTime()/1000 - h264.getPts() > time * 1000){
            return true;
        }
        return false;
    }

    public synchronized LinkedList<H264Data> getCloneCache() {
        return  (LinkedList<H264Data>)cache.clone();
    }

    public LinkedList<H264Data> getCache() {
        return cache;
    }

    /**
     * @param time ms
     */
    public void setAvailableTime(int time) {
        this.time = time;
    }
    public void clear(){
        cache.clear();
    }
}
