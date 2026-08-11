package com.openai.flagsecure.compat;

import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Compatibility-oriented rewrite of VarunS2002/Xposed-Disable-FLAG_SECURE 2.0.0.
 *
 * Design goals:
 *  - keep app-process hooks for Window.setFlags, SurfaceView.setSecure and
 *    WindowManagerGlobal add/update paths;
 *  - do not resolve system_server-only classes in ordinary app processes;
 *  - isolate every hook behind its own Throwable boundary;
 *  - avoid Kotlin/runtime helper classes and static hook initialization;
 *  - avoid hard-coding WindowManagerGlobal overload signatures where possible.
 */
public final class DisableFlagSecureCompat implements IXposedHookLoadPackage {
    private static final String TAG = "FLAGSecureCompat: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null) return;

        safeLog("loading for " + lpparam.packageName + " / " + lpparam.processName);

        hookWindowSetFlags();
        hookSurfaceViewSetSecure();
        hookWindowManagerGlobal(lpparam.classLoader);

        // These hooks only make sense inside Android's system_server. In rootless
        // LSPatch integrated mode the target app is not system_server, so attempting
        // to resolve com.android.server.wm.* there adds risk without benefit.
        if ("android".equals(lpparam.packageName) || "android".equals(lpparam.processName)) {
            hookSystemServer(lpparam.classLoader);
        }
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
                            if (param.args == null || param.args.length < 1 || !(param.args[0] instanceof Integer)) return;
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
            final Class<?> wmg = XposedHelpers.findClass("android.view.WindowManagerGlobal", appClassLoader);
            final XC_MethodHook clearLayoutParamsHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null) return;
                    for (Object arg : param.args) {
                        if (arg instanceof WindowManager.LayoutParams) {
                            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                            lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                        }
                    }
                }
            };

            Set<XC_MethodHook.Unhook> add = XposedBridge.hookAllMethods(wmg, "addView", clearLayoutParamsHook);
            Set<XC_MethodHook.Unhook> update = XposedBridge.hookAllMethods(wmg, "updateViewLayout", clearLayoutParamsHook);
            safeLog("WindowManagerGlobal hooked: addView=" + add.size() + ", updateViewLayout=" + update.size());
        } catch (Throwable t) {
            safeLog("WindowManagerGlobal skipped: " + t);
        }
    }

    private static void hookSystemServer(ClassLoader classLoader) {
        try {
            Class<?> windowState = XposedHelpers.findClass("com.android.server.wm.WindowState", classLoader);
            XposedHelpers.findAndHookMethod(
                    windowState,
                    "isSecureLocked",
                    XC_MethodReplacement.returnConstant(false)
            );
            safeLog("WindowState.isSecureLocked hooked");
        } catch (Throwable t) {
            safeLog("WindowState hook skipped: " + t);
        }

        try {
            Class<?> windowState = XposedHelpers.findClass("com.android.server.wm.WindowState", classLoader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.wm.WindowManagerService",
                    classLoader,
                    "isSecureLocked",
                    windowState,
                    XC_MethodReplacement.returnConstant(false)
            );
            safeLog("WindowManagerService.isSecureLocked hooked");
        } catch (Throwable t) {
            safeLog("WindowManagerService hook skipped: " + t);
        }
    }

    private static void safeLog(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
