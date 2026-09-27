// MIT License - see LICENSE file.
#include <jni.h>
#include <android/log.h>
#define LOG_TAG "ShizukuShellNative"
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*, void*) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "libshizuku_shell_local.so carregada");
    return JNI_VERSION_1_6;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_arena_shizuku_1shell_1local_NativeMarker_nativeLibraryName(JNIEnv* env, jclass) {
    return env->NewStringUTF("libshizuku_shell_local.so");
}
