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
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class LocalModelInfo(
    val exists: Boolean = false,
    val fileName: String = LocalLiteRtEngine.MODEL_FILE_NAME,
    val sizeBytes: Long = 0L,
    val formattedSize: String = ""
)

/**
 * Memory-safe LiteRT-LM manager that loads `.litertlm` models exclusively from
 * `context.filesDir/offline_model.litertlm` (never bundled in APK/assets).
 *
 * Configured for mid-range and low-RAM Android tablets:
 * - Caps context KV-cache (`maxNumTokens = 1024`).
 * - Defaults to `Backend.CPU(threadCount = 4)` instead of GPU/OpenCL to avoid
 *   driver memory duplication spikes.
 * - Catches `OutOfMemoryError` and native initialization failures gracefully.
 * - Auto-closes the active `Conversation` and `Engine` after 60 seconds of idle
 *   time (and supports immediate release when leaving Doubt Solver) to free RAM.
 */
class LocalLiteRtEngine(private val appContext: Context) {

    private val context = appContext.applicationContext
    private val inferenceExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-LM-SingleThread").apply { isDaemon = true }
    }
    private val inferenceDispatcher: ExecutorCoroutineDispatcher = inferenceExecutor.asCoroutineDispatcher()

    private val idleTimerExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "LiteRT-LM-IdleTimer").apply { isDaemon = true }
    }
    private val idleLock = Any()
    private var pendingIdleClose: ScheduledFuture<*>? = null

    private var engine: Engine? = null
    private var engineInitialized = false
    private var activeConversation: Conversation? = null
    private var loadedModelLastModified = -1L
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
            cancelIdleClose()
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
        cancelIdleClose()

        val file = modelFile()
        check(file.exists() && file.length() > 0L) {
            "No offline model found. Import a .litertlm file first."
        }

        val readyEngine = ensureEngineInitialized(file)
        closeActiveConversationLocked()

        var conversation: Conversation? = null
        try {
            val conversationConfig = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt)
            )
            conversation = readyEngine.createConversation(conversationConfig)
            activeConversation = conversation

            conversation.sendMessageAsync(userPrompt).collect { chunk ->
                val token = chunk.toString()
                if (token.isNotEmpty()) {
                    emit(token)
                }
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable + inferenceDispatcher) {
                conversation?.let { runCatching { it.cancelProcess() } }
            }
            throw ce
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during offline LiteRT-LM inference", oom)
            withContext(NonCancellable + inferenceDispatcher) {
                closeEngineLocked()
                System.gc()
            }
            throw IllegalStateException(
                "Not enough free RAM on this device to finish the offline AI response. Close unused background apps or switch to online mode.",
                oom
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Offline LiteRT-LM generation failed", t)
            withContext(NonCancellable + inferenceDispatcher) {
                closeEngineLocked()
            }
            throw IllegalStateException(
                "Offline AI couldn't generate a response (${t.message ?: t.javaClass.simpleName}).",
                t
            )
        } finally {
            withContext(NonCancellable + inferenceDispatcher) {
                conversation?.let { closeConversationSafely(it) }
                if (activeConversation === conversation) {
                    activeConversation = null
                }
                if (!isReleased.get() && engineInitialized) {
                    scheduleIdleClose()
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

    /**
     * Non-blocking release trigger for UI lifecycle exit (e.g. leaving the Doubt Solver
     * screen) so Conversation and Engine native RAM is reclaimed immediately.
     */
    fun releaseOnUiExit() {
        cancelIdleClose()
        if (isReleased.get()) return
        runCatching {
            inferenceExecutor.execute {
                closeEngineLocked()
            }
        }
    }

    suspend fun closeEngineSafely() {
        cancelIdleClose()
        withContext(NonCancellable + inferenceDispatcher) {
            closeEngineLocked()
        }
    }

    fun shutdown() {
        if (!isReleased.compareAndSet(false, true)) return
        cancelIdleClose()
        runCatching { idleTimerExecutor.shutdownNow() }
        runCatching {
            inferenceExecutor.execute {
                closeEngineLocked()
                inferenceExecutor.shutdown()
            }
        }
    }

    private fun ensureEngineInitialized(file: File): Engine {
        val currentEngine = engine
        val modified = file.lastModified()
        if (currentEngine != null && engineInitialized && loadedModelLastModified == modified) {
            return currentEngine
        }

        closeEngineLocked()

        var candidateEngine: Engine? = null
        var initialized = false
        return try {
            val config = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.CPU(threadCount = CPU_WORKER_THREADS),
                maxNumTokens = MAX_CONTEXT_TOKENS
            )
            candidateEngine = Engine(config)
            candidateEngine.initialize()
            initialized = true
            engine = candidateEngine
            engineInitialized = true
            loadedModelLastModified = modified
            candidateEngine
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError initializing LiteRT-LM Engine", oom)
            if (initialized && candidateEngine != null) {
                runCatching { candidateEngine.close() }
            }
            closeEngineLocked()
            System.gc()
            throw IllegalStateException(
                "Not enough free RAM to load the offline AI model on this tablet. Close heavy background apps or use online mode.",
                oom
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Native/runtime failure initializing LiteRT-LM Engine", t)
            if (initialized && candidateEngine != null) {
                runCatching { candidateEngine.close() }
            }
            closeEngineLocked()
            throw IllegalStateException(
                "Offline LiteRT-LM failed to initialize (${t.message ?: t.javaClass.simpleName}). Check that the .litertlm file is valid or use online mode.",
                t
            )
        }
    }

    private fun scheduleIdleClose() {
        synchronized(idleLock) {
            pendingIdleClose?.cancel(false)
            if (isReleased.get()) return
            pendingIdleClose = idleTimerExecutor.schedule({
                if (!isReleased.get()) {
                    runCatching {
                        inferenceExecutor.execute {
                            if (activeConversation == null && engineInitialized) {
                                Log.d(TAG, "Auto-closing idle LiteRT-LM Engine after ${IDLE_CLOSE_TIMEOUT_SECONDS}s to free RAM")
                                closeEngineLocked()
                            }
                        }
                    }
                }
            }, IDLE_CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun cancelIdleClose() {
        synchronized(idleLock) {
            pendingIdleClose?.cancel(false)
            pendingIdleClose = null
        }
    }

    private fun closeEngineLocked() {
        closeActiveConversationLocked()
        val eng = engine
        val wasInitialized = engineInitialized
        engine = null
        engineInitialized = false
        loadedModelLastModified = -1L
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
        const val MAX_CONTEXT_TOKENS = 1024
        const val CPU_WORKER_THREADS = 4
        const val IDLE_CLOSE_TIMEOUT_SECONDS = 60L

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
