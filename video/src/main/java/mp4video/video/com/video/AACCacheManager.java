package mp4video.video.com.video;

import java.util.LinkedList;

public class AACCacheManager {

    private static final String TAG = AACCacheManager.class.getSimpleName();
    private static AACCacheManager instance = new AACCacheManager();
    private int time = VideoConfig.aacCacheTime;
    private LinkedList<AACData> cache = new LinkedList<>();

    public static AACCacheManager getInstance(){
        return instance;
    }

    public synchronized void add(AACData aac){
        while (isTimeout(aac)){
           cache.remove();
        }
        cache.offer(aac);
    }

    private boolean isTimeout(AACData aac){
        if (aac == null){
            return false;
        }
        if (cache.size() <= 0){
            return false;
        }
        if (aac.getPts() - cache.getFirst().getPts()> time * 1000){
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

    public void clear(){
        cache.clear();
    }
}
