package mp4video.video.com.video;

import java.util.LinkedList;

public class AACCacheManager {

    private static final String TAG = AACCacheManager.class.getSimpleName();
    private static AACCacheManager instance = new AACCacheManager();
    private int time = 15000;
    private LinkedList<AACData> cache = new LinkedList<>();

    public static AACCacheManager getInstance(){
        return instance;
    }

    public synchronized void add(AACData aac){
        if (isTimeout(cache.peek())){
            AACData remove = cache.remove();
//            Log.e(TAG,"remove:"+remove.hashCode());
        }
        boolean offer = cache.offer(aac);
    }

    private boolean isTimeout(AACData aac){
        if (aac == null){
            return false;
        }

        if (System.nanoTime()/1000 - aac.getPts() > time * 1000){
            return true;
        }
        return false;
    }

    public synchronized LinkedList<AACData> getCloneCache() {
        return  (LinkedList<AACData>)cache.clone();
    }

    public LinkedList<AACData> getCache() {
        return cache;
    }

    /**
     * @param time ms
     */
    public void setAvailableTime(int time) {
        this.time = time;
    }
}
