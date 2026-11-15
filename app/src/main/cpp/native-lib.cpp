#include <jni.h>
#include <string>
#include <cmath>

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_viu_shield_NativeGuard_status(
    JNIEnv* env,
    jobject
) {
    return env->NewStringUTF("ViU Native Engine Online");
}

JNIEXPORT jstring JNICALL
Java_com_viu_shield_NativeGuard_version(
    JNIEnv* env,
    jobject
) {
    return env->NewStringUTF("ViU Engine 1.0");
}

JNIEXPORT jdouble JNICALL
Java_com_viu_shield_NativeGuard_execute(
    JNIEnv* env,
    jobject,
    jint operation,
    jdouble a,
    jdouble b
) {
    switch (operation) {
        case 0: return a + b;
        case 1: return a - b;
        case 2: return a * b;
        case 3: return b != 0 ? a / b : NAN;
        case 4: return std::pow(a, b);
        default: return NAN;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_viu_shield_NativeGuard_selfTest(
    JNIEnv* env,
    jobject
) {
    return (2 + 3 == 5) ? JNI_TRUE : JNI_FALSE;
}

}
