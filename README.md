# LocalChat — On-Device LLM Inference with MediaPipe


>An Android application demonstrating fully on-device multimodal LLM inference using **Google MediaPipe LLM Inference API** and **Gemma 3n**. No cloud. No API key. Everything runs locally on the device.
---

## Modules

```
localchat/
├── app/               # UI layer — Jetpack Compose, ViewModel, Hilt
└── inference/         # MediaPipe inference engine — the focus of this project
```

The `inference` module is fully decoupled from the UI. It exposes a clean API that any Android consumer can use regardless of UI framework.

---

## Inference Module

This is the core of the project. The `inference` module wraps the **MediaPipe LLM Inference API** and handles the complete lifecycle of running Gemma 3n on-device — model loading, session management, multimodal input, streaming token generation, benchmarking, and resource cleanup.

### Why MediaPipe LLM Inference API?

MediaPipe provides a high-level Android-native API for running LLMs on-device with GPU acceleration via OpenCL and Vulkan delegates. The `.task` model format bundles the model weights and tokenizer into a single file, eliminating manual tokenizer setup. It supports multimodal input (text + image + audio) natively on Gemma 3n without requiring separate preprocessing pipelines.

Official documentation: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android

---

### Model Setup

The model is **not bundled in the APK**. You must download it manually and place it in the assets folder before building.

**Step 1 — Download the model**

Download `gemma-3n-E2B-it-int4.task` from HuggingFace:

```
https://huggingface.co/litert-community/Gemma3n-E2B-IT
```

The model is Gemma 3n Effective-2B, 4-bit quantized, packaged in MediaPipe's `.task` format. File size is approximately 2GB.

**Step 2 — Place in assets**

```
inference/src/main/assets/models/gemma-3n-E2B-it-int4.task
```

The `GemmaInference` class copies this file to internal storage on first launch, since the MediaPipe API requires an absolute file path on the device rather than an asset stream.

> **Why copy to internal storage?**
> Android assets live inside a compressed APK and do not have a direct filesystem path. `LlmInference.createFromOptions()` takes a `String` path, not a stream, so the file must exist as a real path on disk. The copy happens once and is cached — subsequent launches load directly from internal storage.

---

### How It Works

#### Initialization

```kotlin
// LlmInference is the model engine — created once, expensive to initialize
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath.await())  // absolute path after asset copy
    .setMaxTokens(1024)
    .setTopK(40)
    .setTemperature(0.8f)
    .setMaxNumImages(10)              // required to enable image input at engine level
    .build()

val llmInference = LlmInference.createFromOptions(context, options)
```

`createFromOptions()` is blocking and takes 5–15 seconds on first run while loading model weights into memory. Always call this from a background thread or coroutine on `Dispatchers.IO`.

#### Session Management

The MediaPipe API separates the **engine** (`LlmInference`) from the **conversation** (`LlmInferenceSession`). The engine loads the model once. Sessions hold the conversation's KV cache — each session is one independent conversation context.

```kotlin
// Session holds conversation context (KV cache)
val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setTopK(40)
    .setTemperature(0.8f)
    .setGraphOptions(
        GraphOptions.builder()
            .setEnableVisionModality(true)  // required for image input at session level
            .build()
    )
    .build()

val session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
```

Vision modality must be enabled at **both** the engine level (`setMaxNumImages`) and the session level (`setEnableVisionModality`). Enabling only one will result in a runtime error when calling `addImage()`.

To start a new conversation, close the current session and create a fresh one — this clears the KV cache and resets the model's context window.

#### Streaming Token Generation

```kotlin
session.addQueryChunk(prompt)

session.generateResponseAsync { partialResult, done ->
    // Called on every token — partialResult is a single token string
    // done=true signals generation is complete
}
```

`generateResponseAsync` streams tokens back through the result listener as they are generated. Each callback delivers one token chunk. The `done` flag is `true` only on the final callback.

#### Image Input — MPImage

Gemma 3n supports image input natively. Images are passed through MediaPipe's `MPImage` container built from an Android `Bitmap`.

```kotlin

// Preprocess Bitmap to ensure it's software-allocated and in ARGB_8888 format
val preprocessBitmap = preprocessBitmap(imageData)
val processImage = BitmapImageBuilder(preprocessBitmap).build()

// Convert to MPImage — MediaPipe's internal image container
val mpImage: MPImage = BitmapImageBuilder(resized).build()

// Add to session — text chunk first, then image, then generate
session.addQueryChunk(prompt)
session.addImage(mpImage)
session.generateResponseAsync { partial, done -> ... }
```

