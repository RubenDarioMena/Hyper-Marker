# Interactive Notes — Android App Design Specification

**Status:** Approved product direction; implementation specification for a coding agent  
**Platform:** Android (native Kotlin)  
**Working name:** Interactive Notes (temporary)  
**Scope:** V1, V1.1, and later enhancements  
**Primary objective:** Store and use interactive HTML experiences as personal notes, with Markdown authoring and non-destructive annotations.

## 1. Product vision

Build a lightweight offline-first Android application that combines a nested note library (similar in organization to Samsung Notes) with a fullscreen HTML viewer. The user will frequently generate interactive HTML notes outside the app (for example, through ChatGPT), download them to a smartphone, import them into a library, and interact with them without persistent browser toolbars.

Each imported HTML project is a **document**. A document can consist of a single monolithic `.html` file or an `index.html` entry point plus adjacent HTML/CSS/JS/images/assets, imported from a directory or ZIP archive. Markdown notes are another first-class document type.

The app also lets users write notes about the content, draw on top of it, place anchored note pins, and eventually replace displayed text without mutating the original source.

### Core principles

1. **Fullscreen reading:** No permanent browser address or top bar; controls appear only on demand.
2. **Non-destructive edits:** Preserve imported originals. Store annotations, Markdown content, and future text overrides in app-managed data separately.
3. **Offline-first:** Imported files remain available without a connection. Network permission for document content is opt-in per document.
4. **Reliable organization:** Nested folders, renaming, search, moving, deletion and recovery/confirmation.
5. **Portable content:** Preserve relative asset paths; support simple and bundled web projects.
6. **Touch and stylus friendly:** Drawing, long-press attachment, undo/redo, and accessible touch targets.
7. **Safe defaults:** Avoid unrestricted local file access, arbitrary native JavaScript bridges, and unsafe archive extraction.

## 2. V1 scope (required)

