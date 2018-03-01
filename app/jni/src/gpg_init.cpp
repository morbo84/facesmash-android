#include <jni.h>
#include <gpg/android_initialization.h>
#include <gpg/android_platform_configuration.h>
#include <gpg/android_support.h>


jint JNI_OnLoad(JavaVM* vm, void* reserved)  {
    gpg::AndroidInitialization::JNI_OnLoad(vm);

    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}


extern "C" {

void Java_com_gamee_facesmash_FaceSmashActivity_nativeOnActivityResult(
        JNIEnv *env,
        jobject thiz,
        jobject activity,
        jint request_code,
        jint result_code,
        jobject data) {
    gpg::AndroidSupport::OnActivityResult(env, activity, request_code, result_code, data);
}

} // extern "C"