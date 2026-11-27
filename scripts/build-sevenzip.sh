#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD/app/build/generated/sevenzip"
src="$root/source"
pin=d3296e39e1f1f92c7b41c0c3848e65640428d42d
mkdir -p "$root"
if [[ ! -d "$src/.git" ]]; then
    git clone --depth 1 --branch 26.06 https://github.com/ip7z/7zip.git "$src"
fi
test "$(git -C "$src" rev-parse HEAD)" = "$pin"
# Android provides pthread symbols in libc. Preserve the upstream source revision
# and apply these reproducible build-only adjustments.
sed -i 's/-Werror//g; s/-lpthread//g' "$src/CPP/7zip/7zip_gcc.mak"
ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?}/ndk/27.2.12479018}"
if [[ ! -d "$ndk/toolchains/llvm/prebuilt" ]]; then ndk="$ANDROID_HOME/ndk/27.2.12479018"; fi
toolchain="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
bundle="$src/CPP/7zip/Bundles/Alone2"
for pair in arm64-v8a:aarch64-linux-android armeabi-v7a:armv7a-linux-androideabi x86:i686-linux-android x86_64:x86_64-linux-android; do
    abi="${pair%%:*}"; target="${pair#*:}"
    out="$root/jniLibs/$abi"
    mkdir -p "$out"
    make -C "$bundle" -f makefile.gcc -j"$(nproc)" O="_slate_$abi" \
        CC="$toolchain/${target}26-clang" CXX="$toolchain/${target}26-clang++" \
        DISABLE_RAR=1 MY_DYNAMIC_LINK=1 MY_LIBS="-static-libstdc++ -Wl,-z,max-page-size=16384"
    install -m 755 "$bundle/_slate_$abi/7zz" "$out/libslate7zz.so"
    "$toolchain/llvm-strip" --strip-unneeded "$out/libslate7zz.so"
    "$toolchain/llvm-readelf" -h "$out/libslate7zz.so" | grep -q 'DYN'
done
mkdir -p "$root/assets/about/sevenzip"
cp "$src/DOC/License.txt" "$root/assets/about/sevenzip/"
find "$src/DOC" -maxdepth 1 -iname '*lgpl*' -exec cp {} "$root/assets/about/sevenzip/" \;
