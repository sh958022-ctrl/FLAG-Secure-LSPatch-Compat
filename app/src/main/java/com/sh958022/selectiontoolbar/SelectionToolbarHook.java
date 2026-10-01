package com.sh958022.selectiontoolbar;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
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
 * Selection Toolbar v0.1
 *
 * Scope: ChatGPT Android app only (com.openai.chatgpt).
 * Goal: keep a compact five-action selection menu:
 *   C | A | AC | +GPT | Tr
 *
 * This prototype reuses the host app's original ActionMode callback instead of
 * reimplementing Copy / Select All / Add to chat / Translate behavior.
 */
public final class SelectionToolbarHook implements IXposedHookLoadPackage {
    private static final String TAG = "SelectionToolbar: ";
    private static final String TARGET_PACKAGE = "com.openai.chatgpt";
    private static final int ID_COPY_ALL = 0x00F10001;

    private static final int ORDER_COPY = 10;
    private static final int ORDER_SELECT_ALL = 20;
    private static final int ORDER_COPY_ALL = 30;
    private static final int ORDER_GPT = 40;
    private static final int ORDER_TRANSLATE = 50;

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
                    if (original instanceof ToolbarCallback) return;

                    // Text selection on modern Android normally uses TYPE_FLOATING.
                    // Leave explicitly non-floating ActionModes untouched.
                    if (param.args.length >= 2 && param.args[1] instanceof Integer) {
                        int type = (Integer) param.args[1];
                        if (type != ActionMode.TYPE_FLOATING) return;
                    }

