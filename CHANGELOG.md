# Changelog

## 0.1.0 (unreleased)

First release.

- `PdfViewer` with horizontal or vertical paging, pinch zoom, pan, and double-tap zoom/reset.
- `PdfViewerState` with load state, current page, `scrollToPage`, and `reload`.
- Sources: local file, in-memory bytes, and remote URL with headers and a disk cache.
- Safe, platform-independent errors: `PdfError`.
- Right-to-left support and localizable page descriptions.
- Bounded bitmap memory: per-page size caps and a 5-page / 64 MiB cache.
- Android (`PdfRenderer`) and iOS (PDFKit) rendering.
