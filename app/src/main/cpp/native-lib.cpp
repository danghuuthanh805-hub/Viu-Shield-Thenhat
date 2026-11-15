
#include <jni.h>
#include <string>
#include <cmath>
#include <cstring>

static const char* ENGINE_VERSION = "1.0.0";

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
    return env->NewStringUTF(ENGINE_VERSION);
}

JNIEXPORT jboolean JNICALL
Java_com_viu_shield_NativeGuard_selfTest(
    JNIEnv* env,
    jobject
) {
    double result = 10.0 + 20.0;

    if (result != 30.0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
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
        case 3:
            return b == 0 ? NAN : a / b;
        case 4:
            return std::pow(a, b);
        default:
            return NAN;
    }
}

}
