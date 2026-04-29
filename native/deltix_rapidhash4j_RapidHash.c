#include <jni.h>
#include <stdint.h>

#ifndef RAPIDHASH_UNROLLED
#define RAPIDHASH_UNROLLED
#endif
#include "rapidhash.h"

JNIEXPORT jlong JNICALL Java_deltix_rapidhash4j_RapidHash_nativeHash(
    JNIEnv *env, jclass cls, jbyteArray data, jint offset, jint length, jlong seed)
{
    (void)cls;

    jbyte *ptr = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (ptr == NULL) return 0;

    uint64_t result = rapidhash_withSeed(
        (const void *)((const char *)ptr + offset),
        (size_t)length,
        (uint64_t)seed
    );

    (*env)->ReleasePrimitiveArrayCritical(env, data, ptr, JNI_ABORT);

    return (jlong)result;
}

JNIEXPORT jlong JNICALL Java_deltix_rapidhash4j_RapidHash_nativeHashDirect(
    JNIEnv *env, jclass cls, jobject buffer, jint offset, jint length, jlong seed)
{
    (void)cls;

    void *addr = (*env)->GetDirectBufferAddress(env, buffer);
    if (addr == NULL) return 0;

    return (jlong)rapidhash_withSeed(
        (const char *)addr + offset,
        (size_t)length,
        (uint64_t)seed
    );
}
