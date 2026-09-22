package com.openai.flagsecure.compat;

import android.os.Build;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPatch 487 / Android 16 oriented FLAG_SECURE compatibility module.
 *
 * Goals:
 *  - app-process only: no system_server/com.android.server.wm hooks;
 *  - no Kotlin runtime dependency;
 *  - each hook is isolated so one API mismatch cannot abort app startup;
 *  - no unsafe LayoutParams cast;
 *  - no hard-coded hidden WindowManagerGlobal overload signatures;
 *  - install hooks only once per process.
 */
public final class DisableFlagSecureCompat implements IXposedHookLoadPackage {
    private static final String TAG = "FLAGSecure487: ";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null) return;

        // LSPatch integrated mode loads this inside the target app process.
        // Never try to act as a system_server module here.
        if ("android".equals(lpparam.packageName) || "android".equals(lpparam.processName)) {
            safeLog("skip system process");
            return;
        }

        if (!INSTALLED.compareAndSet(false, true)) {
            safeLog("hooks already installed for process " + lpparam.processName);
            return;
        }

        safeLog(
                "loading package=" + lpparam.packageName
                        + " process=" + lpparam.processName
                        + " sdk=" + Build.VERSION.SDK_INT
        );

        hookWindowSetFlags();
        hookWindowSetAttributes();
        hookSurfaceViewSetSecure();
        hookWindowManagerGlobal(lpparam.classLoader);

        safeLog("hook installation complete");
    }

    private static void hookWindowSetFlags() {
        try {
            XposedHelpers.findAndHookMethod(
                    Window.class,
                    "setFlags",
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null
                                    || param.args.length < 1
                                    || !(param.args[0] instanceof Integer)) {
                                return;
                            }

                            int flags = (Integer) param.args[0];
                            param.args[0] = flags & ~WindowManager.LayoutParams.FLAG_SECURE;
                        }
                    }
            );
            safeLog("Window.setFlags hooked");
        } catch (Throwable t) {
            safeLog("Window.setFlags skipped: " + t);
        }
    }

    private static void hookWindowSetAttributes() {
        try {
            XposedHelpers.findAndHookMethod(
                    Window.class,
                    "setAttributes",
                    WindowManager.LayoutParams.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 1) return;

                            Object arg = param.args[0];
                            if (arg instanceof WindowManager.LayoutParams) {
                                clearSecureFlag((WindowManager.LayoutParams) arg);
                            }
                        }
                    }
            );
            safeLog("Window.setAttributes hooked");
        } catch (Throwable t) {
            safeLog("Window.setAttributes skipped: " + t);
        }
    }

    private static void hookSurfaceViewSetSecure() {
        try {
            XposedHelpers.findAndHookMethod(
                    SurfaceView.class,
                    "setSecure",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length > 0) {
                                param.args[0] = false;
                            }
                        }
                    }
            );
            safeLog("SurfaceView.setSecure hooked");
        } catch (Throwable t) {
            safeLog("SurfaceView.setSecure skipped: " + t);
        }
    }

    private static void hookWindowManagerGlobal(ClassLoader appClassLoader) {
        try {
            Class<?> wmg = XposedHelpers.findClass(
                    "android.view.WindowManagerGlobal",
                    appClassLoader
            );

            XC_MethodHook clearLayoutParamsHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null) return;

                    for (Object arg : param.args) {
                        if (arg instanceof WindowManager.LayoutParams) {
                            clearSecureFlag((WindowManager.LayoutParams) arg);
                        } else if (arg instanceof ViewGroup.LayoutParams) {
                            // Intentionally ignore non-WindowManager LayoutParams.
                            // Do not cast them: a forced cast can crash the target app.
                        }
                    }
                }
            };

            Set<XC_MethodHook.Unhook> addHooks =
                    XposedBridge.hookAllMethods(wmg, "addView", clearLayoutParamsHook);
            Set<XC_MethodHook.Unhook> updateHooks =
                    XposedBridge.hookAllMethods(wmg, "updateViewLayout", clearLayoutParamsHook);

            safeLog(
                    "WindowManagerGlobal hooked addView="
                            + addHooks.size()
                            + " updateViewLayout="
                            + updateHooks.size()
            );
        } catch (Throwable t) {
            safeLog("WindowManagerGlobal skipped: " + t);
        }
    }

    private static void clearSecureFlag(WindowManager.LayoutParams lp) {
        if (lp == null) return;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
    }

    private static void safeLog(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
            // Logging must never be able to break target app startup.
        }
    }
}
