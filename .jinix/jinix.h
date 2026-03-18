#include <jni.h>

#ifndef _Jinix_Headers
#define _Jinix_Headers

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_org_jinix_Jinix_init(JNIEnv *);
JNIEXPORT jint JNICALL Java_org_jinix_Main_testNative(JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif

#endif
