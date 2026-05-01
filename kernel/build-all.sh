#!/bin/bash
set -e

mkdir -p output

KMIS="android12-5.10 android13-5.10 android13-5.15 android14-5.15 android14-6.1 android15-6.6 android16-6.12"

cd ..

GIT_FIX_PATH="$(pwd)/.tmp_gitconfig"

cat <<EOF > GIT_FIX_PATH
[safe]
    directory = /build
EOF

for kmi in $KMIS; do
    echo "========== Building $kmi =========="
    export DDK_TARGET=$kmi
    if ddk build \
        -e GIT_CONFIG_GLOBAL=/build/.tmp_gitconfig \
        -e CONFIG_KSU=m \
        -e CONFIG_KSU_TRACEPOINT_HOOK=y \
        -e CONFIG_KSU_MULTI_MANAGER_SUPPORT=y \
        -- -C kernel; then
        cd kernel/
        if [ -f kernelsu.ko ]; then
            cp kernelsu.ko "kernelsu-${kmi}.ko"
            llvm-objcopy --strip-unneeded "kernelsu-${kmi}.ko"
            echo "✓ Built kernelsu-${kmi}.ko"
        fi
        cd ..
    else
        echo "✗ Build failed for $kmi"
    fi
    echo ""
    unset DDK_TARGET
done

rm -f "$GIT_FIX_PATH"

cd kernel

echo "========== Final output =========="
ls -la
