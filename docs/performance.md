# Slate performance verification

Release uses R8, resource shrinking and a conservative hand-authored baseline/startup profile.
The profile is an initial seed, not a measured profile. Use the benchmark build on a physical
Android device to measure startup with and without a profile and File Manager frame timing.
Complete onboarding, grant file access, select English and put a representative file set on the device.

```sh
gradle :benchmark:connectedBenchmarkAndroidTest
```

Use the same device, dataset, temperature and power settings when comparing versions.
For sideloaded APKs ProfileInstaller can install the packaged baseline profile; inspect logcat
and ART compilation state rather than assuming an immediate speedup on first launch.
Check release runtime functions: JNI archive and directory scanning, Shizuku, APK signing,
DocumentsProvider, gallery, updater and protected file operations.
