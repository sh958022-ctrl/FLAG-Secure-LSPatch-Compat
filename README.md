# Selection Toolbar v0.1

Prototype Xposed/LSPatch module for the ChatGPT Android app (`com.openai.chatgpt`).

## v0.1 goal

Keep the text-selection toolbar compact and prioritize only:

- `C` — Copy selected text
- `A` — Select all
- `AC` — Select all, then copy
- `+GPT` — Reuse ChatGPT's existing **채팅에 추가** action when present
- `Tr` — Reuse the best translation action already provided by the host/system

All other ActionMode menu items are hidden in this prototype so the five requested actions have room to stay in the first toolbar row.

## Implementation

The module hooks `View.startActionMode(...)` only inside `com.openai.chatgpt`, wraps the host `ActionMode.Callback`, lets ChatGPT/Android build the original menu first, then snapshots the requested actions and rebuilds the visible menu in this order:

`C | A | AC | +GPT | Tr`

`AC` calls the host's own Select All action and then, after a short UI-frame delay, calls the host's own Copy action.

## Known v0.1 limitations

- This is a prototype for Android 16 / LSPatch compatibility testing.
- `+GPT` appears only when the current ChatGPT build exposes an action whose label matches **채팅에 추가** / `Add to chat`.
- `Tr` prefers a plain system `번역` / `Translate` entry; Papago is only a fallback.
- v0.1 does **not** yet replace Android's FloatingToolbar layout. Two-row layout, long-press actions, dynamic URL/code/JSON rules, FloatMemo integration, and system-wide support belong to later versions.
- Compose/WebView text selection may use implementation paths that differ from classic `TextView`; device testing is required before expanding scope.

## Build

The `selection-toolbar-v0.1` branch has a GitHub Actions workflow that builds a debug APK on pushes to this branch and also supports manual workflow dispatch.

Expected artifact name: `Selection-Toolbar-v0.1`
