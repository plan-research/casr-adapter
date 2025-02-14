#!/bin/bash

set -e

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <target1> [target2] [target3] ..."
    echo "Available targets:"
    echo "  x86_64-unknown-linux-gnu"
    echo "  aarch64-unknown-linux-gnu"
    echo "  x86_64-apple-darwin"
    echo "  aarch64-apple-darwin"
    echo "  x86_64-pc-windows-gnu"
    echo "  aarch64-pc-windows-msvc"
    exit 1
fi

for TARGET in "$@"; do
    echo "Processing target: $TARGET"
    ./build_one_target.sh "$TARGET"
done

echo "All builds completed successfully."
