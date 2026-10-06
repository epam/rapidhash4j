#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output="${1:-build/native-binaries}"
targets="$(awk 'NF && $1 !~ /^#/ { print }' native/targets.tsv)"
test -n "$targets"

while read -r platform target library extra; do
    if [[ -z "$library" || -n "$extra" ]]; then
        echo "Invalid native target: $platform $target $library $extra" >&2
        exit 1
    fi
    case "$target" in
        x86_64-linux*) pattern='^ELF 64-bit LSB shared object, x86-64' ;;
        aarch64-linux*) pattern='^ELF 64-bit LSB shared object, ARM aarch64' ;;
        x86_64-macos*) pattern='^Mach-O 64-bit .*x86_64' ;;
        aarch64-macos*) pattern='^Mach-O 64-bit .*arm64' ;;
        x86_64-windows*) pattern='^PE32\+ .*x86-64' ;;
        aarch64-windows*) pattern='^PE32\+ .*Aarch64' ;;
        *) echo "Unsupported binary format check for target: $target" >&2; exit 1 ;;
    esac
    path="$platform/$library"
    binary="$output/$path"
    if [[ ! -s "$binary" ]]; then
        echo "Missing or empty native binary: $binary" >&2
        exit 1
    fi
    description="$(file -b "$binary")"
    echo "$path: $description"
    if [[ ! "$description" =~ $pattern ]]; then
        echo "Unexpected binary format or architecture: $binary" >&2
        exit 1
    fi
    [[ "$target" == *-linux* ]] || continue
    # Run on Linux with binutils (installed by CI).
    versions="$(readelf --version-info --wide "$binary")"
    requirements="$(printf '%s\n' "$versions" | sed -n 's/.*Name: GLIBC_\([^ ]*\).*/\1/p')"
    case "$target" in
        *-gnu.*) maximum="${target##*-gnu.}" ;;
        *-musl*)
            if [[ -n "$requirements" ]]; then
                echo "$platform is a musl target but requires glibc" >&2
                exit 1
            fi
            continue ;;
        *) echo "Linux GNU targets must pin a glibc version: $target" >&2; exit 1 ;;
    esac
    while read -r version; do
        [[ -n "$version" ]] || continue
        if [[ ! "$version" =~ ^[0-9]+(\.[0-9]+)+$ ]] ||
                [[ "$(printf '%s\n' "$version" "$maximum" | sort -V | tail -n 1)" != "$maximum" ]]; then
            echo "$platform requires GLIBC_$version, exceeding $maximum" >&2
            exit 1
        fi
    done <<< "$requirements"
    echo "$platform: glibc requirements fit $maximum"
done <<< "$targets"
