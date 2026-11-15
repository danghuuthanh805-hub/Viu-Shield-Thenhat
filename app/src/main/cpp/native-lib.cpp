
#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_viu_shield_NativeGuard_status(
    JNIEnv* env,
    jobject
) {
    std::string status = "ViU Native Engine Online";
    return env->NewStringUTF(status.c_str());
}
