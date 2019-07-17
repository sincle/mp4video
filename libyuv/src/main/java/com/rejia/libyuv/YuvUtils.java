package com.rejia.libyuv;

public class YuvUtils {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("rejia_jni_yuv");
    }

    /**
     * yyyyyyy vu vu ---> yyyyyyyy u u v v
     * @param src
     * @param dst
     * @param width
     * @param height
     */
    public native void nv21ToI420(byte[] src,byte[] dst,int width,int height);

    /**
     * 仅支持90，270，如果编码器使用旋转数据 注意调整 width height
     * @param src
     * @param dst
     * @param width
     * @param height
     * @param degree
     */
    public native void rotateI420(byte[] src,byte[] dst,int width,int height,int degree);

    /**
     * 暂无
     * yyyyyyyy uv uv yyyyyyyy u u v v
     *
     * @param src
     * @param dst
     * @param width
     * @param height
     */
    public native void Y420SPToI420(byte[] src,byte[] dst,int width,int height);

    /**
     * 暂无
     * @param src
     * @param dst
     * @param width
     * @param height
     */
    public native void nv21ToY420SP(byte[] src,byte[] dst,int width,int height);

    /**
     * yyyyyyyy u u v v -----> yyyyyyyy uv uv
     * @param src
     * @param dst
     * @param width
     * @param height
     */
    public native void I420ToY420SP(byte[] src,byte[] dst,int width,int height);
}