                    param.args[0] = new ToolbarCallback(original);
                }
            });
            log("hook installed for " + lpparam.processName);
        } catch (Throwable t) {
            log("hook failed: " + t);
        }
    }

    private static final class ToolbarCallback extends ActionMode.Callback2 {
        private final ActionMode.Callback original;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private Menu lastMenu;
        private int copyItemId = android.R.id.copy;
        private int selectAllItemId = android.R.id.selectAll;

        ToolbarCallback(ActionMode.Callback original) {
            this.original = original;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            boolean created = original.onCreateActionMode(mode, menu);
            if (!created) return false;
            lastMenu = menu;
            rewriteMenu(menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            original.onPrepareActionMode(mode, menu);
            lastMenu = menu;
            rewriteMenu(menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item != null && item.getItemId() == ID_COPY_ALL) {
                performCopyAll(mode);
                return true;
            }
            return original.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            lastMenu = null;
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

        private void performCopyAll(ActionMode mode) {
            final Menu menu = lastMenu;
            if (menu == null) return;

            final MenuItem selectAll = findAction(menu, selectAllItemId, "A", "모두 선택", "select all");
            if (selectAll == null) {
                log("AC: Select All action not found");
                return;
            }

            try {
                original.onActionItemClicked(mode, selectAll);
            } catch (Throwable t) {
                log("AC select-all failed: " + t);
                return;
            }

            // Selection changes can refresh ActionMode. Posting one frame later lets
            // the host finish that refresh before invoking Copy.
            mainHandler.postDelayed(() -> {
                try {
                    Menu active = lastMenu != null ? lastMenu : menu;
                    MenuItem copy = findAction(active, copyItemId, "C", "복사", "copy");
                    if (copy != null) {
                        original.onActionItemClicked(mode, copy);
                    } else {
                        log("AC: Copy action not found after Select All");
                    }
                } catch (Throwable t) {
                    log("AC copy failed: " + t);
                }
            }, 32L);
        }

        private void rewriteMenu(Menu menu) {
            if (menu == null) return;

            Snapshot copy = null;
            Snapshot selectAll = null;
            Snapshot addToChat = null;
            Snapshot translate = null;
            int translateScore = Integer.MIN_VALUE;

            // Snapshot first because we clear the host menu afterwards.
            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item == null) continue;

                String title = item.getTitle() == null ? "" : item.getTitle().toString();
                String normalized = title.trim().toLowerCase(Locale.ROOT);

                // Android TextView normally uses android.R.id.*, while Compose or
                // app-owned selection menus may use their own IDs. Match both ID
                // and visible label so v0.1 works on either path.
                if (item.getItemId() == android.R.id.copy || isCopy(normalized)) {
                    copy = Snapshot.of(item);
                    copyItemId = item.getItemId();
                    continue;
                }
                if (item.getItemId() == android.R.id.selectAll || isSelectAll(normalized)) {
                    selectAll = Snapshot.of(item);
                    selectAllItemId = item.getItemId();
                    continue;
                }

                if (isAddToChat(normalized)) {
                    addToChat = Snapshot.of(item);
                    continue;
                }

                int score = translationScore(normalized);
                if (score > translateScore) {
                    translateScore = score;
                    if (score > Integer.MIN_VALUE) {
                        translate = Snapshot.of(item);
                    }
                }
            }

            // v0.1 deliberately keeps only the five requested actions visible.
            menu.clear();

            if (copy != null) addSnapshot(menu, copy, ORDER_COPY, "C");
            if (selectAll != null) addSnapshot(menu, selectAll, ORDER_SELECT_ALL, "A");

            if (copy != null && selectAll != null) {
                menu.add(Menu.NONE, ID_COPY_ALL, ORDER_COPY_ALL, "AC")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            if (addToChat != null) addSnapshot(menu, addToChat, ORDER_GPT, "+GPT");
            if (translate != null) addSnapshot(menu, translate, ORDER_TRANSLATE, "Tr");
        }

        private static boolean isCopy(String title) {
            return "복사".equals(title) || "copy".equals(title);
        }

        private static boolean isSelectAll(String title) {
            return "모두 선택".equals(title)
                    || "전체 선택".equals(title)
                    || "select all".equals(title);
        }

        private static boolean isAddToChat(String title) {
            return title.contains("채팅에 추가")
                    || title.contains("add to chat")
                    || title.contains("add to conversation");
        }

        private static int translationScore(String title) {
            if (title == null || title.isEmpty()) return Integer.MIN_VALUE;
            if ("번역".equals(title) || "translate".equals(title) || "translation".equals(title)) {
                return 100;
            }
            if (title.contains("papago")) return 10;
            if (title.contains("번역") || title.contains("translate")) return 50;
            return Integer.MIN_VALUE;
        }

        private static MenuItem findAction(Menu menu, int preferredId, String... labels) {
            if (menu == null) return null;

            try {
                MenuItem byId = menu.findItem(preferredId);
                if (byId != null) return byId;
            } catch (Throwable ignored) {
            }

            for (int i = 0; i < menu.size(); i++) {
                MenuItem item = menu.getItem(i);
                if (item == null || item.getTitle() == null) continue;
                String title = item.getTitle().toString().trim().toLowerCase(Locale.ROOT);
                for (String label : labels) {
                    if (label != null && title.equals(label.toLowerCase(Locale.ROOT))) {
                        return item;
                    }
                }
            }
            return null;
        }

        private static void addSnapshot(Menu menu, Snapshot s, int order, String label) {
            MenuItem item = menu.add(s.groupId, s.itemId, order, label);
            try { item.setIntent(s.intent); } catch (Throwable ignored) {}
            try { item.setEnabled(s.enabled); } catch (Throwable ignored) {}
            try { item.setVisible(s.visible); } catch (Throwable ignored) {}
            try { item.setCheckable(s.checkable); } catch (Throwable ignored) {}
            try { item.setChecked(s.checked); } catch (Throwable ignored) {}
            try { item.setAlphabeticShortcut(s.alphaShortcut); } catch (Throwable ignored) {}
            try { item.setNumericShortcut(s.numericShortcut); } catch (Throwable ignored) {}
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    private static final class Snapshot {
        int groupId;
        int itemId;
        android.content.Intent intent;
        boolean enabled;
        boolean visible;
        boolean checkable;
        boolean checked;
        char alphaShortcut;
        char numericShortcut;

        static Snapshot of(MenuItem item) {
            Snapshot s = new Snapshot();
            s.groupId = item.getGroupId();
            s.itemId = item.getItemId();
            s.intent = item.getIntent();
            s.enabled = item.isEnabled();
            s.visible = item.isVisible();
            s.checkable = item.isCheckable();
            s.checked = item.isChecked();
            s.alphaShortcut = item.getAlphabeticShortcut();
            s.numericShortcut = item.getNumericShortcut();
            return s;
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + message);
        } catch (Throwable ignored) {
        }
    }
}
