# JVM Adapter for LibCASR
## Requirements
You need `cargo`, `docker` and `cross` or permissions to install it. You might as well need `libiconv`.
## Build
Building is a dependency for `jar` task in gradle. You can run build manually by calling [build script](CasrAdapter/build.sh) and passing desired targets as parameters. They should be formatted as output of the `rustc --print target-list`.
## Supported platforms
* Linux x86
* Linux arm
* Windows x86
* Windows arm
* MacOS x86
* MacOS arm