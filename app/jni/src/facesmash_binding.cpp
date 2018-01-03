#include <jni.h>
#include "locator/locator.hpp"
#include "service/camera_android.h"
#include <tuple>



namespace gamee {

static std::pair<int, int> resolution{-1, -1};
static int bitsPerPixel = -1;


std::tuple<int, int, int> facesmashGetCameraParams() {
    return std::make_tuple(resolution.first, resolution.second, bitsPerPixel);
}

} // namespace gamee


extern "C" {

void Java_com_cynny_gamee_facesmash_FaceSmashActivity_WriteFrameCamera(JNIEnv* env, jobject obj, jbyteArray frame) {
    jbyte* data = env->GetByteArrayElements(frame, 0);
    auto length = env->GetArrayLength(frame);
    std::vector v{data, data + length};
    auto& camera = static_cast<gamee::CameraAndroid&>(gamee::Locator::Camera::ref());
    camera.setPixels(data);
    env->ReleaseByteArrayElements(frame, data, 0);
}


void Java_com_cynny_gamee_facesmash_FaceSmashActivity_WriteCameraParams(JNIEnv* env, jobject obj, jint width, jint height, jint bits) {
    gamee::resolution = {width, height};
    gamee::bitsPerPixel = bits;
}

} // extern "C"