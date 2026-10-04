package com.sh958022.selectiontoolbar;

import android.graphics.Rect;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Temporary ChatGPT-only patch.
 *
 * Goal: keep the host selection menu intact and only promote the existing
 * "Add to chat" item into the floating toolbar's main row.
 *
 * No labels are changed, no items are removed, and the original MenuItem object
 * is preserved so ChatGPT's own click handling continues to run unchanged.
 */
public final class SelectionToolbarHook implements IXposedHookLoadPackage {
    private static final String TAG = "AddToChatFirst: ";
    private static final String TARGET_PACKAGE = "com.openai.chatgpt";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) return;

        try {
            XposedBridge.hookAllMethods(View.class, "startActionMode", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0) return;
                    if (!(param.args[0] instanceof ActionMode.Callback)) return;

                    ActionMode.Callback original = (ActionMode.Callback) param.args[0];
                    if (original instanceof PromoteCallback) return;

                    if (param.args.length >= 2 && param.args[1] instanceof Integer) {
                        int type = (Integer) param.args[1];
                        if (type != ActionMode.TYPE_FLOATING) return;
                    }

                    param.args[0] = new PromoteCallback(original);
                }
            });
            log("hook installed for " + lpparam.processName);
        } catch (Throwable t) {
            log("hook failed: " + t);
        }
    }

    private static final class PromoteCallback extends ActionMode.Callback2 {
        private final ActionMode.Callback original;

        PromoteCallback(ActionMode.Callback original) {
            this.original = original;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            boolean created = original.onCreateActionMode(mode, menu);
            if (!created) return false;
            promoteAddToChat(menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            boolean changed = original.onPrepareActionMode(mode, menu);
            boolean promoted = promoteAddToChat(menu);
            // Returning true forces the floating toolbar to reconsider layout when
            // we changed SHOW_AS_ACTION on the existing item.
            return changed || promoted;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return original.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            original.onDestroyActionMode(mode);
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (original instanceof ActionMode.Callback2) {
                ((ActionMode.Callback2) original).onGetContentRect(mode, view, outRect);
            } else {
                super.onGetContentRect(mode, view, outRect);
            }
        }
    }

    private static boolean promoteAddToChat(Menu menu) {
        if (menu == null) return false;

        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item == null || item.getTitle() == null) continue;

            String title = item.getTitle().toString().trim().toLowerCase(Locale.ROOT);
            if (!isAddToChat(title)) continue;

            try {
                // FloatingToolbar prioritizes items that require an action button
                // before ordinary/overflow items. This keeps ChatGPT's original
                // MenuItem/callback but moves it into the main toolbar row.
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                log("promoted: " + item.getTitle());
                return true;
            } catch (Throwable t) {
                log("promotion failed: " + t);
                return false;
            }
        }
        return false;
    }

    private static boolean isAddToChat(String title) {
        return title.contains("채팅에 추가")
                || title.contains("add to chat")
                || title.contains("add to conversation");
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
