# Credits

SD Maid SE (https://github.com/d4rken-org/sdmaid-se), by darken and contributors, is a feature and compatibility reference. This initial implementation contains no copied SD Maid source or artwork. Any future imported code must retain its copyright notices, license and upstream provenance.

Slate uses Kotlin, AndroidX and Jetpack Compose. Their respective licenses apply.

Manufacturer marks: Motorola vector from [Simple Icons](https://github.com/simple-icons/simple-icons/blob/develop/icons/motorola.svg). Simple Icons artwork is distributed under CC0 subject to its [brand guidelines](https://github.com/simple-icons/simple-icons/blob/develop/DISCLAIMER.md); trademarks remain the property of their owners. Displayed for device identification only.

Privileged cache trimming uses the [Shizuku API](https://github.com/RikkaApps/Shizuku-API), by RikkaApps and contributors (Apache-2.0). No Shizuku application source is copied.

Additional manufacturer marks from Simple Icons: [samsung](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/samsung.svg), [xiaomi](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/xiaomi.svg), [oneplus](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/oneplus.svg), [google](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/google.svg), [asus](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/asus.svg), [sony](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/sony.svg), [huawei](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/huawei.svg), [honor](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/honor.svg), [oppo](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/oppo.svg), [vivo](https://github.com/simple-icons/simple-icons/blob/c01109118e134e061c9bdf2e1a7f814117f1f032/icons/vivo.svg). Same CC0 and trademark guidelines as above.

[App Manager](https://github.com/MuntashirAkon/AppManager), by Muntashir Al-Islam and contributors, is a compatibility reference for privileged Android cache trimming. Slate uses its own implementation and Android's internal package-manager API; no App Manager source is imported.

[AndroidHiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass) by LSPosed (Apache-2.0) is used for access to the cache-only package-manager interfaces within the privileged Shizuku service.

Adreno KGSL memstore utilization fallback adapted from Winlator-Ludashi commit [5cf4bac](https://github.com/Other-backup/Winlator-Ludashi/commit/5cf4bac607f17b673d1409be29da7c47d615cbd6), derived from The412Banner/Bannerlator commit 0ab047311ccc55b2911d984727d2d57ac6c0ebf7 and DroidDeck PRs #1969 and #1981 (GPL-3.0-only). Qualcomm KGSL minimal UAPI retains its GPL-2.0 WITH Linux-syscall-note notice.

### Integrated advanced file tools

- [ARSCLib](https://github.com/REAndroid/ARSCLib) — APK resources and binary manifest editing.
- [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/), [XZ for Java](https://tukaani.org/xz/java.html), [zstd-jni](https://github.com/luben/zstd-jni) and [Junrar](https://github.com/junrar/junrar) — archive extraction backends.
- [MP Manager](https://github.com/AbdurazaaqMohammed/MP-Manager) — functional reference, no source copied.

### Debloater

- [Canta](https://github.com/samolego/Canta) — feature reference.
- [Universal Android Debloater Next Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) — community package descriptions and risk classifications (downloaded from upstream at runtime).

- **7-Zip 26.06** — Igor Pavlov, https://github.com/ip7z/7zip/tree/d3296e39e1f1f92c7b41c0c3848e65640428d42d (LGPL-2.1-or-later and upstream public-domain/BSD portions; built with DISABLE_RAR=1). Slate's reproducible Android build: https://github.com/stevenmx2/Slate/blob/main/scripts/build-sevenzip.sh. License texts are included in the APK under `about/sevenzip`.