### Library
- Nested folders of arbitrary practical depth; documents inside folders.
- Folder/document create, rename, move, duplicate, delete; confirm destructive actions.
- Search by document title and folder name; optional favorites/recent items if easy.
- Import single `.html`/`.htm` files, HTML project folders, and `.zip` bundles.
- Detect project entry point (`index.html` preferred; otherwise ask the user to select an HTML file).
- Use Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`, or document provider equivalents) and Android Sharesheet (`ACTION_SEND`/`ACTION_SEND_MULTIPLE` where appropriate) to receive downloads from ChatGPT or another app.
- Copy imported content into app-private storage. Do not rely on indefinite access to a temporary shared URI.

### HTML viewer
- Native Android `WebView` with JavaScript enabled only for the selected document's viewer, and local assets loaded through `WebViewAssetLoader` or an equivalent controlled HTTPS-style asset provider.
- Fullscreen immersive reader, edge-to-edge, without a persistent address bar. Transient actions via a discreet floating affordance or bottom sheet.
- Browser-like essentials: back (web history before exiting), forward, reload, page zoom, fullscreen toggle, and open developer console.
- Proper relative resource resolution for imported multi-file projects. Test HTML/CSS/JS and built React production assets. Raw JSX/TSX is **not** executed directly; it must be built externally first.
- Allow JavaScript-driven interactivity. A static web page, a DOM-based SPA, and a canvas/PixiJS document should render where compatible with Android System WebView.
- Adjustable autoscroll (play/pause/speed/direction as appropriate), with manual touch pausing it.
- Collapsible console displaying `console.log`, `warn`, `error`, unhandled errors and asset-load failures when observable. Use `WebChromeClient.onConsoleMessage`, plus safe error capture as needed.

### Markdown notes
- Create and edit `.md`-backed notes within the library.
- Two editing experiences: **Source** (Markdown characters visible) and **Visual** (headings, lists, emphasis, links and common formatting shown as formatted text).
- Switching modes must preserve ordinary supported Markdown with minimal formatting loss. If a construct cannot round-trip faithfully, warn and keep the source as the authoritative version.
- Autosave and undo/redo. Markdown rendering may use a dedicated WebView-based editor or a native source editor + preview; choose a maintainable library, document its license, and make the implementation testable.

### Drawing and note overlays
- A **Draw mode** with pen, highlighter, eraser, basic color/width controls, undo/redo and clear-with-confirmation.
- A **Pin mode** allowing the user to anchor notes to displayed HTML elements, including a title/body editor with Markdown support.
- Pins and drawings must not modify the original HTML file. Store them as an overlay associated with the imported document.
- Pins open their note on tap. Allow edit, move/reanchor, and delete.
- In normal **View mode**, the HTML continues receiving touch gestures and interactive controls normally.

## 3. Signature gesture: long-press element highlighting for pin notes

**This behavior is a non-negotiable V1 requirement.**

### Interaction contract
1. User opens an HTML document and activates **Pin mode**.
2. User touches and holds a point on the rendered page for approximately **500 ms** (tunable for Android long-press accessibility).
3. While the touch is held, identify the DOM element under that point and **visually highlight the exact element to which the pin will attach**: a translucent fill plus clearly visible outline, following its current rendered bounds.
4. If the user moves the finger/stylus while maintaining the hold, update the prospective target and its highlight live. Optionally show a tiny attachment marker near the contact point.
5. On release, commit the selected target and open a compact note editor (title and Markdown body). Only create the pin if the user saves meaningful content. On cancel, remove the highlight and create nothing.
6. The saved pin appears at or near the selected touch point and remains anchored to that element when the page scrolls, the layout changes or the device rotates.
7. A short tap in Pin mode should select an existing pin; it should **not** accidentally activate links/buttons underneath. Normal interactions resume upon leaving Pin mode.
8. The temporary highlight is removed on release/cancel; a selected pin may show its target highlight again when explicitly inspected or reanchored.

### Target selection
- In Pin mode, inject a small, isolated JavaScript controller that tracks pointer events and queries `document.elementsFromPoint(clientX, clientY)`; ignore the app's own annotation/highlight layers and non-content wrappers.
- Choose a useful, visible target: prefer the nearest meaningful content element rather than an entire `body` or giant page wrapper when a child is available. Respect interactive elements when explicitly selected.
- Outline the selected element using an overlay (not a permanent style mutation) so the original layout does not shift.
- Prefer a stable DOM identifier when present (`id`, `data-*` anchor or carefully generated CSS selector). Also store fallback fingerprints and context (tag, text snippet, ancestor path, approximate position).
- Save the pin position as normalized coordinates within the target's bounding rectangle, not just raw screen pixels.
- Re-resolve anchors on reopen, zoom and layout changes; update pin placement after scroll/resize and relevant DOM mutation events. Debounce expensive observation.
- If the element disappears or an anchor cannot be recovered, mark the note **Unanchored** rather than silently attaching it to a wrong element. Offer manual reattachment.
- For `<canvas>`, WebGL, SVG drawings or documents with no meaningful DOM child, treat the visible surface as an anchor and save a relative location within it. Do not promise semantic selection of an object drawn inside canvas; that requires cooperation from the page.
- For very large or transformed DOM regions, visibly explain the chosen boundary. Never claim pin precision beyond the resolved anchor.

### Gesture conflicts
- Enter Pin mode explicitly so long presses on ordinary interactive documents are unaffected in View mode.
- Suppress text selection/context menus and accidental HTML clicks **only inside Pin mode**, where necessary.
- Respect pinch zoom and scroll by cancelling an ambiguous pending long-press on multi-touch or substantial movement before activation.
- Provide a clear Cancel action and exit path. Support finger and stylus events.

## 4. UI/navigation specification

### Library screen
- Clean note-list or card layout: folder breadcrumb, search, import (+), and nested folder view.
- One-tap open; long-press on a library item opens contextual actions.
- Distinguish HTML documents, Markdown notes and folders visually with subtle file-type icons.

### Reader screen
- HTML occupies the whole available screen by default; no persistent top chrome.
- A small unobtrusive action handle reveals a bottom sheet with **View / Pin / Draw / Markdown note / Console / Settings** and browser actions.
- Pin and Draw mode each have a minimal floating toolbar, easily dismissed.
- The annotation editor opens as a sheet/popup that avoids covering the anchor if practical.
- Keep the system navigation gesture functional; immersive mode should be reversible through standard Android gestures.

### Markdown screen
- Source / Visual toggle; basic formatting toolbar, undo/redo, save status and note title.
- Markdown notes can live anywhere in the folder tree and be linked conceptually to an HTML document, but embedding Markdown editing inside arbitrary imported webpages is not necessary for V1.

### Accessibility
- TalkBack labels, adequate touch sizes, scalable text, contrast-preserving selection outline, and a non-long-press alternative to create or reanchor a pin.

## 5. Architecture

### Recommended stack
- **Kotlin + Jetpack Compose:** Android shell, library UI, navigation, editor sheets.
- **Room:** Documents, folders, annotation metadata and link relationships.
- **App-private files:** Imported projects, Markdown bodies, original snapshots, drawing data as appropriate.
- **WebView + controlled asset serving:** Local HTML/CSS/JS rendering. Prefer `WebViewAssetLoader` with an app-private custom `PathHandler`, path validation and per-document access boundaries.
- **HTML-side annotation controller:** Small injected JS/CSS module responsible for hit testing, target highlighting, calculating DOM anchors and displaying position-aware pin visuals/SVG strokes.
- **Native/JS messaging:** Typed, versioned, narrowly scoped message interface for annotation events (e.g., target selected, request save, anchor geometry changed). Never give imported JavaScript general-purpose filesystem/native privileges.

### Recommended module boundaries

```
app/
  library/        # Compose UI, folders, search, import entry points
  viewer/         # WebView hosting, navigation, console, autoscroll
  markdown/       # Source/visual editor and Markdown persistence
  annotation/     # Pin/drawing models, tools, undo/redo, anchoring
  storage/        # Room DAOs, private files, safe archive importer
  security/       # Permission policy, path validation, URI handling
  bridge/         # Narrow WebView-controller message contracts
