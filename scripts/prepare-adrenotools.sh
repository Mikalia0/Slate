#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD/app/build/generated/adrenotools"
src="$root/source"
pin=8483dfdaa2abf97ee89ad0e5f337e7b508550c6b
mkdir -p "$root"
if [[ ! -d "$src/.git" ]]; then
    git init "$src"
    git -C "$src" remote add origin https://github.com/Pipetto-crypto/libadrenotools.git
    git -C "$src" fetch --depth 1 origin "$pin"
    git -C "$src" checkout --detach FETCH_HEAD
fi
test "$(git -C "$src" rev-parse HEAD)" = "$pin"
git -C "$src" submodule update --init --recursive --depth 1
test "$(git -C "$src/lib/linkernsbypass" rev-parse HEAD)" = b10d48548d608dfca38ffc449d0350335b2f3737
# Record successful custom ICD loading in this dedicated probe process. Upstream
# can otherwise fall back to the system ICD without notifying the caller.
python3 - "$src/src/hook/hook_impl.cpp" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1])
s=p.read_text()
marker='setenv("SLATE_CUSTOM_DRIVER_LOADED", "1", 1);'
if marker not in s:
    anchor='        return handle;'
    assert s.count(anchor)==1
    s='#include <stdlib.h>\n'+s.replace(anchor,'        '+marker+'\n'+anchor)
    p.write_text(s)
PY
mkdir -p "$root/assets/about/adrenotools"
cp "$src/LICENSE" "$root/assets/about/adrenotools/LICENSE-adrenotools.txt"
cp "$src/lib/linkernsbypass/LICENSE" "$root/assets/about/adrenotools/LICENSE-linkernsbypass.txt"
