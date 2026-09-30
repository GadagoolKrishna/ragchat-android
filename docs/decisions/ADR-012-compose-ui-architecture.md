# ADR-012: Material 3 Compose UI Architecture, Pluggable State Holders, and Design Tokens

- **Status**: Accepted
- **Date**: 2026-09-30
- **Authors**: Antigravity Assistant & Core Architecture Team

---

## Context & Problem Statement

The RagChat Android SDK requires an enterprise-ready, accessible, and themeable Compose UI library (`:sdk-ui-compose`). Host applications embedding the SDK need either full drop-in screens (`ChatScreen`, `DocumentManagerScreen`) or individual modular components (`MessageBubble`, `InputBar`, `CitationChip`, `ModelStatusBanner`, `FeedbackControls`).

The UI must satisfy strict quality criteria:
1. **Material 3 Design Tokens**: A `RagChatTheme` token system allowing host applications to customize color palettes, typography, shapes, and elevation.
2. **Decoupled Architecture**: Zero business logic inside Composables. All event dispatching and async operations are coordinated via stable state classes and Android `ViewModel` state holders.
3. **Pluggable Architecture**: Provide a default `ChatViewModelFactory` binding to the `:sdk` `RagChat` facade, while allowing hosts to provide their own custom `ViewModel` or `ChatManager`.
4. **Rich Content Rendering**: Support real-time streaming markdown text, formatted monospaced code blocks with horizontal scrolling, and structured Markdown table layouts.
5. **Citations & Grounding UI**: Modal bottom sheet (`SourceViewerSheet`) showing document context, page numbers, and visually highlighted chunk text.
6. **Localization & Accessibility**: Multi-language support (English, Hindi, Kannada, Tamil), minimum 48dp touch targets, TalkBack semantics, and high-contrast dark/light mode tokens.

---

## Decision Drivers

- **Android Framework Isolation**: Compose UI stays strictly within `:sdk-ui-compose`. Core modules (`:sdk-api`, `:sdk-core`, `:sdk-governance`) remain 100% pure JVM.
- **Compose Stability**: All UI state models use `@Immutable` or `@Stable` data classes to ensure minimal recomposition overhead during token streaming.
- **Accessibility & Internationalization**: Minimum 48x48dp touch targets for interactive elements, semantic labels on all icons, RTL compatibility, and localization in `en`, `hi`, `kn`, `ta`.

---

## Architectural Design

### 1. State Holder & ViewModel Pattern

```
┌──────────────────┐           ┌────────────────────┐           ┌────────────────────┐
│                  │  Events   │                    │  ask()    │                    │
│    ChatScreen    ├──────────►│   ChatViewModel    ├──────────►│    ChatManager     │
│   (Composables)  │◄──────────┤ (Android ViewModel)│◄──────────┤ (Flow<ChatEvent>)  │
│                  │  UI State │                    │  Events   │                    │
└──────────────────┘ (StateFlow)└────────────────────┘           └────────────────────┘
```

- Composables observe an immutable `ChatUiState` emitted as a `StateFlow`.
- Actions (`sendMessage()`, `stopGenerating()`, `onCitationClicked()`, `onFeedbackGiven()`) route exclusively to `ChatViewModel`.

### 2. Design Tokens (`RagChatTheme`)

`RagChatTheme` exposes:
- `RagChatColors`: Material 3 primary, secondary, container, user bubble, assistant bubble, error bubble, citation background, and code block background.
- `RagChatTypography`: Consistent heading, body, code, and caption text styles.
- `RagChatShapes`: Customizable corners for bubbles, chips, and sheets.
- `RagChatSpacing`: Standardized padding and margin scale (4dp, 8dp, 12dp, 16dp, 24dp, 32dp).

### 3. Rich Content & Code Rendering

`MessageBubble` parses message content:
- **Code Blocks**: Regex-delimited ` ```lang ... ``` ` spans rendered with monospaced typography, distinct background container, and horizontal scrolling.
- **Markdown Tables**: Simple pipe-delimited tables rendered as clean grid rows with divider lines.
- **Citations**: Interactive `CitationChip` elements docked below assistant messages. Clicking a chip invokes `SourceViewerSheet` highlighting the retrieved chunk in context.

---

## Consequences

### Positive
- Host applications can integrate the entire chat experience in two lines of Compose code (`ChatScreen()`).
- Modular components can be reused independently within existing host chat architectures.
- Guaranteed compliance with enterprise accessibility, font scaling, and multi-language requirements.
- Clean separation of concerns with full testability via unit and Compose tests.

### Considerations
- Full markdown parsing uses optimized lightweight regex rendering to maintain 60fps streaming performance without heavyweight third-party dependencies.
