#include <SDL.h>
#include <jni.h>
#include <android/log.h>
#include "locator/locator.hpp"
#include "service/camera_android.h"
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <queue>
#include <string>
#include <tuple>


// helper functions

namespace {

// credits https://stackoverflow.com/a/41820336/2508150
std::string jstring2string(JNIEnv *env, jstring jStr) {
    if (!jStr)
        return "";

    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes",
                                                "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes,
                                                                       env->NewStringUTF(
                                                                               "UTF-8"));

    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte *pBytes = env->GetByteArrayElements(stringJbytes, NULL);

    std::string ret = std::string((char *) pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);

    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

}

// ######################### VISAGE ###############################

// neccessary prototype declaration for licensing
namespace VisageSDK {
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


// ############################# CAMERA #############################

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


void callVoidMethod(std::string method) {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz{env->GetObjectClass(activity)};

    // find the identifier of the method to call
    jmethodID method_id = env->GetMethodID(clazz, method.c_str(), "()V");

    // effectively call the Java method
    env->CallVoidMethod(activity, method_id);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);
}

void bindingStartCamera() {
    callVoidMethod("StartCamera");
}


void bindingStopCamera() {
    callVoidMethod("StopCamera");
}


// ############################# ADS #############################

void bindingLoadInterstitialAd() {
    callVoidMethod("AdsInterstitialLoad");
}


bool bindingIsLoadedIntestitialAd() {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz(env->GetObjectClass(activity));

    // find the identifier of the method to call
    jmethodID method_id = env->GetMethodID(clazz, "AdsIsInterstitialLoaded", "()Z");

    // effectively call the Java method
    auto ret = env->CallBooleanMethod(activity, method_id);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);

    return static_cast<bool>(ret);
}


void bindingShowInterstitialAd() {
    callVoidMethod("AdsInterstitialShow");
}


void bindingLoadBannerAd() {
    // we load the banner when show is called in the java code
}


bool bindingIsLoadedBannerAd() {
    return true;
}


void bindingShowBannerAd() {
    callVoidMethod("AdsBannerShow");
}


void bindingHideBannerAd() {
    callVoidMethod("AdsBannerHide");
}


// ############################# VIDEO CAPTURE #########################

static std::string videoOutputPath;


std::string bindingVideoOutputPath() {
    // I guess we can live without synchronization here
    return videoOutputPath;
}


void bindingVideoExport() {
    callVoidMethod("galleryAddVideo");
}


void bindingMuxAudioVideo() {
    callVoidMethod("muxAudioVideo");
}

// ############################# PERMISSIONS #########################

std::mutex permissionsMtx;
std::queue<std::pair<int, int>> permissionsQ;


void enqueuePermissionResult(int permission, int result) {
    std::lock_guard l{permissionsMtx};
    permissionsQ.push({permission, result});
}


bool dequeuePermissionResult(std::pair<int, int>& p) {
    std::lock_guard l{permissionsMtx};
    auto ret = !permissionsQ.empty();
    if(ret) {
        p = std::move(permissionsQ.front());
        permissionsQ.pop();
    }

    return ret;
}


int checkPermissionStatus(int permission) {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz{env->GetObjectClass(activity)};

    // invoke the method
    jmethodID myMethod = env->GetMethodID(clazz, "CheckPermissionStatus", "(I)I");
    auto ret = env->CallIntMethod(activity, myMethod, permission);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);

    return ret;
}


void requestPermission(int permission) {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz{env->GetObjectClass(activity)};

    // invoke the method
    jmethodID myMethod = env->GetMethodID(clazz, "RequestPermission", "(I)V");
    env->CallVoidMethod(activity, myMethod, permission);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);
}


// ############################# OSS LICENSES #########################

void showOssLicenses() {
    callVoidMethod("showOssLicenses");
}


// ############################# BILLINGS #########################

std::mutex purchasessMtx;
std::queue<std::pair<int, int>> purchasesQ;


void enqueuePurchaseUpdates(int permission, int result) {
    std::lock_guard l{purchasessMtx};
    purchasesQ.push({permission, result});
}


bool dequeuePurchaseUpdates(std::pair<int, int>& p) {
    std::lock_guard l{purchasessMtx};
    auto ret = !purchasesQ.empty();
    if(ret) {
        p = std::move(purchasesQ.front());
        purchasesQ.pop();
    }

    return ret;
}


void initiatePurchaseFlow(int productId) {
    // retrieve the JNI environment.
    JNIEnv* env = (JNIEnv*)SDL_AndroidGetJNIEnv();

    // retrieve the Java instance of the SDLActivity
    jobject activity = (jobject)SDL_AndroidGetActivity();

    // find the Java class of the activity. It should be SDLActivity or a subclass of it.
    jclass clazz{env->GetObjectClass(activity)};

    // invoke the method
    jmethodID myMethod = env->GetMethodID(clazz, "initiatePurchaseFlow", "(I)V");
    env->CallVoidMethod(activity, myMethod, productId);

    // clean up the local references.
    env->DeleteLocalRef(activity);
    env->DeleteLocalRef(clazz);
}


void queryPurchases()  {
    callVoidMethod("queryPurchases");
}


} // namespace gamee


extern "C" {

void Java_com_gamee_facesmash_FaceSmashActivity_WriteFrameCamera(JNIEnv* env, jobject obj, jbyteArray frame) {
    jbyte* data = env->GetByteArrayElements(frame, 0);

    if(gamee::cameraAndroidReady) {
        auto &camera = static_cast<gamee::CameraAndroid &>(gamee::Locator::Camera::ref());
        camera.setPixels(data);
    }

    env->ReleaseByteArrayElements(frame, data, 0);
}


void Java_com_gamee_facesmash_FaceSmashActivity_WriteCameraParams(JNIEnv* env, jobject obj, jint width, jint height) {
    if(gamee::bindingReady) return;
    std::unique_lock lck{gamee::bindingMtx};
    gamee::resolution = {width, height};
    gamee::bindingReady = true;
    lck.unlock();
    gamee::bindingCv.notify_all();
}


void Java_com_gamee_facesmash_FaceSmashActivity_InitVisage(JNIEnv* env, jobject obj) {
    auto path = std::string{SDL_AndroidGetInternalStoragePath()} + "/visage/504-932-294-611-606-835-011-303-246-311-003.vlc";
    VisageSDK::initializeLicenseManager(env, obj, path.c_str(), AlertCallback);
}


void Java_com_gamee_facesmash_FaceSmashActivity_WriteVideoOutputPath(JNIEnv* env, jobject obj, jstring path) {
    gamee::videoOutputPath = jstring2string(env, path);
}


void Java_com_gamee_facesmash_FaceSmashActivity_EnqueuePermissionResult(JNIEnv* env, jobject obj, jint permission, jint result) {
    gamee::enqueuePermissionResult(permission, result);
}


void Java_com_gamee_facesmash_FaceSmashActivity_purchaseUpdated(JNIEnv *env, jobject instance, jint product, jint result) {
    gamee::enqueuePurchaseUpdates(product, result);
}


} // extern "C"extern "C"