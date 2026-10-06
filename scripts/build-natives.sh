#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

: "${JAVA_HOME:?Set JAVA_HOME to a JDK containing include/jni.h}"
test -f "$JAVA_HOME/include/jni.h"
zig="${ZIG:-zig}"
output="${1:-build/native-binaries}"
shopt -s nullglob
sources=(native/com_epam_deltix_rapidhash4j_*.c)
if [[ ${#sources[@]} -eq 0 ]]; then
    echo "No JNI C sources found in native/" >&2
    exit 1
fi
targets="$(awk 'NF && $1 !~ /^#/ { print }' native/targets.tsv)"
test -n "$targets"
while read -r platform target library extra; do
    if [[ -z "$library" || -n "$extra" ]]; then
        echo "Invalid native target: $platform $target $library $extra" >&2
        exit 1
    fi
    shared_flag=-shared
    [[ "$target" != *-macos* ]] || shared_flag=-dynamiclib
    echo "Building $platform ($target)"
    mkdir -p "$output/$platform"
    "$zig" cc -target "$target" -mcpu=baseline "$shared_flag" -fPIC -O3 -s \
        -DNDEBUG -DRAPIDHASH_UNROLLED -fvisibility=hidden \
        -Wall -Wextra -Werror -Wno-unused-parameter \
        -I scripts/include -I "$JAVA_HOME/include" -I native/rapidhash \
        "${sources[@]}" \
        -o "$output/$platform/$library"
done <<< "$targets"
