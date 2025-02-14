#!/bin/bash

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <target>"
    echo "Available targets:"
    echo "  x86_64-unknown-linux-gnu"
    echo "  aarch64-unknown-linux-gnu"
    echo "  x86_64-apple-darwin"
    echo "  aarch64-apple-darwin"
    echo "  x86_64-pc-windows-gnu"
    echo "  aarch64-pc-windows-msvc"
    exit 1
fi

TARGET="$1"

cargo install cross --git https://github.com/cross-rs/cross

case "$TARGET" in
    aarch64-apple-darwin | x86_64-apple-darwin | aarch64-pc-windows-msvc)
        git clone https://github.com/cross-rs/cross
        cd cross
        git submodule update --init --remote
        cargo xtask configure-crosstool

        case "$TARGET" in
            aarch64-apple-darwin)
                cargo build-docker-image aarch64-apple-darwin-cross --build-arg 'MACOS_SDK_URL=https://github.com/joseluisq/macosx-sdks/releases/download/12.3/MacOSX12.3.sdk.tar.xz' --tag local
                ;;
            x86_64-apple-darwin)
                cargo build-docker-image x86_64-apple-darwin-cross --build-arg 'MACOS_SDK_URL=https://github.com/joseluisq/macosx-sdks/releases/download/12.3/MacOSX12.3.sdk.tar.xz' --tag local
                ;;
            aarch64-pc-windows-msvc)
                cargo build-docker-image aarch64-pc-windows-msvc-cross --tag local
                ;;
        esac

        cd ..
        ;;
esac

echo "Building for $TARGET..."
cross build --release --target "$TARGET"

if [[ -d "cross" ]]; then
    rm -rf cross
fi

echo "Build for $TARGET completed successfully."
