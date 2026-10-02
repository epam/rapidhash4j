#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifndef RAPIDHASH_UNROLLED
#define RAPIDHASH_UNROLLED
#endif
#include "rapidhash.h"

/* Mirrors upstream V3 rapidhash_internal. See README.md for compatibility
 * vectors and the upstream update procedure before changing the algorithm. */
#define CHUNK_SIZE 112
#define CHUNK_PREV 16

typedef struct {
    uint64_t seeds[7];
    size_t   buffer_len;
    int      processed;
} chunk_state;

typedef struct {
    uint64_t    seed;
    chunk_state state;
    uint8_t     buffer[CHUNK_PREV + CHUNK_SIZE];
} rapidhash_stream;

static inline void chunk_state_init(chunk_state *cs, uint64_t seed) {
    for (int i = 0; i < 7; i++) cs->seeds[i] = seed;
    cs->buffer_len = 0;
    cs->processed = 0;
}

static inline void chunk_write(chunk_state *cs, const uint64_t *secrets, const uint8_t *chunk) {
    cs->seeds[0] = rapid_mix(rapid_read64(chunk)      ^ secrets[0], rapid_read64(chunk + 8)   ^ cs->seeds[0]);
    cs->seeds[1] = rapid_mix(rapid_read64(chunk + 16) ^ secrets[1], rapid_read64(chunk + 24)  ^ cs->seeds[1]);
    cs->seeds[2] = rapid_mix(rapid_read64(chunk + 32) ^ secrets[2], rapid_read64(chunk + 40)  ^ cs->seeds[2]);
    cs->seeds[3] = rapid_mix(rapid_read64(chunk + 48) ^ secrets[3], rapid_read64(chunk + 56)  ^ cs->seeds[3]);
    cs->seeds[4] = rapid_mix(rapid_read64(chunk + 64) ^ secrets[4], rapid_read64(chunk + 72)  ^ cs->seeds[4]);
    cs->seeds[5] = rapid_mix(rapid_read64(chunk + 80) ^ secrets[5], rapid_read64(chunk + 88)  ^ cs->seeds[5]);
    cs->seeds[6] = rapid_mix(rapid_read64(chunk + 96) ^ secrets[6], rapid_read64(chunk + 104) ^ cs->seeds[6]);
    cs->processed = 1;
}

/* Slow path: buffer overflow, need to process blocks. */
static void write_inner(rapidhash_stream *s, const uint8_t *data, size_t len) {
    uint8_t *chunk_prev = s->buffer;
    uint8_t *chunk_curr = s->buffer + CHUNK_PREV;

    /* Fill current buffer to CHUNK_SIZE and process it. */
    size_t copy_bytes = CHUNK_SIZE - s->state.buffer_len;
    memcpy(chunk_curr + s->state.buffer_len, data, copy_bytes);
    chunk_write(&s->state, rapid_secret, chunk_curr);

    const uint8_t *remaining = data + copy_bytes;
    size_t remaining_len = len - copy_bytes;

    /* Process full chunks directly from input, leaving at least 1 byte for the buffer. */
    size_t stop = remaining_len > 0 ? ((remaining_len - 1) / CHUNK_SIZE) * CHUNK_SIZE : 0;
    const uint8_t *chunk_last = NULL;
    size_t pos = 0;

    while (pos < stop) {
        chunk_last = remaining + pos;
        chunk_write(&s->state, rapid_secret, chunk_last);
        pos += CHUNK_SIZE;
    }

    /* Save lookback: last 16 bytes of the most recently processed chunk. */
    if (chunk_last != NULL) {
        memcpy(chunk_prev, chunk_last + CHUNK_SIZE - CHUNK_PREV, CHUNK_PREV);
    } else {
        memcpy(chunk_prev, chunk_curr + CHUNK_SIZE - CHUNK_PREV, CHUNK_PREV);
    }

    /* Buffer the unprocessed remainder. */
    size_t unprocessed_len = remaining_len - pos;
    memcpy(chunk_curr, remaining + pos, unprocessed_len);
    s->state.buffer_len = unprocessed_len;
}

static void stream_write(rapidhash_stream *s, const uint8_t *data, size_t len) {
    /* Fast path: fits in buffer without overflow. */
    if (s->state.buffer_len + len <= CHUNK_SIZE) {
        memcpy(s->buffer + CHUNK_PREV + s->state.buffer_len, data, len);
        s->state.buffer_len += len;
        return;
    }
    write_inner(s, data, len);
}

