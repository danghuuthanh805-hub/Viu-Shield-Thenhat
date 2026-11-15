
#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_viu_shield_NativeGuard_status(
    JNIEnv* env,
    jobject
) {
    std::string result = "ViU Native Engine Online";
    return env->NewStringUTF(result.c_str());
}
