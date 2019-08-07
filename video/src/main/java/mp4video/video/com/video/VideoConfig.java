package mp4video.video.com.video;

public class VideoConfig {


    private static int videoCacheTime = 10*1000;
    /**
     * 事件视频 时长 ms
     */
    public static int eventVideoAfter = 5*1000;
    public static int aacCacheTime = videoCacheTime;
    public static int h264CacheTime = videoCacheTime;
    public static long continueVideoLength = 10*1000*1000;

    //不可修改
    public static int videoWidth = 640;
    public static int videoHeight = 480;
}
