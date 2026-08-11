# FLAG Secure LSPatch Compat

Compatibility rewrite of the app-process behavior in VarunS2002/Xposed-Disable-FLAG_SECURE 2.0.0 for testing with LSPatch 0.8 build 439 on Android 16.

Changes from the original 2.0.0 logic:

- Java-only entry class, no Kotlin runtime/helper dependency in the module class.
- Every hook is isolated with `try/catch(Throwable)`.
- `com.android.server.wm.*` is only resolved in the `android`/system-server package/process.
- `WindowManagerGlobal.addView` and `updateViewLayout` use `hookAllMethods`, avoiding hard-coded hidden overload signatures.
- App hooks for `Window.setFlags`, `SurfaceView.setSecure`, and WindowManager layout params are retained.
- Separate package id: `com.openai.flagsecure.compat`, so it can coexist with the original module.

Build with Android SDK 36 and JDK 17+:

```bash
./gradlew :app:assembleRelease
```

The project uses a compile-only Xposed API stub jar. It is not packaged into the APK.
