package com.adhdcompanion.focus.data.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class LocalModelInfo(
    val exists: Boolean = false,
    val fileName: String = LocalLiteRtEngine.MODEL_FILE_NAME,
    val sizeBytes: Long = 0L,
    val formattedSize: String = ""
)

/**
 * Thread-safe LiteRT-LM manager that loads `.litertlm` models exclusively from
 * `context.filesDir/offline_model.litertlm` (never bundled in APK/assets).
 *
 * All native lifecycle calls (`initialize`, `sendMessageAsync`, `cancelProcess`,
 * and `close`) execute on a single dedicated single-thread dispatcher so native
 * state is never raced, GPU initialization falls back cleanly to CPU, and active
 * Conversations are always closed before their parent Engine.
 */
class LocalLiteRtEngine(private val appContext: Context) {

    private val context = appContext.applicationContext
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-LM-SingleThread").apply { isDaemon = true }
    }
    private val inferenceDispatcher: ExecutorCoroutineDispatcher = inferenceExecutor.asCoroutineDispatcher()

    private var engine: Engine? = null
    private var engineInitialized = false
    private var activeConversation: Conversation? = null
    private var loadedModelLastModified = -1L
    private var loadedBackendName: String? = null
    private val isReleased = AtomicBoolean(false)

    fun modelFile(): File = File(context.filesDir, MODEL_FILE_NAME)

    fun hasModel(): Boolean {
        val f = modelFile()
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun getModelInfo(): LocalModelInfo {
        val file = modelFile()
        val exists = file.exists() && file.isFile && file.length() > 0L
        val sizeBytes = if (exists) file.length() else 0L
        return LocalModelInfo(
            exists = exists,
            fileName = MODEL_FILE_NAME,
            sizeBytes = sizeBytes,
            formattedSize = if (exists) formatFileSize(sizeBytes) else ""
        )
    }

    suspend fun importModelFromUri(uri: Uri): Result<LocalModelInfo> = withContext(Dispatchers.IO) {
        runCatching {
            // Ensure any loaded Engine/Conversation releases the file first
            closeEngineSafely()

            val targetFile = modelFile()
            val tempFile = File(context.filesDir, "$MODEL_FILE_NAME.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            var totalBytes = 0L
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected model file." }
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(1024 * 128)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        totalBytes += read
                    }
                    output.fd.sync()
                }
            }

            if (totalBytes <= 0L) {
                tempFile.delete()
                error("Selected file is empty.")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                Log.w(TAG, "Failed to delete previous model file before replace")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            getModelInfo()
        }
    }

    fun streamGenerate(
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> = flow {
        check(!isReleased.get()) { "LocalLiteRtEngine has been released." }

        val file = modelFile()
        check(file.exists() && file.length() > 0L) {
            "No offline model found. Import a .litertlm file first."
        }

        val readyEngine = ensureEngineInitialized(file)
        closeActiveConversationLocked()

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt)
        )
        val conversation = readyEngine.createConversation(conversationConfig)
        activeConversation = conversation

        try {
            conversation.sendMessageAsync(userPrompt).collect { chunk ->
                val token = chunk.toString()
                if (token.isNotEmpty()) {
                    emit(token)
                }
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable + inferenceDispatcher) {
                runCatching { conversation.cancelProcess() }
            }
            throw ce
        } finally {
            withContext(NonCancellable + inferenceDispatcher) {
                closeConversationSafely(conversation)
                if (activeConversation === conversation) {
                    activeConversation = null
                }
            }
        }
    }.flowOn(inferenceDispatcher)

    suspend fun cancelGeneration() {
        withContext(NonCancellable + inferenceDispatcher) {
            activeConversation?.let { conv ->
                runCatching { conv.cancelProcess() }
            }
        }
    }

    suspend fun closeEngineSafely() {
        withContext(NonCancellable + inferenceDispatcher) {
            closeEngineLocked()
        }
    }

    fun shutdown() {
        if (!isReleased.compareAndSet(false, true)) return
        inferenceExecutor.execute {
            closeEngineLocked()
            inferenceExecutor.shutdown()
        }
    }

    private fun ensureEngineInitialized(file: File): Engine {
        val currentEngine = engine
        val modified = file.lastModified()
        if (currentEngine != null && engineInitialized && loadedModelLastModified == modified) {
            return currentEngine
        }

        closeEngineLocked()

        // Try GPU backend first, then automatically fall back to CPU if GPU init fails
        val gpuResult = tryInitializeBackend(file.absolutePath, Backend.GPU(), "GPU")
        if (gpuResult != null) {
            engine = gpuResult
            engineInitialized = true
            loadedModelLastModified = modified
            loadedBackendName = "GPU"
            return gpuResult
        }

        val cpuEngine = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU()
            )
        )
        try {
            cpuEngine.initialize()
            engine = cpuEngine
            engineInitialized = true
            loadedModelLastModified = modified
            loadedBackendName = "CPU"
            return cpuEngine
        } catch (t: Throwable) {
            // Do NOT leak a half-initialized Engine; never propagate secondary close failure
            runCatching { cpuEngine.close() }
            engine = null
            engineInitialized = false
            loadedModelLastModified = -1L
            loadedBackendName = null
            throw IllegalStateException(
                "Offline LiteRT-LM failed to initialize (${t.message ?: t.javaClass.simpleName}).",
                t
            )
        }
    }

    private fun tryInitializeBackend(
        modelPath: String,
        backend: Backend,
        label: String
    ): Engine? {
        var candidate: Engine? = null
        var initialized = false
        return try {
            candidate = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend
                )
            )
            candidate.initialize()
            initialized = true
            candidate
        } catch (t: Throwable) {
            Log.w(TAG, "LiteRT-LM $label backend init failed; falling back to CPU", t)
            if (initialized && candidate != null) {
                runCatching { candidate.close() }
            }
            null
        }
    }

    private fun closeEngineLocked() {
        closeActiveConversationLocked()
        val eng = engine
        val wasInitialized = engineInitialized
        engine = null
        engineInitialized = false
        loadedModelLastModified = -1L
        loadedBackendName = null
        if (eng != null && wasInitialized) {
            runCatching { eng.close() }
                .onFailure { Log.w(TAG, "Ignored error while closing Engine", it) }
        }
    }

    private fun closeActiveConversationLocked() {
        val conv = activeConversation ?: return
        activeConversation = null
        closeConversationSafely(conv)
    }

    private fun closeConversationSafely(conversation: Conversation) {
        runCatching { conversation.cancelProcess() }
        runCatching { conversation.close() }
            .onFailure { Log.w(TAG, "Ignored error while closing Conversation", it) }
    }

    companion object {
        private const val TAG = "LocalLiteRtEngine"
        const val MODEL_FILE_NAME = "offline_model.litertlm"

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.0f MB", mb)
            }
        }
    }
}
