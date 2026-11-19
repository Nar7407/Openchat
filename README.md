# AllChat — Universal BYOK AI Client for Android

AllChat is a native Kotlin & Jetpack Compose Android client that lets you connect your own API keys from multiple AI providers in a single Obsidian Dark workspace.

## Features

- **Multi-Provider BYOK System**:
  - **Google Gemini** (`generativelanguage.googleapis.com/v1beta` — SSE streaming chat, vision input, Gemini/Imagen image generation, and Veo async video generation)
  - **OpenAI-Compatible** (OpenAI, OpenRouter, Groq, DeepSeek, Mistral, Together AI, Ollama, LM Studio, or any custom endpoint)
  - **Anthropic Claude** (`/v1/messages` with `x-api-key` and `anthropic-version: 2023-06-01` headers)
  - **Model Discovery & Capability Tagging**: Fetch models automatically from providers or add model IDs manually, then toggle capabilities (`Text Chat`, `Image Input`, `Image Gen`, `Video Gen`, `Summarize`) per model.
  - **Connection Diagnostics**: Test provider connectivity and configure an optional **Proxy / Gateway URL** per provider.
- **Streaming Chat**:
  - Real-time SSE streaming with Stop Generation, Regenerate Last Response, Markdown rendering with copyable code blocks, per-message/per-conversation token usage badges, and conversation drawer (create, search, rename, delete).
  - Multimodal image attachments via the zero-permission Android Photo Picker or device camera.
- **Summarize Mode (+ Android Share Target)**:
  - Paste text, extract text client-side from `.txt`, `.md`, or `.pdf` files, or fetch and strip article text from any URL.
  - Select **Short**, **Medium**, or **Detailed** length; long documents are automatically split into chunks, summarized individually, and combined.
  - Appears in Android's system Share Sheet (`ACTION_SEND` `text/plain`) and opens shared text or URLs directly in Summarize mode.
- **Image & Video Studios**:
  - Generate images with aspect ratio selection, local gallery grid, fullscreen inspection, and save to `Pictures/AllChat`.
  - Submit asynchronous video jobs, monitor live polling progress with timeout/cancellation support, play results inline via Media3 ExoPlayer, and save to `Movies/AllChat`.

## Setup

1. Open the project in Android Studio or build with Gradle (`compileSdk 36`, `minSdk 24`).
2. Launch **AllChat** and open the **Settings** tab.
3. Tap **Add Provider** (or choose a preset such as Google Gemini, OpenAI, Anthropic, Groq, OpenRouter, or Ollama).
4. Paste your API key and tap **Save**.
5. Tap **Test** to verify connectivity and **Fetch Models** (or add a model ID manually) to populate your model list.
6. Toggle the desired capabilities on each model so it appears in the corresponding mode's model picker.

### Local Endpoints (Ollama / LM Studio)

- On the **Android Emulator**, use `http://10.0.2.2:11434/v1` for Ollama or `http://10.0.2.2:1234/v1` for LM Studio.
- On a **physical device**, enter your computer's local Wi-Fi IP address (e.g., `http://192.168.1.42:11434/v1`).

## Known Limitations

- **Device-Only Key Security**: API keys are encrypted via Android Keystore AES-GCM and stored locally on-device with no cloud sync. Anyone with unlocked access to the physical device can still use configured providers inside the app, so keep a device screen lock enabled.
- **Network / Gateway Restrictions**: Some regional networks or corporate firewalls block direct requests to third-party AI endpoints. Use the optional **Proxy / Gateway URL** field in Provider Settings to route requests through your own reverse proxy.
- **Provider Feature Support**: Not all OpenAI-compatible endpoints implement `/models`, `stream_options.include_usage`, `/images/generations`, or async `/videos` endpoints. Manual model ID entry is provided for endpoints without model listing.
- **Client-Side PDF Extraction**: Very large or scanned image-only PDFs without embedded text layers cannot yield extractable text client-side.