```

Use clean layers (UI → use cases/services → repositories/storage) without building a framework prematurely.

### Data model (indicative)

```
Folder(id, parentFolderId?, name, sortOrder, createdAt, updatedAt)
Document(id, folderId?, type[HTML|MARKDOWN], title, entryPath?, rootPath?, createdAt, updatedAt, trustPolicy, revision)
MarkdownContent(documentId, sourceMarkdown, updatedAt)
Annotation(id, documentId, kind[PIN|STROKE], payloadJson, createdAt, updatedAt)
Anchor(id, annotationId, strategy[DOM|SURFACE|PAGE], selector?, fingerprint?, relativeX, relativeY, fallbackX?, fallbackY?, status)
ViewerPreference(documentId, zoom?, scrollSpeed?, scrollDirection?, lastPosition?)
```

- Store pin Markdown text separately or within a versioned annotation payload; do not interpolate untrusted note content directly into HTML.
- Record annotation schema versions so exports/migrations remain possible.
- Keep a separate document identity even when two imports share a filename.
- Do not assume a filename maps to a stable DOM structure across reimports.

### Storage and import safety
- Normalize and validate every extracted archive entry: reject absolute paths, `../` traversal, symlinks outside extraction root and unexpected overwrite collisions.
- Set reasonable maximum total uncompressed size, file count and per-file size; report failures without half-imported documents.
- Preserve relative internal links while restricting reads to the current project's authorized content.
- No unrestricted `file://` reads or universal file URL access.
- Untrusted HTML has active scripts; default to offline document loading, disable unnecessary WebView permissions, and offer an explicit per-document option to allow network access if required. Do **not** describe the V1 WebView as a perfect sandbox.
- Keep `addJavascriptInterface` usage minimal or avoid it where possible; validate all messages and never expose broad filesystem/process controls.
- WebView renderer crashes should not lose the library or source files.

## 6. Annotation persistence and geometry

- Default: page-aware annotation overlay injected into the WebView DOM (e.g., dedicated SVG layer for strokes and pin marker layer), rendered above document content only in annotation modes where interaction is needed.
- Preserve coordinate systems: record pin relative to its anchor bounds; record drawings in document or chosen surface coordinates with viewport/zoom metadata.
- Recompute display geometry on scroll, zoom, rotation and resize. For reflow-heavy pages, pin annotations are expected to be more stable than arbitrary drawings; drawing reflow behavior should be documented.
- Avoid using screenshots as the primary source of truth for annotations.
- Undo/redo should cover creating, deleting and moving annotations, and drawing actions. Persist after completed strokes/transactions rather than every pixel movement.
- For all annotation elements, verify they do not unintentionally intercept touches in View mode.

## 7. V1.1: direct rendered-text editing and advanced anchors

Implement **Edit text mode** only after the core library, notes and annotation workflows are stable.

### Text editing behavior
1. User activates Edit text mode and taps a visible text element.
2. Highlight the selected block and open a text editor containing its rendered text.
3. Save an **override record**, not a destructive edit to the imported HTML.
4. On reopen, attempt to locate the same element and apply the override after page initialization.
5. Provide **Restore original** per override and expose conflicts/unmatched overrides.

### Constraints
- Static DOM pages: text replacement can generally work via text nodes or controlled content editing.
- React/Vue/other reactive apps: frameworks can re-render and overwrite manual DOM changes. Override reapplication via observers is only a best-effort compatibility mode; do not guarantee source-level editing of arbitrary SPA components.
- Never edit input/password fields or execute new arbitrary code as a consequence of text modification.
- Advanced anchor reattachment/reconciliation for changing pages belongs here: content-fingerprint matching, diff-aware import revision handling and orphan-management UI.

## 8. Later enhancements (not required for V1)

