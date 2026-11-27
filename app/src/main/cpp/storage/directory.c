// SPDX-License-Identifier: GPL-3.0-only
#define _GNU_SOURCE
#include <jni.h>
#include <dirent.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

static void le(unsigned char *out,uint64_t v,unsigned n){for(unsigned i=0;i<n;i++)out[i]=(unsigned char)(v>>(i*8));}
JNIEXPORT jbyteArray JNICALL Java_com_slate_android_storage_NativeDirectory_readNative(JNIEnv *env,jobject self,jbyteArray path){
    (void)self;
    jsize plen=(*env)->GetArrayLength(env,path);
    if(plen<=0||plen>32768)return NULL;
    char *name=malloc((size_t)plen+1);if(!name)return NULL;
    (*env)->GetByteArrayRegion(env,path,0,plen,(jbyte*)name);name[plen]=0;
    if((*env)->ExceptionCheck(env)||memchr(name,0,(size_t)plen)){free(name);return NULL;}
    int fd=open(name,O_RDONLY|O_DIRECTORY|O_CLOEXEC|O_NOFOLLOW);free(name);
    if(fd<0)return NULL;
    DIR *dir=fdopendir(fd);if(!dir){close(fd);return NULL;}
    size_t cap=16384,used=0;unsigned char *data=malloc(cap);int ok=data!=NULL;
    while(ok){
        errno=0;struct dirent *e=readdir(dir);
        if(!e){if(errno)ok=0;break;}
        if(!strcmp(e->d_name,".")||!strcmp(e->d_name,".."))continue;
        size_t n=strlen(e->d_name),need=21+n;
        if(n>4096||used+need>32u*1024*1024){ok=0;break;}
        if(used+need>cap){size_t next=cap*2;unsigned char *p=realloc(data,next);if(!p){ok=0;break;}data=p;cap=next;}
        struct stat st;int kind=0;uint64_t size=0,modified=0;
        if(fstatat(fd,e->d_name,&st,AT_SYMLINK_NOFOLLOW)==0){
            kind=S_ISDIR(st.st_mode)?1:S_ISREG(st.st_mode)?2:S_ISLNK(st.st_mode)?3:4;
            if(st.st_size>0)size=(uint64_t)st.st_size;
            if(st.st_mtim.tv_sec>=0)modified=(uint64_t)st.st_mtim.tv_sec*1000+st.st_mtim.tv_nsec/1000000;
        }
        le(data+used,n,4);data[used+4]=(unsigned char)kind;
        le(data+used+5,size,8);le(data+used+13,modified,8);
        memcpy(data+used+21,e->d_name,n);used+=need;
    }
    closedir(dir);
    jbyteArray result=NULL;
    if(ok){result=(*env)->NewByteArray(env,(jsize)used);if(result&&used)(*env)->SetByteArrayRegion(env,result,0,(jsize)used,(jbyte*)data);}
    free(data);return result;
}