static uint64_t stream_finish(const rapidhash_stream *s) {
    uint64_t seed = s->seed;
    uint64_t a, b;
    uint64_t remainder;

    if (!s->state.processed && s->state.buffer_len <= 16) {
        /* Short path: 0-16 bytes, no blocks processed. Mirrors rapidhash_internal short path. */
        const uint8_t *data = s->buffer + CHUNK_PREV;
        size_t len = s->state.buffer_len;

        if (len >= 4) {
            seed ^= (uint64_t)len;
            if (len >= 8) {
                a = rapid_read64(data);
                b = rapid_read64(data + len - 8);
            } else {
                a = (uint64_t)rapid_read32(data);
                b = (uint64_t)rapid_read32(data + len - 4);
            }
        } else if (len > 0) {
            a = ((uint64_t)data[0] << 45) | (uint64_t)data[len - 1];
            b = (uint64_t)data[len >> 1];
        } else {
            a = 0;
            b = 0;
        }
        remainder = (uint64_t)len;
    } else {
        /* Medium/long path. */
        if (s->state.processed) {
            seed = s->state.seeds[0] ^ s->state.seeds[1] ^ s->state.seeds[2]
                 ^ s->state.seeds[3] ^ s->state.seeds[4] ^ s->state.seeds[5]
                 ^ s->state.seeds[6];
        }

        const uint8_t *slice = s->buffer + CHUNK_PREV;
        size_t slice_len = s->state.buffer_len;

        /* Tail processing: cascading mix for 16-byte pairs. */
        if (slice_len > 16) {
            seed = rapid_mix(rapid_read64(slice) ^ rapid_secret[2], rapid_read64(slice + 8) ^ seed);
            if (slice_len > 32) {
                seed = rapid_mix(rapid_read64(slice + 16) ^ rapid_secret[2], rapid_read64(slice + 24) ^ seed);
                if (slice_len > 48) {
                    seed = rapid_mix(rapid_read64(slice + 32) ^ rapid_secret[1], rapid_read64(slice + 40) ^ seed);
                    if (slice_len > 64) {
                        seed = rapid_mix(rapid_read64(slice + 48) ^ rapid_secret[1], rapid_read64(slice + 56) ^ seed);
                        if (slice_len > 80) {
                            seed = rapid_mix(rapid_read64(slice + 64) ^ rapid_secret[2], rapid_read64(slice + 72) ^ seed);
                            if (slice_len > 96) {
                                seed = rapid_mix(rapid_read64(slice + 80) ^ rapid_secret[1], rapid_read64(slice + 88) ^ seed);
                            }
                        }
                    }
                }
            }
        }

        /* Read last 16 bytes — may reach into lookback area (buffer[0..15]). */
        const uint8_t *full_buf = s->buffer;
        size_t full_len = CHUNK_PREV + slice_len;
        a = rapid_read64(full_buf + full_len - 16) ^ (uint64_t)slice_len;
        b = rapid_read64(full_buf + full_len - 8);
        remainder = (uint64_t)slice_len;
    }

    a ^= rapid_secret[1];
    b ^= seed;
    rapid_mum(&a, &b);
    return rapid_mix(a ^ rapid_secret[7], b ^ rapid_secret[1] ^ remainder);
}

static void stream_init(rapidhash_stream *s, uint64_t raw_seed) {
    s->seed = raw_seed ^ rapid_mix(raw_seed ^ rapid_secret[2], rapid_secret[1]);
    chunk_state_init(&s->state, s->seed);
    memset(s->buffer, 0, sizeof(s->buffer));
}

/* --- JNI bindings --- */

JNIEXPORT jlong JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamInit(
    JNIEnv *env, jclass cls, jlong seed)
{
    (void)env; (void)cls;
    rapidhash_stream *s = (rapidhash_stream *)malloc(sizeof(rapidhash_stream));
    if (s == NULL) return 0;
    stream_init(s, (uint64_t)seed);
    return (jlong)(uintptr_t)s;
}

JNIEXPORT void JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamUpdate(
    JNIEnv *env, jclass cls, jlong state, jbyteArray data, jint offset, jint length)
{
    (void)cls;
    rapidhash_stream *s = (rapidhash_stream *)(uintptr_t)state;
    jbyte *ptr = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    if (ptr == NULL) return;
    stream_write(s, (const uint8_t *)ptr + offset, (size_t)length);
    (*env)->ReleasePrimitiveArrayCritical(env, data, ptr, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamUpdateDirect(
    JNIEnv *env, jclass cls, jlong state, jobject buffer, jint offset, jint length)
{
    (void)cls;
    rapidhash_stream *s = (rapidhash_stream *)(uintptr_t)state;
    void *addr = (*env)->GetDirectBufferAddress(env, buffer);
    if (addr == NULL) return;
    stream_write(s, (const uint8_t *)addr + offset, (size_t)length);
}

JNIEXPORT jlong JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamFinish(
    JNIEnv *env, jclass cls, jlong state)
{
    (void)env; (void)cls;
    rapidhash_stream *s = (rapidhash_stream *)(uintptr_t)state;
    uint64_t result = stream_finish(s);
    chunk_state_init(&s->state, s->seed);
    memset(s->buffer, 0, sizeof(s->buffer));
    return (jlong)result;
}

JNIEXPORT void JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamReset(
    JNIEnv *env, jclass cls, jlong state, jlong seed)
{
    (void)env; (void)cls;
    rapidhash_stream *s = (rapidhash_stream *)(uintptr_t)state;
    stream_init(s, (uint64_t)seed);
}

JNIEXPORT void JNICALL Java_com_epam_deltix_rapidhash4j_RapidHashStream_nativeStreamFree(
    JNIEnv *env, jclass cls, jlong state)
{
    (void)env; (void)cls;
    free((void *)(uintptr_t)state);
}