`BitmapImageBuilder` reads pixel data via the CPU to construct the internal tensor representation — passing a hardware bitmap crashes the process. Always use `ImageDecoder.ALLOCATOR_SOFTWARE` or `bitmap.copy(Bitmap.Config.ARGB_8888, false)`.

---

### Benchmarking

The inference module measures three metrics for every generation:

| Metric | Description |
|---|---|
| **TTFT** | Time To First Token — latency from `generateResponseAsync()` call to first token callback |
| **Decode speed** | Tokens per second during the decode phase (after the first token) |
| **Total time** | Wall-clock time from call to `done=true` |

```kotlin
data class InferenceBenchmark(
    val timeToFirstTokenMs: Long,
    val totalTimeMs: Long,
    val totalTokens: Int,
    val decodeSpeedTps: Float,
)
```

TTFT captures the prefill latency — the time the model spends processing the input prompt before generating the first output token. This is separate from decode speed, which measures how fast the model auto-regressively generates subsequent tokens. On Gemma 3n with a mid-range GPU, expect TTFT around 800–2000ms and decode speed around 10–25 tokens/second depending on device.

---

### Inference State

The module exposes a `StateFlow<InferenceState>` that consumers observe to drive UI:

```kotlin
sealed class InferenceState {
    object Idle : InferenceState()
    object LoadingModel : InferenceState()
    object Ready : InferenceState()
    object Generating : InferenceState()
    data class Error(val message: String, val cause: Throwable? = null) : InferenceState()
}
```

---

### Resource Cleanup

```kotlin
// Always close session before closing the engine
// Closing engine first causes a native crash
session.close()       // releases KV cache
llmInference.close()  // releases model weights from memory
```

Both `LlmInference` and `LlmInferenceSession` implement `AutoCloseable`. In the `app` module, `GemmaInference.close()` is called from `ChatViewModel.onCleared()`, ensuring native resources are freed when the ViewModel is destroyed.

---

## App Module

Standard Android architecture consuming the `inference` module:

- **Jetpack Compose** — UI
- **Hilt** — dependency injection, `GemmaInference` injected into `ChatViewModel`
- **StateFlow + combine** — UI state derived from inference state and local UI state
- **ChatViewModel** — orchestrates message list, streaming append, and session lifecycle

The ViewModel owns the conversation message list and maps inference callbacks to UI state updates. Image selection is held in UI state until the next `sendMessage()` call, then cleared after the message is dispatched.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| LLM Runtime | MediaPipe LLM Inference API (`tasks-genai:0.10.27`) |
| Model | Gemma 3n E2B INT4 (`.task` format) |
| Image input | `BitmapImageBuilder` → `MPImage` |
| Async | Kotlin Coroutines + StateFlow |
| Architecture | MVVM — ViewModel / Use Case / Module |

---

## Requirements

- Android 10 (API 29) minimum
- Physical device required — **emulators are not supported** by the MediaPipe LLM Inference API
- Recommended: Snapdragon 8-series or Dimensity 9000+ with at least 6GB RAM
- ~2GB free internal storage for model copy

---

## Gradle Setup

```kotlin
// inference/build.gradle.kts
dependencies {
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("com.google.mediapipe:tasks-vison:latest.release")
}
```

Add to `inference/src/main/AndroidManifest.xml` inside `<application>` for GPU support:

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

---

## Project Structure

```
localchat/
├── app/
│   └── src/main/
│       ├── presenter/
│       │   └── ChatViewModel.kt            # ViewModel — message list, stream handling
│       └── model/
│           ├── ChatMessage.kt
│           ├── ChatRole.kt
│           └── ChatUiState.kt
│
└── inference/
    └── src/main/
        ├── assets/
        │   └── gemma-3n-E2B-it-int4.task   # ← place downloaded model here
        ├── GemmaInference.kt               # public API of the module
        └── model/
            ├── InferenceBenchmark.kt
            └── InferenceState.kt
```

---

## References

- [MediaPipe LLM Inference API — Android](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android)
- [Gemma 3n model documentation](https://ai.google.dev/gemma/docs/gemma-3n)
- [litert-community/Gemma3n-E2B-IT on HuggingFace](https://huggingface.co/litert-community/Gemma3n-E2B-IT)
- [MediaPipe Multimodal prompting guide](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android#multimodal-prompting)