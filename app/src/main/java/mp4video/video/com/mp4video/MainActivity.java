package mp4video.video.com.mp4video;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private TextureView textureView;
    private CameraManager cameraManager;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private int mWidth = 640;
    private int mHeight = 480;
    private Handler mHandler = new Handler();
    private ImageReader imageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, 1);//预览数据流最好用非JPEG
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            //创建CameraPreviewSession
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            // 相机已经关闭
            if (null == mCameraDevice) {
                return;
            }
            // 会话准备好后，我们开始显示预览
//            mCaptureSession = session;
            try {
                // 自动对焦
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 打开闪光灯
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // 开启相机预览并添加事件
                CaptureRequest previewRequest = mPreviewRequestBuilder.build();

                //发送请求
                session.setRepeatingRequest(previewRequest,
                        null, mHandler);
                Log.e(TAG, " 开启相机预览并添加事件");
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, " onConfigureFailed 开启预览失败");
        }
    };

    private ImageReader.OnImageAvailableListener mAvaliImageListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.e(TAG,"reader:"+reader.getWidth());
//            Image image = reader.acquireLatestImage();//最后一帧
//            //do something
//            int len = image.getPlanes().length;
//            byte[][] bytes = new byte[len][];
//            int count = 0;
//            for (int i = 0; i < len; i++) {
//                ByteBuffer buffer = image.getPlanes()[i].getBuffer();
//                int remaining = buffer.remaining();
//                byte[] data = new byte[remaining];
//                byte[] _data = new byte[remaining];
//                buffer.get(data);
//                System.arraycopy(data, 0, _data, 0, remaining);
//                bytes[i] = _data;
//                count += remaining;
//            }
//            //数据流都在 bytes[][] 中，关于有几个plane，可以看查看 ImageUtils.getNumPlanesForFormat(int format);
//            // ...
//            image.close();//一定要关闭

            //获取预览帧数据
            Image image = reader.acquireNextImage();
            //处理逻辑
            if (image != null){
                Image.Plane y = image.getPlanes()[0];
                ByteBuffer y_buffer = y.getBuffer();
                byte[] y_data = new byte[y_buffer.remaining()];
                Log.d(TAG, "y=" + y_data.length);
                y_buffer.get(y_data);

                Image.Plane u = image.getPlanes()[1];
                ByteBuffer u_buffer = u.getBuffer();
                byte[] u_data = new byte[u_buffer.remaining()];
                Log.d(TAG, "u=" + u_data.length);
                u_buffer.get(u_data);

                Image.Plane v = image.getPlanes()[2];
                ByteBuffer v_buffer = v.getBuffer();
                byte[] v_data = new byte[v_buffer.remaining()];
                Log.d(TAG, "v=" + v_data.length);
                v_buffer.get(v_data);

                image.close();
            }
        }
    };

    private void createCameraPreviewSession() {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(mWidth, mHeight);
        Surface surface = new Surface(texture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mPreviewRequestBuilder.addTarget(surface);
        imageReader.setOnImageAvailableListener(mAvaliImageListener, null);
        //获取 ImageReader 的 Surface
        final Surface readerSurface = imageReader.getSurface();
        mPreviewRequestBuilder.addTarget(readerSurface);
        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface,readerSurface), mSessionStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        textureView.setSurfaceTextureListener(this);
    }

    // CameraCharacteristics  可通过 CameraManager.getCameraCharacteristics() 获取
    private int isHardwareSupported(CameraCharacteristics characteristics) {
        Integer deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == null) {
            Log.e(TAG, "can not get INFO_SUPPORTED_HARDWARE_LEVEL");
            return -1;
        }
        switch (deviceLevel) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                Log.w(TAG, "hardware supported level:LEVEL_FULL");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                Log.w(TAG, "hardware supported level:LEVEL_LEGACY");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                Log.w(TAG, "hardware supported level:LEVEL_3");
                break;
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                Log.w(TAG, "hardware supported level:LEVEL_LIMITED");
                break;
        }
        return deviceLevel;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            //获取可用摄像头列表
            for (String cameraId : cameraManager.getCameraIdList()) {
                //获取相机的相关参数
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                // 不使用前置摄像头。
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                mCameraId = cameraId;
                Log.e(TAG, " 相机可用 ");
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            //不支持Camera2API
        }

        try {
            cameraManager.openCamera(mCameraId, mStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
