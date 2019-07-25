package mp4video.video.com.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Base64;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageUtils {


    public static Bitmap clipYuvImg(byte[] img, int width, int height, int left, int top, int right, int bottom, int compress) {

        ByteArrayOutputStream stream = null;
        try {
            YuvImage image = new YuvImage(img, ImageFormat.NV21, width, height, null);
            if (image != null) {
                stream = new ByteArrayOutputStream();
                image.compressToJpeg(new Rect(left, top, right, bottom), compress, stream);

                Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                //因为图片会放生旋转，因此要对图片进行旋转到和手机在一个方向上
                return rotateMyBitmap(bmp);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static Bitmap rotateMyBitmap(Bitmap bmp) {
        //*****旋转一下
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap nbmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        bmp.recycle();
        return nbmp2;
    }

    public static String bitmapToBase64(Bitmap bitmap) {

        // 要返回的字符串
        String reslut = null;

        ByteArrayOutputStream baos = null;

        try {

            if (bitmap != null) {

                baos = new ByteArrayOutputStream();
                /**
                 * 压缩只对保存有效果bitmap还是原来的大小
                 */
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                baos.flush();
                baos.close();
                // 转换为字节数组
                byte[] byteArray = baos.toByteArray();
                // 转换为字符串
                reslut = Base64.encodeToString(byteArray, Base64.DEFAULT);
                bitmap.recycle();
            } else {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

            try {
                if (baos != null) {
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return reslut;
    }

    public byte[] bitmap2RGB(Bitmap bitmap) {
        int bytes = bitmap.getByteCount();  //返回可用于储存此位图像素的最小字节数

        ByteBuffer buffer = ByteBuffer.allocate(bytes); //  使用allocate()静态方法创建字节缓冲区
        bitmap.copyPixelsToBuffer(buffer); // 将位图的像素复制到指定的缓冲区

        byte[] rgba = buffer.array();
        byte[] pixels = new byte[(rgba.length / 4) * 3];

        int count = rgba.length / 4;

        //Bitmap像素点的色彩通道排列顺序是RGBA
        for (int i = 0; i < count; i++) {

            pixels[i * 3] = rgba[i * 4];        //R
            pixels[i * 3 + 1] = rgba[i * 4 + 1];    //G
            pixels[i * 3 + 2] = rgba[i * 4 + 2];       //B

        }

        return pixels;
    }

    public static byte[] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {
        int[] argb = new int[inputWidth * inputHeight];
        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
        scaled.recycle();
        return yuv;
    }

    static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;
        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0; // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;
                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the sampling is every other
                // pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }
                index++;
            }
        }
    }

    private byte[] YUV420p2NV21(byte[] yuv, int width, int height) {
        int frameSize = width * height;
        byte[] nv21 = new byte[frameSize * 3 / 2];
        //y 分量不变
        System.arraycopy(yuv, 0, nv21, 0, frameSize);
        byte[] u = new byte[frameSize / 4];
        byte[] v = new byte[frameSize / 4];
        System.arraycopy(yuv, frameSize, u, 0, frameSize / 4);
        System.arraycopy(yuv, frameSize + frameSize / 4, v, 0, frameSize / 4);
        for (int i = 0; i < u.length; i++) {
            nv21[frameSize] = v[i];
            nv21[frameSize + 1] = u[i];
            frameSize++;
        }
        return nv21;
    }

    /**
     * I420转nv21
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static byte[] I420Tonv21(byte[] data, int width, int height) {
       // long millis = System.currentTimeMillis();
        byte[] ret = new byte[data.length];
        int total = width * height;
        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total + total / 4, total / 4);
        bufferY.put(data, 0, total);
        for (int i = 0; i < total / 4; i += 1) {
            bufferV.put(data[total + i]);
            bufferU.put(data[i + total + total / 4]);
        }
       // Logger.d("I420Tonv21", "I420Tonv21:" + (System.currentTimeMillis() - millis) + "ms");
        return ret;
    }

    public static void saveFile(String filePath, byte[] b,String fileName) {

        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        try {
            File dir = new File(filePath);
            if(!dir.exists()){//判断文件目录是否存在
                dir.mkdirs();
            }
            file = new File(filePath,fileName);
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(b);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    public static byte[] readFile(File file) {
        // 需要读取的文件，参数是文件的路径名加文件名
        if (file.isFile()) {
            // 以字节流方法读取文件

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                // 设置一个，每次 装载信息的容器
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                // 开始读取数据
                int len = 0;// 每次读取到的数据的长度
                while ((len = fis.read(buffer)) != -1) {// len值为-1时，表示没有数据了
                    // append方法往sb对象里面添加数据
                    outputStream.write(buffer, 0, len);
                }
                // 输出字符串
                return outputStream.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("文件不存在！");
        }
        return null;
    }
}
