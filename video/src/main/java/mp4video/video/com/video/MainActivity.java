package mp4video.video.com.video;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;


public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private String TAG = MainActivity.class.getSimpleName();
    private SurfaceView mSurfaceview = null; // SurfaceView对象：(视图组件)视频显示
    private SurfaceHolder mSurfaceHolder = null; // SurfaceHolder对象：(抽象接口)SurfaceView支持类
    private Camera mCamera;
    private VideoManager manager;
    private long mLastFrameDataTime;
    private String path;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/kang";
        manager = VideoManager.getInstance();
        manager.setup(10,640,480,false);
        manager.startRecord(true);
        mSurfaceview = (SurfaceView) findViewById(R.id.surfaceview);
        mSurfaceHolder = mSurfaceview.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
        mSurfaceHolder.addCallback(this); // SurfaceHolder加入回调接口
        // mSurfaceHolder.setFixedSize(176, 144); // 预览大小設置
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);// 設置顯示器類型，setType必须设置

    }

    private void initCamera(SurfaceHolder holder) {

        try {
            mCamera = Camera.open();
            Camera.Parameters p = mCamera.getParameters();
            p.setPreviewFormat(ImageFormat.NV21);
            p.setPreviewSize(640, 480);
            mCamera.setParameters(p);
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        initCamera(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {

        long currentTimeMillis = System.currentTimeMillis();
//        ImageUtils.saveFile(path, data, currentTimeMillis + ".yuv");
////        if (currentTimeMillis - mLastFrameDataTime >= 100){
////            //保存
////
////            mLastFrameDataTime = System.currentTimeMillis();
////        }
        Log.e(TAG,"---------------------------:"+(System.currentTimeMillis() - currentTimeMillis));
        manager.frame(data,640,480,0);
        camera.addCallbackBuffer(data);
    }
}