- `.rar` extraction through an appropriately licensed, maintained decompression library; keep ZIP as the first supported archive format.
- Advanced console/debugger features (network inspector, DOM explorer, JS eval) with careful security controls.
- Exports/backups (source project + annotations + Markdown) using a documented portable bundle format.
- Links between notes, optional tags, favorites, more drawing shapes, advanced handwriting tools.
- Optional project-specific online resource permissions and advanced React compatibility handling.

## 9. Definition of done / test matrix

The agent should produce a working Android project plus a reproducible debug APK build and documentation. Test at minimum:

**Library and import**
- Create 3 levels of nested folders, move/rename items, search and reopen after app restart.
- Import (a) a standalone HTML file, (b) a folder with relative CSS/JS/image resources, and (c) a ZIP containing `index.html` with assets.
- Import via Android Share from a downloaded file where supported.
- Reject a malicious ZIP with path traversal; do not write outside the project's import root.

**Viewer**
- No permanent toolbar; browser navigation and console available on demand.
- Verify live JavaScript interactions, asset loading, adjustable autoscroll and back/forward/reload.
- Validate a compiled React/Vite example built with relative asset paths.
- Rotate the device and change zoom without losing the document or its notes.

**Markdown**
- Create an `.md` note; edit headings/lists/emphasis in Source and Visual modes; save, reopen and compare content.
- Demonstrate recovery or warning for unsupported round-trip syntax.

**Draw and pins**
- Draw a stroke, scroll, undo it, redo it, close/reopen, and verify it persists.
- **Long-press (~500 ms) in Pin mode: DOM element under finger becomes clearly highlighted while held.**
- Slide to another target without releasing: highlight follows the new target.
- Release: note editor opens; cancel: no pin saved; save: pin appears attached to the selected element.
- Scroll/rotate/zoom/reopen: a saved DOM pin stays with the correct element and relative location.
- Test long-press over text, links, nested DOM elements and `<canvas>`; canvas fallback is explicit.
- Test a missing or dynamically replaced target; annotation becomes unanchored or is recovered with an observable, defensible rule (no silent incorrect attachment).
- Exit Pin mode and verify underlying HTML buttons, touch gestures and links work normally.

**Stability/security**
- Handle malformed HTML or missing assets without crashing.
- Verify app does not unintentionally grant arbitrary file reads or native privileges to imported JavaScript.
- Verify app stays usable offline for imported local-only content.

## 10. Implementation order for the coding agent

1. Create project skeleton, Room schema, storage repository and nested library UI.
2. Implement safe HTML/file/folder/ZIP import and asset serving.
3. Build immersive WebView viewer, browser controls, console and autoscroll.
4. Add Markdown creation/editing (Source + Visual), autosave and persistence.
5. Add WebView annotation controller and long-press DOM target highlighting; implement pins, save/edit/reanchor and persistence.
6. Add freehand drawing/highlighter overlay, undo/redo and persistence.
7. Harden lifecycle, orientation, gestures, imports and permission policies; add automated tests and a sample content fixture set.
8. Produce V1 debug APK and succinct instructions for building/installing and testing on an Android phone.
9. After V1 acceptance, implement V1.1 text override editing and more resilient dynamic anchors.
10. Defer RAR and advanced debugging until V1.1 or later.

## 11. Agent deliverables

- Runnable Android Studio/Gradle Kotlin project, with documented minimum SDK choice and dependencies.
- `README.md` with architecture, build, debug APK path, import workflow, and known limitations.
- `ARCHITECTURE.md` or equivalent with storage, WebView security boundaries and annotation-controller event protocol.
- Small representative test fixture collection: standalone HTML, multi-file HTML, built React sample, `<canvas>` sample, ZIP, Markdown note and malicious ZIP fixture.
- Tests for import safety, folder persistence and DOM anchor selection/recovery where practical.
- `PROGRESS.md` with completed features, decisions, limitations and next steps.
- APK build via `./gradlew assembleDebug`; if using a cloud CI environment, document artifact collection. Do not claim cloud APK generation succeeds unless the Android SDK and build have been verified.

## 12. Agent instruction / handoff

> Implement **V1** of the Android application described in this specification. Treat the long-press pin interaction—including visible live highlighting of the actual HTML element under the touch—as a mandatory acceptance criterion. Preserve imported originals, use secure app-private storage and controlled WebView asset access, and implement annotations as a separate layer. Follow the stated implementation order, build incrementally, test the listed cases and report any constraint before silently cutting scope. Do not start V1.1 or later features until V1 is demonstrably working. Keep architectural decisions and progress documented, and provide a reproducible debug APK build when the environment supports Android builds.
