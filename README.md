# FLAG Secure LSPatch 487 Safe

A compatibility-focused FLAG_SECURE module build tailored for:

- LSPatch 1.2 build 487
- Android 16 / API 36
- Integrated patch mode
- app-process injection

This branch is intentionally conservative. It is designed to reduce the chance that the module itself causes app startup crashes.

## What changed

- Removed all `com.android.server.wm.*` / system_server hooks.
- Java-only entry class; no Kotlin runtime dependency.
- Every hook is isolated behind `try/catch(Throwable)`.
- Added an install-once guard per process.
- Removed unsafe `LayoutParams` casts.
- Uses `hookAllMethods` for hidden `WindowManagerGlobal` overloads instead of hard-coded signatures.
- Hooks:
  - `Window.setFlags(...)`
  - `Window.setAttributes(...)`
  - `SurfaceView.setSecure(false)`
  - `WindowManagerGlobal.addView(...)`
  - `WindowManagerGlobal.updateViewLayout(...)`
- Logging prefix: `FLAGSecure487:`

## Intended LSPatch test setup

- LSPatch: 1.2 (487)
- Patch mode: Integrated
- Signature bypass: lv2 first
- Module: this APK only
- Verbose patch log: enabled

Test one app at a time. For the current crash investigation, RIDI is the preferred comparison app because the previous Disable-FLAG_SECURE build crashed there while another screenshot module could enter the app.

## Build

GitHub Actions builds automatically on pushes to this branch and can also be started manually.

Artifact name:

`FLAG-Secure-LSPatch-487-Safe`

Local build:

```bash
gradle :app:assembleDebug
```

The Xposed API stub jar is compile-only and is not packaged into the APK.
