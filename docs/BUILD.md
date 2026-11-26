# Building Slate

Requirements: JDK 17, Android SDK 35, Gradle 8.9, NDK 27.2.12479018 and CMake 3.22.1.

```sh
gradle :app:testReleaseUnitTest :app:assembleRelease :advanced:assembleRelease
```

The main APK is in `app/build/outputs/apk/release/`. The optional companion APK is in `advanced/build/outputs/apk/release/`.

Set `SLATE_KEYSTORE_PATH` and `SLATE_KEYSTORE_PASSWORD` to sign local builds. Without them release APKs are unsigned. Maintainers can use `scripts/setup-signing.sh` to configure persistent signing and Actions secrets. Never commit signing credentials. Keep the original key backed up: rotating it prevents in-place updates.

## Privacy and limitations

Storage scans, indexes, app statistics and device readings are processed locally. Network access is used for update checks and downloads. Storage and usage access are requested for the corresponding features. Root and Shizuku require explicit authorization. Cleanup candidates are reviewed before deletion; scheduled scans do not automatically delete personal files. Protected files remain subject to Android and backend restrictions. Storage and app measurements may overlap or be incomplete; hardware readings depend on device support.

## Demo recordings

Record actual app screens using Android's screen recorder or ADB, with sample data and no personal information. Convert recordings with FFmpeg:

```sh
ffmpeg -i demo.mp4 -vf "fps=10,scale=360:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 demo.gif
```

Keep short clips in `docs/demos/` and embed only a few in the README. Do not present mockups as live app recordings.
