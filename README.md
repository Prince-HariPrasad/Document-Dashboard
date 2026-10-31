# Document Dashboard

An Android app that scans the documents on your phone, summarizes them with an
LLM, and presents them in a clean, glassmorphism-styled dashboard — fully
self-contained on the device.

## Features

- **Tabbed dashboard** — an Overview tab with a file-type distribution chart and
  insights, plus one tab per document type (PDF, DOCX, PPTX, XLS, TXT, …).
- **On-device summarization** — extracts text from PDFs (PdfBox), Office files
  (OOXML), and plain text, then summarizes via a cloud LLM of your choice.
- **Bring your own LLM** — configure **Google Gemini**, **xAI Grok**, or
  **Hugging Face** with your own API token in Settings. No external server or
  companion app required.
- **Manual summary editing** — type or correct any summary by hand, independent
  of the AI.
- **Built-in PDF viewer** — renders PDFs in-app (no external reader needed);
  Office files open via an installed viewer.
- **File management** — open, rename, delete (single or multi-select), and a
  dedicated "Unscanned" tab with a live scan loader and bulk "Summarize all".
- **Search & sort** — filter by name/summary; sort by name, size, or date.
- **Fast lists** — RecyclerView-backed, smooth with hundreds of documents.

## Requirements

- Android 7.0 (API 24)+ — targets API 34
- JDK 17+
- "All files access" permission (to scan and manage documents)

## Build

```bash
./gradlew assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

Install on a connected device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Configure the AI

Open the app → tap the **⚙ gear** → pick a provider and paste its API token:

- Gemini — https://aistudio.google.com/apikey
- Grok — https://console.x.ai
- Hugging Face — https://huggingface.co/settings/tokens

Then open the **Unscanned** tab and tap **Summarize all**, or use the **Edit**
button on any card to summarize or write a summary manually.

## Tech

- Kotlin, programmatic UI (no XML layouts)
- AndroidX AppCompat + RecyclerView
- PdfBox-Android for PDF text extraction and the in-app viewer uses `PdfRenderer`
- Cloud LLM APIs (Gemini / OpenAI-compatible for Grok & Hugging Face)
