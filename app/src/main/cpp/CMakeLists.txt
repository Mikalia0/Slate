cmake_minimum_required(VERSION 3.22.1)
project(slate_gpu C CXX)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
add_library(gpuusage SHARED gpuusage/kgsl_busy.c)
set_property(TARGET gpuusage PROPERTY C_STANDARD 11)
target_link_libraries(gpuusage log)

# Optional for local development; release CI always prepares the pinned source.
set(SEVENZIP_C "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/sevenzip/source/C")
if(EXISTS "${SEVENZIP_C}/7z.h")
    set(SDK_FILES Bcj2 Bra Bra86 BraIA64 CpuArch Delta Lzma2Dec LzmaDec Ppmd7 Ppmd7Dec 7zCrc 7zCrcOpt 7zAlloc 7zArcIn 7zBuf 7zBuf2 7zDec 7zFile 7zStream)
    set(SDK_SOURCES "")
    foreach(name IN LISTS SDK_FILES)
        list(APPEND SDK_SOURCES "${SEVENZIP_C}/${name}.c")
    endforeach()
    add_library(slate7z SHARED archive/sevenzip_jni.c ${SDK_SOURCES})
    target_include_directories(slate7z PRIVATE "${SEVENZIP_C}")
    target_compile_definitions(slate7z PRIVATE Z7_PPMD_SUPPORT Z7_EXTRACT_ONLY _FILE_OFFSET_BITS=64)
    target_compile_options(slate7z PRIVATE -O2 -fvisibility=hidden)
    target_link_options(slate7z PRIVATE "-Wl,-z,max-page-size=16384")
    set_property(TARGET slate7z PROPERTY C_STANDARD 11)
endif()

add_library(slatefs SHARED storage/directory.c)
target_compile_options(slatefs PRIVATE -O2 -fvisibility=hidden)
target_link_options(slatefs PRIVATE "-Wl,-z,max-page-size=16384")

# Vulkan diagnostics are available on all ABIs. The Ludashi AdrenoTools fork
# supports ARM64; do not gate custom driver selection on a GPU vendor ID.
set(ADRENO_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/generated/adrenotools/source")
find_program(SLATE_GLSLANG glslangValidator REQUIRED)
foreach(stage vert frag)
    add_custom_command(OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/labs_cube_${stage}.h"
        COMMAND "${SLATE_GLSLANG}" -V --vn labs_cube_${stage}_code -x
            -o "${CMAKE_CURRENT_BINARY_DIR}/labs_cube_${stage}.h"
            "${CMAKE_CURRENT_SOURCE_DIR}/vulkan/labs_cube.${stage}"
        DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/vulkan/labs_cube.${stage}" VERBATIM)
endforeach()
add_library(slatevulkan SHARED vulkan/probe.c
    "${CMAKE_CURRENT_BINARY_DIR}/labs_cube_vert.h"
    "${CMAKE_CURRENT_BINARY_DIR}/labs_cube_frag.h")
target_include_directories(slatevulkan PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(slatevulkan dl log m android)
target_link_options(slatevulkan PRIVATE "-Wl,-z,max-page-size=16384")
set_property(TARGET slatevulkan PROPERTY C_STANDARD 11)
if(ANDROID_ABI STREQUAL "arm64-v8a" AND EXISTS "${ADRENO_SOURCE}/CMakeLists.txt")
    add_subdirectory("${ADRENO_SOURCE}" "${CMAKE_CURRENT_BINARY_DIR}/adrenotools")
    target_compile_definitions(slatevulkan PRIVATE SLATE_ADRENOTOOLS)
    target_link_libraries(slatevulkan adrenotools)
    add_dependencies(slatevulkan main_hook hook_impl file_redirect_hook gsl_alloc_hook)
    foreach(target main_hook hook_impl file_redirect_hook gsl_alloc_hook)
        target_link_options(${target} PRIVATE "-Wl,-z,max-page-size=16384")
    endforeach()
endif()
