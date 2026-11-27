// SPDX-License-Identifier: GPL-3.0-only
#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <pthread.h>
#include "7z.h"
#include "7zFile.h"
#include "7zCrc.h"

/* Bound total SDK allocations, including solid blocks and metadata. */
#define MAX_NATIVE_BYTES ((size_t)192 * 1024 * 1024)
typedef union { size_t size; max_align_t align; } AllocHeader;
typedef struct { ISzAlloc api; size_t used; } Budget;
static void *budget_alloc(ISzAllocPtr self, size_t n) {
    Budget *b = (Budget *)self;
    if (!n || n > MAX_NATIVE_BYTES - b->used || n > SIZE_MAX - sizeof(AllocHeader)) return NULL;
    AllocHeader *h = malloc(sizeof(*h) + n);
    if (!h) return NULL;
    h->size = n; b->used += n; return h + 1;
}
static void budget_free(ISzAllocPtr self, void *ptr) {
    if (ptr) { Budget *b = (Budget *)self; AllocHeader *h = (AllocHeader *)ptr - 1; b->used -= h->size; free(h); }
}
static pthread_once_t crc_once = PTHREAD_ONCE_INIT;
static void init_crc(void) { CrcGenerateTable(); }

/* Native code only reads an already opened fd. Kotlin owns every output path. */
JNIEXPORT jint JNICALL Java_com_slate_android_NativeSevenZip_extractNative(
    JNIEnv *env, jobject self, jint fd, jobject sink, jlong output_limit) {
    (void)self;
    jclass cls = (*env)->GetObjectClass(env, sink);
    if (!cls) return SZ_ERROR_FAIL;
    jmethodID begin = (*env)->GetMethodID(env, cls, "begin", "(Ljava/lang/String;ZJI)V");
    jmethodID write = (*env)->GetMethodID(env, cls, "write", "([BI)V");
    jmethodID finish = (*env)->GetMethodID(env, cls, "finish", "()V");
    if (!begin || !write || !finish) return SZ_ERROR_FAIL;
    CFileInStream stream;
    File_Construct(&stream.file);
    stream.file.fd = dup(fd);
    if (stream.file.fd < 0) return SZ_ERROR_READ;
    FileInStream_CreateVTable(&stream);
    CLookToRead2 look;
    LookToRead2_CreateVTable(&look, 0);
    Budget alloc = {{budget_alloc, budget_free}, 0};
    look.bufSize = 256 * 1024;
    look.buf = ISzAlloc_Alloc(&alloc.api, look.bufSize);
    look.realStream = &stream.vt;
    LookToRead2_INIT(&look)
    CSzArEx db; SzArEx_Init(&db);
    Byte *block = NULL; size_t block_size = 0; UInt32 block_index = (UInt32)-1;
    jint result = SZ_ERROR_MEM;
    if (!look.buf) goto done;
    pthread_once(&crc_once, init_crc);
    result = SzArEx_Open(&db, &look.vt, &alloc.api, &alloc.api);
    if (result) goto done;
    if (db.NumFiles > 100000) { result = SZ_ERROR_DATA; goto done; }
    UInt64 total = 0;
    for (UInt32 i = 0; i < db.NumFiles; i++) {
        UInt64 n = SzArEx_GetFileSize(&db, i);
        if (n > (UInt64)output_limit - total) { result = SZ_ERROR_DATA; goto done; }
        total += n;
    }
    /* Large solid blocks use the streaming Java fallback rather than exhausting RAM. */
    for (UInt32 i = 0; i < db.db.NumFolders; i++) {
        if (SzAr_GetFolderUnpackSize(&db.db, i) > 96ULL * 1024 * 1024) {
            result = SZ_ERROR_UNSUPPORTED; goto done;
        }
    }
    jbyteArray buffer = (*env)->NewByteArray(env, 256 * 1024);
    if (!buffer) { result = SZ_ERROR_MEM; goto done; }
    for (UInt32 i = 0; i < db.NumFiles; i++) {
        size_t len = SzArEx_GetFileNameUtf16(&db, i, NULL);
        if (!len || len > 32768) { result = SZ_ERROR_DATA; break; }
        UInt16 *name = ISzAlloc_Alloc(&alloc.api, len * sizeof(UInt16));
        if (!name) { result = SZ_ERROR_MEM; break; }
        SzArEx_GetFileNameUtf16(&db, i, name);
        jstring path = (*env)->NewString(env, (const jchar *)name, (jsize)(len - 1));
        ISzAlloc_Free(&alloc.api, name);
        if (!path) { result = SZ_ERROR_MEM; break; }
        int dir = SzArEx_IsDir(&db, i);
        UInt32 attrs = SzBitWithVals_Check(&db.Attribs, i) ? db.Attribs.Vals[i] : 0;
        (*env)->CallVoidMethod(env, sink, begin, path, (jboolean)dir, (jlong)SzArEx_GetFileSize(&db, i), (jint)attrs);
        (*env)->DeleteLocalRef(env, path);
        if ((*env)->ExceptionCheck(env)) { result = SZ_ERROR_PROGRESS; break; }
        if (!dir) {
            size_t offset = 0, size = 0;
            result = SzArEx_Extract(&db, &look.vt, i, &block_index, &block, &block_size, &offset, &size, &alloc.api, &alloc.api);
            if (result) break;
            if (offset > block_size || size > block_size - offset || size != SzArEx_GetFileSize(&db, i)) { result = SZ_ERROR_DATA; break; }
            for (size_t pos = 0; pos < size;) {
                jsize n = (jsize)((size - pos) > 256 * 1024 ? 256 * 1024 : size - pos);
                (*env)->SetByteArrayRegion(env, buffer, 0, n, (const jbyte *)(block + offset + pos));
                if ((*env)->ExceptionCheck(env)) { result = SZ_ERROR_PROGRESS; break; }
                (*env)->CallVoidMethod(env, sink, write, buffer, n);
                if ((*env)->ExceptionCheck(env)) { result = SZ_ERROR_PROGRESS; break; }
                pos += (size_t)n;
            }
            if (result) break;
        }
        (*env)->CallVoidMethod(env, sink, finish);
        if ((*env)->ExceptionCheck(env)) { result = SZ_ERROR_PROGRESS; break; }
    }
    (*env)->DeleteLocalRef(env, buffer);
done:
    ISzAlloc_Free(&alloc.api, block);
    SzArEx_Free(&db, &alloc.api);
    ISzAlloc_Free(&alloc.api, look.buf);
    File_Close(&stream.file);
    return result;
}
