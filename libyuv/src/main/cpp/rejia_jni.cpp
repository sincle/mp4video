#include <jni.h>
#include <string>
#include <libyuv/convert.h>

extern "C"
JNIEXPORT void JNICALL
Java_com_rejia_libyuv_YuvUtils_nv21ToI420(JNIEnv *env, jobject instance, jbyteArray src_,
                                                  jbyteArray dst_, jint width, jint height) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jbyte *dst = env->GetByteArrayElements(dst_, NULL);

    jint src_y_size = width * height;

    jint src_u_size = (width >> 1) * (height >> 1);

    jbyte *src_nv21_y_data = src;
    jbyte *src_nv21_vu_data = src + src_y_size;

    jbyte *src_i420_y_data = dst;
    jbyte *src_i420_u_data = dst + src_y_size;
    jbyte *src_i420_v_data = dst + src_y_size + src_u_size;



    libyuv::NV21ToI420((const uint8 *) src_nv21_y_data, width,
                       (const uint8 *) src_nv21_vu_data, width,
                       (uint8 *) src_i420_y_data, width,
                       (uint8 *) src_i420_u_data, width >> 1,
                       (uint8 *) src_i420_v_data, width >> 1,
                       width, height);

    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dst_, dst, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_rejia_libyuv_YuvUtils_rotateI420(JNIEnv *env, jobject instance, jbyteArray src_,
                                              jbyteArray dst_, jint width, jint height,
                                              jint degree) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jbyte *dst = env->GetByteArrayElements(dst_, NULL);


    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src;
    jbyte *src_i420_u_data = src + src_i420_y_size;
    jbyte *src_i420_v_data = src + src_i420_y_size + src_i420_u_size;

    jbyte *dst_i420_y_data = dst;
    jbyte *dst_i420_u_data = dst + src_i420_y_size;
    jbyte *dst_i420_v_data = dst + src_i420_y_size + src_i420_u_size;

    if (degree == libyuv::kRotate90 || degree == libyuv::kRotate270) {
        libyuv::I420Rotate((const uint8 *) src_i420_y_data, width,
                           (const uint8 *) src_i420_u_data, width >> 1,
                           (const uint8 *) src_i420_v_data, width >> 1,
                           (uint8 *) dst_i420_y_data, height,
                           (uint8 *) dst_i420_u_data, height >> 1,
                           (uint8 *) dst_i420_v_data, height >> 1,
                           width, height,
                           (libyuv::RotationMode) degree);
    }
//    free(src_y);
    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dst_, dst, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_rejia_libyuv_YuvUtils_Y420SPToI420(JNIEnv *env, jobject instance, jbyteArray src_,
                                                jbyteArray dst_, jint width, jint height) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jbyte *dst = env->GetByteArrayElements(dst_, NULL);


    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dst_, dst, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_rejia_libyuv_YuvUtils_nv21ToY420SP(JNIEnv *env, jobject instance, jbyteArray src_,
                                                jbyteArray dst_, jint width, jint height) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jbyte *dst = env->GetByteArrayElements(dst_, NULL);

    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dst_, dst, 0);
}extern "C"
JNIEXPORT void JNICALL
Java_com_rejia_libyuv_YuvUtils_I420ToY420SP(JNIEnv *env, jobject instance, jbyteArray src_,
                                                jbyteArray dst_, jint width, jint height) {
    jbyte *src = env->GetByteArrayElements(src_, NULL);
    jbyte *dst = env->GetByteArrayElements(dst_, NULL);

    jint src_i420_y_size = width * height;
    jint src_i420_u_size = (width >> 1) * (height >> 1);

    jbyte *src_i420_y_data = src;
    jbyte *src_i420_u_data = src + src_i420_y_size;
    jbyte *src_i420_v_data = src + src_i420_y_size + src_i420_u_size;

    jbyte *dst_y420sp_y_data = dst;
    jbyte *dst_y420sp_vu_data = dst + src_i420_y_size;

    libyuv::I420ToNV12((const uint8 *) src_i420_y_data,width,
                       (const uint8 *) src_i420_u_data,width >> 1,
                       (const uint8 *) src_i420_v_data,width >> 1,
                       (uint8 *) dst_y420sp_y_data,width,
                       (uint8 *) dst_y420sp_vu_data,width,
                       width,height
    );
    env->ReleaseByteArrayElements(src_, src, 0);
    env->ReleaseByteArrayElements(dst_, dst, 0);
}