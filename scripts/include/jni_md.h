#ifndef RAPIDHASH4J_JNI_MD_H
#define RAPIDHASH4J_JNI_MD_H
#include <stdint.h>
#if defined(_WIN32)
#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall
#else
#define JNIEXPORT __attribute__((visibility("default")))
#define JNIIMPORT __attribute__((visibility("default")))
#define JNICALL
#endif
typedef int32_t jint;
typedef int64_t jlong;
typedef int8_t jbyte;
#endif
