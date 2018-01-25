#include <SDL.h>
#include <jni.h>
#include <android/log.h>
#include "locator/locator.hpp"
#include "service/camera_android.h"
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <tuple>

// neccessary prototype declaration for licensing
namespace VisageSDK
{
    void initializeLicenseManager(JNIEnv* env, jobject obj, const char *licenseKeyFileName, void (*alertFunction)(const char*) = 0);
}


/**
 * Callback method for license notification.
 *
 * Alerts the user that the license is not valid
 */
static void AlertCallback(const char* warningMessage) {
    __android_log_print(ANDROID_LOG_ERROR, "EMO DETECTOR license", "%s", warningMessage);
}

namespace gamee {

static std::pair<int, int> resolution{-1, -1};

std::atomic_bool cameraAndroidReady{false};
static bool bindingReady{false};
static std::mutex bindingMtx;
static std::condition_variable bindingCv;


std::tuple<int, int> bindingGetCameraParams() {
    std::unique_lock lck{bindingMtx};
    if(!bindingReady)
        bindingCv.wait(lck, [] { return bindingReady; });
    return std::make_tuple(resolution.first, resolution.second);
}


void bindingStartCamera() {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz(env->GetObjectClass(activity));

    // find the identifier of the method to call
    jmethodID method_id = env->GetMethodID(clazz, "StartCamera", "()V");

    // effectively call the Java method
    env->CallVoidMethod(activity, method_id);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);
}


void bindingStopCamera() {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz(env->GetObjectClass(activity));

    // find the identifier of the method to call
    jmethodID method_id = env->GetMethodID(clazz, "StopCamera", "()V");

    // effectively call the Java method
    env->CallVoidMethod(activity, method_id);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);
}

} // namespace gamee


extern "C" {

void Java_com_cynny_gamee_facesmash_FaceSmashActivity_WriteFrameCamera(JNIEnv* env, jobject obj, jbyteArray frame) {
    jbyte* data = env->GetByteArrayElements(frame, 0);
    if(!gamee::cameraAndroidReady) return;
    auto& camera = static_cast<gamee::CameraAndroid&>(gamee::Locator::Camera::ref());
    camera.setPixels(data);
    env->ReleaseByteArrayElements(frame, data, 0);
}


void Java_com_cynny_gamee_facesmash_FaceSmashActivity_WriteCameraParams(JNIEnv* env, jobject obj, jint width, jint height) {
    if(gamee::bindingReady) return;
    std::unique_lock lck{gamee::bindingMtx};
    gamee::resolution = {width, height};
    gamee::bindingReady = true;
    lck.unlock();
    gamee::bindingCv.notify_all();
}


void Java_com_cynny_gamee_facesmash_FaceSmashActivity_InitVisage(JNIEnv* env, jobject obj) {
    auto path = "/data/data/com.cynny.gamee.facesmash/files/visage/578-496-411-691-522-273-235-359-916-935-253.vlc";
    VisageSDK::initializeLicenseManager(env, obj, path, AlertCallback);
}


} // extern "C"