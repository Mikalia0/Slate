# Slate Advanced

Optional companion APK, built and signed separately by the Slate workflow. Uses the Android Storage Access Framework, no broad storage permission and no dynamic code loading in Slate.

Dependencies and credits:
- REAndroid ARSCLib (Apache-2.0): https://github.com/REAndroid/ARSCLib
- Apache Commons Compress (Apache-2.0): https://commons.apache.org/proper/commons-compress/
- XZ for Java: https://tukaani.org/xz/java.html
- zstd-jni (BSD-2-Clause): https://github.com/luben/zstd-jni
- Junrar (UnRAR license): https://github.com/junrar/junrar

MP Manager was a functional reference; no MP Manager source is copied into this implementation.
APK metadata edits produce a separate unsigned file; package cloning and DEX editing are not implemented. RAR compatibility depends on Junrar (RAR5 not supported).
