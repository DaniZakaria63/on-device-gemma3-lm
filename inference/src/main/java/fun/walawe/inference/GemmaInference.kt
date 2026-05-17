package `fun`.walawe.inference

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.scale
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import `fun`.walawe.inference.model.InferenceBenchmark
import `fun`.walawe.inference.model.InferenceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel.MapMode.READ_ONLY
import javax.inject.Inject
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

class GemmaInference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG : String = "GemmaInference"

        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f
        private const val RANDOM_SEED = 42
        private const val MAX_BITMAP_PIXELS = 256
        private const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.task"
    }

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state = _state.asStateFlow()

    var llmInference: LlmInference? = null
    val currentBenchmark: InferenceBenchmark? = null
    private var currentSession: LlmInferenceSession? = null

    suspend fun loadModel() = withContext(Dispatchers.IO){
        _state.value = InferenceState.LoadingModel
        Log.d(TAG, "loadModel: Loading model from assets")

        val modelPath = async { resolveModelPath() }

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath.await())
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .setMaxNumImages(1)
            .build()

        llmInference = LlmInference.createFromOptions(context, inferenceOptions)

        requireNotNull(llmInference) {
            "LlmInference not initialized. Call loadModel() first."
        }

        currentSession = createSession()
        _state.value = InferenceState.Ready
        Log.d(TAG, "loadModel: Model loaded successfully")
    }

    fun generateText(
        prompt: String,
        onTokenReceived: (String) -> Unit,
        onComplete: (InferenceBenchmark) -> Unit
    ){
        val session = requireNotNull(currentSession) {
            "Inference session not initialized. Call loadModel() first."
        }

        if(_state.value != InferenceState.Ready) {
            Log.w(TAG, "generateText: Model is not ready. Current state: ${_state.value}")
            onComplete(InferenceBenchmark.empty())
            return
        }

        _state.value = InferenceState.Generating
        val startTime = System.currentTimeMillis()
        val totalTokens = session.sizeInTokens(prompt)
        session.addQueryChunk(prompt)
        session.generateResponseAsync { string, isFinish ->
            onTokenReceived(string)
            if(isFinish){
                val totalTime = System.currentTimeMillis() - startTime
                val decodeSpeed = if (totalTime > 0) {
                    totalTokens / (totalTime / 1000f)
                } else {
                    0f
                }
                val benchmark = InferenceBenchmark(
                    timeToFirstTokenMs = totalTime,
                    totalTimeMs = totalTime,
                    totalTokens = totalTokens,
                    decodeSpeedTps = decodeSpeed
                )
                onComplete(benchmark)
                _state.value = InferenceState.Ready
            }
        }
    }

    fun generateTextWithImage(
        prompt: String,
        imageData: Bitmap,
        onTokenReceived: (String) -> Unit,
        onComplete: (InferenceBenchmark) -> Unit
    ){
        val session = requireNotNull(currentSession) {
            "Session not initialized. Call loadModel() first."
        }

        if (_state.value !is InferenceState.Ready) {
            Log.w(TAG, "generateWithImage() called but manager is not Ready.")
            return
        }

        _state.value = InferenceState.Generating
        val startTime = System.currentTimeMillis()
        val totalTokens = session.sizeInTokens(prompt)

        val preprocessBitmap = preprocessBitmap(imageData)
        val processImage = BitmapImageBuilder(preprocessBitmap).build()

        session.addQueryChunk(prompt)
        session.addImage(processImage)
        session.generateResponseAsync { token, isFinish ->
            onTokenReceived(token)
            if (isFinish) {
                val totalTime = System.currentTimeMillis() - startTime
                val decodeSpeed = if (totalTime > 0) {
                    totalTokens / (totalTime / 1000f)
                } else {
                    0f
                }
                val benchmark = InferenceBenchmark(
                    timeToFirstTokenMs = totalTime,
                    totalTimeMs = totalTime,
                    totalTokens = totalTokens,
                    decodeSpeedTps = decodeSpeed
                )
                onComplete(benchmark)
                _state.value = InferenceState.Ready
            }
        }
    }

    fun close() {
        Log.d(TAG, "Closing Inference and releasing resources...")

        runCatching { currentSession?.close() }
            .onFailure { Log.w(TAG, "Error closing session: ${it.message}") }
        currentSession = null

        runCatching { llmInference?.close() }
            .onFailure { Log.w(TAG, "Error closing LlmInference: ${it.message}") }
        llmInference = null

        _state.value = InferenceState.Idle
        Log.d(TAG, "Resources released successfully.")
    }

    fun resetConversation() {
        currentSession?.close()
        currentSession = createSession()
        Log.d(TAG, "Conversation reset")
    }

    private fun createSession(): LlmInferenceSession {
        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(TEMPERATURE)
            .setRandomSeed(RANDOM_SEED)
            .setTopK(TOP_K)
            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
            .build()
        return LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
    }

    private fun preprocessBitmap(original: Bitmap): Bitmap {
        val w = original.width
        val h = original.height

        if (w <= MAX_BITMAP_PIXELS && h <= MAX_BITMAP_PIXELS) return original

        val scale = MAX_BITMAP_PIXELS.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()

        return original.scale(newW, newH)
            .also { Log.i(TAG, "Image scaled: ${w}x${h} → ${newW}x${newH}") }
    }

    private suspend fun resolveModelPath(): String = withContext(Dispatchers.IO) {
        val modelFileName = context.assets.list("")?.firstOrNull { it == MODEL_FILE_NAME }?:
            throw IllegalStateException("Model file $MODEL_FILE_NAME not found in assets")
        val internalFile = File(context.filesDir, "model.task")

        return@withContext when {
            internalFile.exists() && internalFile.length() > 1_000_000 -> {
                Log.d(TAG, "resolveModelPath: Model file found in internal storage: ${internalFile.absolutePath}")
                internalFile.absolutePath
            }
            assetExists(modelFileName) -> {
                Log.d(TAG, "resolveModelPath: Model file found in assets, copying to internal storage")
                context.assets.open(modelFileName).use { inputStream ->
                    internalFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "resolveModelPath: Model file copied to internal storage: ${internalFile.absolutePath}")
                internalFile.absolutePath
            }
            else -> {
                Log.e(TAG, "resolveModelPath: Model file not found in assets or internal storage")
                throw IllegalStateException("Model file not found")
            }
        }
    }

    fun assetExists(path: String): Boolean = runCatching {
        context.assets.open(path).close()
        true
    }.getOrDefault(false)

    fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                ?: throw IllegalStateException("Failed to decode bitmap from URI: $uri")
        }
    }

    private fun AssetManager.loadModelFile(
        modelPath: String
    ): MappedByteBuffer {
        val asset = openFd(modelPath)
        val inputStream = FileInputStream(asset.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = asset.startOffset
        val declaredLength = asset.declaredLength
        return fileChannel.map(READ_ONLY, startOffset, declaredLength)
    }
}