# Compose PDF Viewer

A Compose Multiplatform PDF viewer for **Android** and **iOS**.

- Local file, in-memory bytes, or remote URL sources
- Horizontal page swiping, with the current page observable and controllable
- Pinch zoom, pan while zoomed, and double-tap zoom/reset
- Loading and error states (with retry) that you can replace with your own
- Right-to-left layouts, localizable page descriptions for accessibility
- Bounded memory use

Pages are rendered natively (`PdfRenderer` on Android, PDFKit on iOS) and everything
else (pager, zoom, gestures, states) is shared Compose code. The library depends only
on Compose Foundation/UI and kotlinx-coroutines.

## Installation

```kotlin
// build.gradle.kts of your shared module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.amrelmekawy:pdfviewer:0.1.0")
        }
    }
}
```

Requirements: Android `minSdk` 24, iOS (`iosArm64`, `iosSimulatorArm64`).

## Usage

```kotlin
@Composable
fun DocumentScreen(path: String) {
    val state = rememberPdfViewerState(PdfSource.File(path))
    PdfViewer(state, Modifier.fillMaxSize())
}
```

### Sources

```kotlin
PdfSource.File("/absolute/path/to/file.pdf")

// Compared by identity: remember the instance so the document is not reopened.
val bytes = remember(data) { PdfSource.Bytes(data) }

PdfSource.Remote(
    url = "https://example.com/report.pdf",
    headers = mapOf("Authorization" to "Bearer $token"),
)
```

Remote documents are downloaded once to the app's cache directory and reused (for
example after rotation). The cache key covers the URL **and every header**, so different
credentials never share a cached file. `state.reload()` downloads the document again.

On Android, add the internet permission to your app to use `PdfSource.Remote`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### State and errors

`PdfViewerState` is hoisted, so you can observe and control the viewer:

```kotlin
val state = rememberPdfViewerState(source, initialPage = 0)
val scope = rememberCoroutineScope()

when (val load = state.loadState) {
    PdfLoadState.Loading -> Unit
    is PdfLoadState.Ready -> Text("${state.currentPage + 1} / ${load.pageCount}")
    is PdfLoadState.Failed -> Text("Failed: ${load.error}")
}

Button(onClick = { scope.launch { state.scrollToPage(state.currentPage + 1) } }) { Text("Next") }
```

`currentPage` survives configuration changes. When the source changes, the old document
is closed and the new one opened. The document is closed when the state leaves the
composition.

Errors are a small sealed type and never contain platform messages (which can leak paths
or URLs): `NotFound`, `Network(httpCode)`, `InvalidDocument`, `PasswordProtected`,
`TooLarge`, and `Unknown`.

Replace the default loading and error UI with slots:

```kotlin
PdfViewer(
    state = state,
    loading = { CircularProgressIndicator() },
    error = { error, retry ->
        Column {
            Text(stringResource(messageFor(error)))
            Button(onClick = retry) { Text("Retry") }
        }
    },
)
```

### Right-to-left and accessibility

Pages follow `LocalLayoutDirection`: in a right-to-left layout the next page is on the
left. Use `reverseLayout = true` to flip that. Page content is never mirrored.

Each page has the accessibility description "Page X of Y". Pass your own to localize it:

```kotlin
PdfViewer(
    state = state,
    pageContentDescription = { page, count -> "صفحة ${page + 1} من $count" },
)
```

### Fullscreen

The library has no fullscreen mode; because the state is hoisted, you can show the same
state in a fullscreen screen and keep the document and current page:

```kotlin
val state = rememberPdfViewerState(source)
var fullscreen by rememberSaveable { mutableStateOf(false) }

if (fullscreen) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PdfViewer(state, Modifier.fillMaxSize())
        Button(onClick = { fullscreen = false }, Modifier.align(Alignment.TopEnd)) { Text("Close") }
    }
} else {
    Column {
        PdfViewer(state, Modifier.weight(1f))
        Button(onClick = { fullscreen = true }) { Text("Fullscreen") }
    }
}
```

## Platform notes

- **Android:** uses the framework `PdfRenderer` (no dependency). In-memory documents are
  written to a temporary file in the app's cache directory, deleted when closed.
- **iOS:** uses PDFKit, drawn into a CoreGraphics bitmap whose pixels are copied into
  Skia-owned memory. `NSURLSession` has no separate connect timeout, so remote downloads
  use one 30-second idle timeout.
- **Limits:** downloads are capped at 100 MiB, a rendered page at 4096 px per side and
  8 megapixels, and the page cache at 5 pages and 64 MiB.

## Limitations

Not supported in 0.1.0: password-protected documents (reported as `PasswordProtected`),
text selection and search, annotations, forms, thumbnails, outlines, vertical scrolling,
and desktop or web targets.

## Sample

`sample/` contains an app for both platforms that shows every source and error:

- Android: `./gradlew :sample:androidApp:installDebug`
- iOS: open `sample/iosApp/iosApp.xcodeproj` in Xcode and run.

## Development

```shell
./gradlew check                                 # tests, iOS simulator tests, lint, ktlint, ABI
./gradlew :pdfviewer:connectedAndroidDeviceTest # real PdfRenderer tests (device/emulator)
./gradlew :pdfviewer:updateKotlinAbi            # after an intended public API change
./gradlew :pdfviewer:dokkaGenerate              # API docs
```

## License

```
Copyright 2026 Amr Elmekawy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
