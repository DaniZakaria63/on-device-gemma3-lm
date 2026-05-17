package `fun`.walawe.inference

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
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

class GemmaInference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG : String = "GemmaInference"

        private const val MAX_TOKENS = 1024
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.8f
        private const val RANDOM_SEED = 42
        private const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.task"
    }

    private val _state = MutableStateFlow<InferenceState>(InferenceState.Idle)
    val state = _state.asStateFlow()

    private val _currentBenchmark = MutableStateFlow<InferenceBenchmark?>(null)
    val currentBenchmark = _currentBenchmark.asStateFlow()

    var llmInference: LlmInference? = null
    private var currentSession: LlmInferenceSession? = null

    private var inferenceStartMs: Long = 0L
    private var firstTokenMs: Long = 0L
    private var tokenCount: Int = 0
    private var firstTokenReceived: Boolean = false

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

        val inference = requireNotNull(llmInference) {
            "LlmInference not initialized. Call loadModel() first."
        }

        currentSession = createSession()
        _state.value = InferenceState.Ready
        Log.d(TAG, "loadModel: Model loaded successfully")
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