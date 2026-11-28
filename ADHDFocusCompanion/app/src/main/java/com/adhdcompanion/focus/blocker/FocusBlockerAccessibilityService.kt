package com.adhdcompanion.focus.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.adhdcompanion.focus.MainActivity
import java.util.Locale

/**
 * Intercepts distracting apps, short-form feeds (YouTube Shorts & Instagram Reels),
 * and non-study YouTube videos (Regain-style Study Channels Only mode).
 */
class FocusBlockerAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var powerManager: PowerManager? = null

    private var overlayView: View? = null
    private var overlayTitleView: TextView? = null
    private var overlayBodyView: TextView? = null
    private var overlayBadgeView: TextView? = null
    private var overlayPrimaryButton: Button? = null
    private var overlaySecondaryButton: Button? = null
    private var overlayPasswordButton: Button? = null

    private var toastPillView: View? = null
    private val dismissToastRunnable = Runnable { removeToastPill() }

    private var activeShieldPackage: String? = null
    private var activeShieldLabel: String? = null
    private var activeShieldReason: String = REASON_BLOCKED_APP

    private var lastLoggedMs = 0L
    private var lastLoggedPackage: String? = null
    private var lastHomeActionMs = 0L
    private var lastLaunchRedirectMs = 0L
    private var lastShortsBackMs = 0L
    private var lastShortsWindowStartMs = 0L
    private var consecutiveShortsHits = 0
    private var lastYtBackMs = 0L
    private var lastApprovedYtAtMs = 0L
    private var lastContentInspectMs = 0L
    private var homeGracePackage: String? = null
    private var homeGraceUntilMs = 0L
    private var cachedEssentialPackages: Set<String> = emptySet()
    private var isActiveMonitorScheduled = false

    private val activeMonitorRunnable = object : Runnable {
        override fun run() {
            isActiveMonitorScheduled = false
            runCatching {
                val lockManager = FocusLockManager.getInstance(this@FocusBlockerAccessibilityService)
                val eval = lockManager.evaluateNow()
                val cfg = lockManager.config.value
                val inWatchedVideoApp = lastForegroundPackage in WATCHED_VIDEO_PACKAGES
                val shouldKeepWatching = eval.isBlockingActive ||
                    ((cfg.blockShortsAndReels || cfg.youtubeStudyModeEnabled) && inWatchedVideoApp)

                if (!shouldKeepWatching) {
                    if (activeShieldReason != REASON_TEST_SHIELD) {
                        removeOverlay()
                    }
                    return@runCatching
                }
                val isInteractive = powerManager?.isInteractive ?: true
                if (isInteractive) {
                    inspectCurrentWindowsAndEnforce(event = null)
                }
                if (isConnectedLive && shouldKeepWatching) {
                    isActiveMonitorScheduled = true
                    mainHandler.postDelayed(this, ACTIVE_MONITOR_INTERVAL_MS)
                }
            }
        }
    }

    private val followUpEnforceRunnable = Runnable {
        runCatching {
            val lockManager = FocusLockManager.getInstance(this)
            val eval = lockManager.evaluateNow()
            val cfg = lockManager.config.value
            val inWatchedVideoApp = lastForegroundPackage in WATCHED_VIDEO_PACKAGES
            if (eval.isBlockingActive || ((cfg.blockShortsAndReels || cfg.youtubeStudyModeEnabled) && inWatchedVideoApp)) {
                inspectCurrentWindowsAndEnforce(event = null)
            } else if (activeShieldReason != REASON_TEST_SHIELD) {
                removeOverlay()
            }
        }
    }

    override fun onServiceConnected() {
        runCatching {
            super.onServiceConnected()
            activeInstance = this
            isConnectedLive = true
            lastServiceError = null

            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.notificationTimeout = 80L
            info.packageNames = null
            serviceInfo = info

            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            cachedEssentialPackages = runCatching { buildEssentialPackages() }.getOrDefault(setOf(packageName))
            Log.d(TAG, "FocusBlockerAccessibilityService connected (full event coverage)")
            onRulesUpdated()
        }.onFailure { err ->
            lastServiceError = err.message ?: err.javaClass.simpleName
            Log.e(TAG, "onServiceConnected error", err)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            activeInstance = this
            isConnectedLive = true
            if (event == null) return

            val eventType = event.eventType
            val eventPkg = runCatching { event.packageName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }

            val isWindowSwitch = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

            if (isWindowSwitch) {
                val effectivePkg = eventPkg
                    ?: runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }
                if (effectivePkg != null) {
                    eventsReceivedCount += 1L
                    if (effectivePkg != packageName) {
                        if (lastForegroundPackage != effectivePkg && effectivePkg !in YOUTUBE_PACKAGES) {
                            lastApprovedYtAtMs = 0L
                        }
                        lastForegroundPackage = effectivePkg
                    }
                }
            }

            val lockManager = FocusLockManager.getInstance(this)
            val eval = lockManager.evaluateNow()
            val cfg = lockManager.config.value
            val activePkg = eventPkg
                ?: runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                ?: lastForegroundPackage

            val hasContentGuard = cfg.blockShortsAndReels || cfg.youtubeStudyModeEnabled
            val isInWatchedApp = activePkg in WATCHED_VIDEO_PACKAGES

            if (!eval.isBlockingActive) {
                if (!hasContentGuard || !isInWatchedApp) {
                    stopActiveMonitor()
                    if (activeShieldReason != REASON_TEST_SHIELD) {
                        removeOverlay()
                    }
                    return
                }
            }

            // Throttle high-frequency content/scroll events to keep battery & CPU tiny
            if (!isWindowSwitch && eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastContentInspectMs < CONTENT_INSPECT_THROTTLE_MS) {
                    return
                }
                lastContentInspectMs = now
            } else {
                lastContentInspectMs = SystemClock.elapsedRealtime()
            }

            ensureActiveMonitorRunning()
            inspectCurrentWindowsAndEnforce(event = event)
        }.onFailure { err ->
            lastServiceError = err.message ?: err.javaClass.simpleName
            Log.e(TAG, "onAccessibilityEvent error", err)
        }
    }

    private fun inspectCurrentWindowsAndEnforce(event: AccessibilityEvent?) {
        val lockManager = FocusLockManager.getInstance(this)
        val eval = lockManager.evaluateNow()
        val cfg = lockManager.config.value

        cachedEssentialPackages = runCatching { buildEssentialPackages() }
            .getOrDefault(cachedEssentialPackages.ifEmpty { setOf(packageName) })

        val now = SystemClock.elapsedRealtime()
        val rootNode = runCatching { rootInActiveWindow }.getOrNull()
        val rootPkg = runCatching { rootNode?.packageName?.toString() }.getOrNull()
        if (!rootPkg.isNullOrBlank() && rootPkg != packageName) {
            lastForegroundPackage = rootPkg
        }

        val candidatePackages = collectCandidatePackages(event, rootPkg)

        if (eval.isBlockingActive) {
            // 1. Check tamper attempts inside Settings / HyperOS Security / Package Installer
            val settingsOrInstallerPkg = candidatePackages.firstOrNull {
                it == "com.android.settings" ||
                    it == "com.miui.securitycenter" ||
                    it.contains("packageinstaller")
            }
            if (settingsOrInstallerPkg != null) {
                val screenTokens = mutableListOf<String>()
                runCatching {
                    event?.text?.forEach { cs ->
                        val str = cs?.toString()?.trim()
                        if (!str.isNullOrEmpty()) screenTokens.add(str)
                    }
                }
                collectNodeTexts(rootNode, screenTokens, maxTokens = 50, depth = 0)
                val clsName = runCatching { event?.className?.toString() }.getOrNull()
                if (lockManager.isTamperAttempt(settingsOrInstallerPkg, clsName, screenTokens)) {
                    engageShield(
                        blockedPackage = settingsOrInstallerPkg,
                        appLabel = "Device Settings / App Info",
                        reason = REASON_TAMPER,
                        windowName = eval.activeWindow?.name ?: "Focus",
                        endText = eval.activeWindow?.let { FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay) }
                            ?: "end of session",
                        useHomeAction = false
                    )
                    return
                }
            }

            // 2. Check any visible distracting app (including split-screen, floating windows, or UsageStats)
            val blockedPkg = candidatePackages.firstOrNull { pkg ->
                if (pkg == homeGracePackage && now < homeGraceUntilMs) {
                    false
                } else {
                    lockManager.isPackageBlockedNow(pkg, cachedEssentialPackages)
                }
            }

            if (blockedPkg != null) {
                val label = lockManager.getAppLabel(blockedPkg)
                val windowName = eval.activeWindow?.name?.ifBlank { "Focus Window" } ?: "Focus Window"
                val endText = eval.activeWindow?.let {
                    FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay)
                } ?: "your scheduled end time"

                engageShield(
                    blockedPackage = blockedPkg,
                    appLabel = label,
                    reason = REASON_BLOCKED_APP,
                    windowName = windowName,
                    endText = endText,
                    useHomeAction = true
                )
                return
            }
        }

        val activeVideoPkg = candidatePackages.firstOrNull { it in WATCHED_VIDEO_PACKAGES }
            ?: rootPkg?.takeIf { it in WATCHED_VIDEO_PACKAGES }

        // 3. Shorts & Reels blocker (also enforced automatically if YouTube Study Mode is ON)
        if ((cfg.blockShortsAndReels || (cfg.youtubeStudyModeEnabled && activeVideoPkg in YOUTUBE_PACKAGES)) &&
            activeVideoPkg != null &&
            !(activeVideoPkg == homeGracePackage && now < homeGraceUntilMs)
        ) {
            if (checkAndExitShortsOrReels(activeVideoPkg, rootNode)) {
                return
            }
        }

        // 4. Regain-style YouTube Study Channels Only guard
        if (cfg.youtubeStudyModeEnabled &&
            activeVideoPkg != null &&
            activeVideoPkg in YOUTUBE_PACKAGES &&
            !(activeVideoPkg == homeGracePackage && now < homeGraceUntilMs)
        ) {
            if (inspectAndEnforceYoutubeStudyMode(activeVideoPkg, rootNode, lockManager, eval)) {
                return
            }
        }

        // Only dismiss blocking shield when MainActivity is resumed or user navigated safely away
        if (overlayView != null && activeShieldReason != REASON_TEST_SHIELD) {
            if (isMainActivityResumed || (activeShieldReason == REASON_YT_NON_STUDY && activeVideoPkg == null)) {
                removeOverlay()
            }
        }
    }

    /**
     * Traverses visible accessibility nodes safely using a bounded BFS/DFS stack (max 18 depth, 220 nodes)
     * to detect YouTube Shorts, Instagram Reels, and mobile browser /shorts or /reel URLs.
     */
    private fun checkAndExitShortsOrReels(foregroundPkg: String, providedRoot: AccessibilityNodeInfo?): Boolean {
        val root = providedRoot ?: runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val isShortFeed = detectShortsOrReelsFeed(root, foregroundPkg)
        if (!isShortFeed) return false

        val now = SystemClock.elapsedRealtime()
        if (now - lastShortsBackMs > 750L) {
            lastShortsBackMs = now
            if (now - lastShortsWindowStartMs > 4000L) {
                lastShortsWindowStartMs = now
                consecutiveShortsHits = 1
            } else {
                consecutiveShortsHits += 1
            }

            val label = when (foregroundPkg) {
                in YOUTUBE_PACKAGES -> "YouTube Shorts"
                in INSTAGRAM_PACKAGES -> "Instagram Reels"
                else -> "Shorts / Reels (Browser)"
            }
            FocusLockManager.getInstance(this).recordBlockedAttempt(
                packageName = "$foregroundPkg.shorts",
                appLabel = label
            )

            // If opened via deep link where Back stayed inside Shorts 3x in a row, exit to Home cleanly
            if (consecutiveShortsHits >= 3) {
                consecutiveShortsHits = 0
                homeGracePackage = foregroundPkg
                homeGraceUntilMs = now + 900L
                performGlobalAction(GLOBAL_ACTION_HOME)
                showBriefToastPill("Exited $label · Protect your attention")
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
                showBriefToastPill("$label blocked · Stay on your plan")
                mainHandler.removeCallbacks(followUpEnforceRunnable)
                mainHandler.postDelayed(followUpEnforceRunnable, 420L)
            }
        }
        return true
    }

    private fun detectShortsOrReelsFeed(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val isYoutube = pkg in YOUTUBE_PACKAGES
        val isInstagram = pkg in INSTAGRAM_PACKAGES
        val isBrowser = pkg in BROWSER_PACKAGES

        var foundRemixLabel = false
        var foundShortTabOrReelDyn = false

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_INSPECT_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            val viewId = runCatching { node.viewIdResourceName?.lowercase(Locale.US) }.getOrNull().orEmpty()
            val text = runCatching { node.text?.toString()?.trim()?.lowercase(Locale.US) }.getOrNull().orEmpty()
            val desc = runCatching { node.contentDescription?.toString()?.trim()?.lowercase(Locale.US) }.getOrNull().orEmpty()
            val selected = runCatching { node.isSelected }.getOrDefault(false)

            if (isYoutube) {
                if (viewId.contains("reel_recycler") ||
                    viewId.contains("reel_player_page_container") ||
                    viewId.contains("reel_watch_fragment_root") ||
                    viewId.contains("shorts_player") ||
                    viewId.contains("reel_progress_bar") ||
                    viewId.contains("reel_item_container") ||
                    viewId.contains("reel_player_overlay") ||
                    viewId.contains("reel_main_container")
                ) {
                    return true
                }
                if (selected && (text == "shorts" || desc == "shorts" || desc.startsWith("shorts,"))) {
                    return true
                }
                if (viewId.contains("reel_dyn_") || desc.contains("shorts")) {
                    foundShortTabOrReelDyn = true
                }
                if (text == "remix" || desc == "remix") {
                    foundRemixLabel = true
                }
                if (foundRemixLabel && foundShortTabOrReelDyn) {
                    return true
                }
            } else if (isInstagram) {
                if (viewId.contains("clips_viewer") ||
                    viewId.contains("clips_video_container") ||
                    viewId.contains("reel_viewer") ||
                    viewId.contains("clips_swipe_refresh") ||
                    viewId.contains("clips_media_info") ||
                    viewId.contains("clips_author_info") ||
                    viewId.contains("clips_caption") ||
                    viewId.contains("clips_ufi") ||
                    viewId.contains("clips_progress")
                ) {
                    return true
                }
                if (selected && (text == "reels" || desc == "reels" || desc.startsWith("reels,"))) {
                    return true
                }
            } else if (isBrowser) {
                val combined = "$text $desc"
                if (combined.contains("youtube.com/shorts") ||
                    combined.contains("youtu.be/shorts") ||
                    combined.contains("m.youtube.com/shorts") ||
                    combined.contains("instagram.com/reel")
                ) {
                    return true
                }
            }

            if (depth < MAX_INSPECT_DEPTH) {
                val count = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
                for (i in 0 until count) {
                    node.getChild(i)?.let { queue.addLast(it to (depth + 1)) }
                }
            }
        }
        return false
    }

    /**
     * Regain-style YouTube Study Channels Only mode:
     * - Allows Search so the user can look up their study channel.
     * - Allows Home / Subscriptions browse screens.
     * - Whenever a video watch page opens, checks channel name / title metadata against whitelisted channels.
     * - Preserves fullscreen playback for an already-approved study lecture.
     */
    private fun inspectAndEnforceYoutubeStudyMode(
        ytPkg: String,
        providedRoot: AccessibilityNodeInfo?,
        lockManager: FocusLockManager,
        eval: com.adhdcompanion.focus.model.LockEvaluationState
    ): Boolean {
        val root = providedRoot ?: runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val snapshot = captureYoutubeScreenSnapshot(root)

        if (snapshot.isSearchScreen) {
            return false
        }
        if (!snapshot.hasVideoPlayer) {
            return false
        }

        val now = SystemClock.elapsedRealtime()

        // Allow fullscreen rotation if user was just watching an approved study lecture
        if (snapshot.isFullscreen && (now - lastApprovedYtAtMs) < APPROVED_YT_FULLSCREEN_GRACE_MS) {
            return false
        }

        // If player just opened and metadata is still populating, re-inspect shortly instead of misfiring
        if (!snapshot.hasMetadataArea && snapshot.tokens.size < 3) {
            mainHandler.removeCallbacks(followUpEnforceRunnable)
            mainHandler.postDelayed(followUpEnforceRunnable, 350L)
            return false
        }

        if (lockManager.isYouTubeContentAllowed(snapshot.tokens)) {
            lastApprovedYtAtMs = now
            if (activeShieldReason == REASON_YT_NON_STUDY && overlayView != null) {
                removeOverlay()
            }
            return false
        }

        // Video is NOT from any whitelisted study channel
        lastApprovedYtAtMs = 0L
        if (now - lastYtBackMs > 1000L) {
            lastYtBackMs = now
            lockManager.recordBlockedAttempt(
                packageName = "$ytPkg.nonstudy",
                appLabel = "YouTube (Non-Study)"
            )
            performGlobalAction(GLOBAL_ACTION_BACK)

            val cfg = lockManager.config.value
            val allowedPreview = if (cfg.allowedYoutubeChannels.isEmpty()) {
                "No study channels added yet — open Focus Shield -> Study Channels to add channels like Khan Academy or Physics Wallah."
            } else {
                val preview = cfg.allowedYoutubeChannels.take(4).joinToString(", ")
                val extra = if (cfg.allowedYoutubeChannels.size > 4) " +${cfg.allowedYoutubeChannels.size - 4} more" else ""
                "Only your whitelisted study channels play while YouTube Study Mode is on:\n$preview$extra"
            }

            showOrUpdateOverlay(
                blockedPackage = ytPkg,
                appLabel = "Non-Study YouTube Video",
                reason = REASON_YT_NON_STUDY,
                windowName = eval.activeWindow?.name ?: "YouTube Study Mode",
                endText = allowedPreview
            )
        }
        return true
    }

    private data class YoutubeScreenSnapshot(
        val isSearchScreen: Boolean,
        val hasVideoPlayer: Boolean,
        val isFullscreen: Boolean,
        val hasMetadataArea: Boolean,
        val tokens: List<String>
    )

    private fun captureYoutubeScreenSnapshot(root: AccessibilityNodeInfo): YoutubeScreenSnapshot {
        var isSearchScreen = false
        var hasVideoPlayer = false
        var hasExitFullscreen = false
        var hasMetadataArea = false
        val tokens = ArrayList<String>(40)

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_YT_INSPECT_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            val viewId = runCatching { node.viewIdResourceName?.lowercase(Locale.US) }.getOrNull().orEmpty()
            val text = runCatching { node.text?.toString()?.trim() }.getOrNull().orEmpty()
            val desc = runCatching { node.contentDescription?.toString()?.trim() }.getOrNull().orEmpty()
            val textLow = text.lowercase(Locale.US)
            val descLow = desc.lowercase(Locale.US)

            if (viewId.contains("search_edit_text") ||
                viewId.contains("search_query") ||
                viewId.contains("search_typeahead") ||
                (node.isEditable && viewId.contains("search"))
            ) {
                isSearchScreen = true
            }

            if (viewId.contains("player_view") ||
                viewId.contains("watch_player") ||
                viewId.contains("next_gen_watch") ||
                viewId.contains("watch_while_time_bar") ||
                viewId.contains("player_control")
            ) {
                hasVideoPlayer = true
            }

            if (viewId.contains("watch_metadata") ||
                viewId.contains("slim_video_information") ||
                viewId.contains("video_metadata") ||
                viewId.contains("channel_name") ||
                viewId.contains("owner_name") ||
                textLow == "subscribe" ||
                textLow == "subscribed" ||
                descLow == "subscribe" ||
                descLow.startsWith("subscribe to ")
            ) {
                hasMetadataArea = true
            }

            if (descLow.contains("exit fullscreen")) {
                hasExitFullscreen = true
            }

            if (text.length in 2..140 && textLow !in IGNORED_YT_UI_LABELS) {
                tokens.add(text)
            }
            if (desc.length in 2..140 && descLow !in IGNORED_YT_UI_LABELS) {
                tokens.add(desc)
            }

            if (depth < MAX_INSPECT_DEPTH) {
                val count = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
                for (i in 0 until count) {
                    node.getChild(i)?.let { queue.addLast(it to (depth + 1)) }
                }
            }
        }

        val isFullscreen = hasVideoPlayer && hasExitFullscreen && !hasMetadataArea
        return YoutubeScreenSnapshot(
            isSearchScreen = isSearchScreen,
            hasVideoPlayer = hasVideoPlayer,
            isFullscreen = isFullscreen,
            hasMetadataArea = hasMetadataArea,
            tokens = tokens
        )
    }

    private fun onRulesUpdated() {
        val lockManager = FocusLockManager.getInstance(this)
        val eval = lockManager.evaluateNow()
        val cfg = lockManager.config.value
        val inWatchedVideoApp = lastForegroundPackage in WATCHED_VIDEO_PACKAGES
        if (eval.isBlockingActive || ((cfg.blockShortsAndReels || cfg.youtubeStudyModeEnabled) && inWatchedVideoApp)) {
            ensureActiveMonitorRunning()
            mainHandler.post {
                runCatching { inspectCurrentWindowsAndEnforce(event = null) }
            }
        } else {
            stopActiveMonitor()
            if (activeShieldReason != REASON_TEST_SHIELD) {
                removeOverlay()
            }
        }
    }

    private fun ensureActiveMonitorRunning() {
        if (!isActiveMonitorScheduled && isConnectedLive) {
            isActiveMonitorScheduled = true
            mainHandler.postDelayed(activeMonitorRunnable, ACTIVE_MONITOR_INTERVAL_MS)
        }
    }

    private fun stopActiveMonitor() {
        isActiveMonitorScheduled = false
        mainHandler.removeCallbacks(activeMonitorRunnable)
        mainHandler.removeCallbacks(followUpEnforceRunnable)
    }

    override fun onInterrupt() {
        isConnectedLive = false
        stopActiveMonitor()
        removeOverlay()
        removeToastPill()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance === this) activeInstance = null
        isConnectedLive = false
        stopActiveMonitor()
        removeOverlay()
        removeToastPill()
        mainHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        isConnectedLive = false
        stopActiveMonitor()
        removeOverlay()
        removeToastPill()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun collectCandidatePackages(event: AccessibilityEvent?, rootPkg: String?): List<String> {
        val candidates = LinkedHashSet<String>()
        runCatching { event?.packageName?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(it) }
        rootPkg?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }

        runCatching { windows }.getOrNull()?.forEach { win ->
            runCatching {
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    win.root?.packageName?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { candidates.add(it) }
                }
            }
        }

        if (candidates.isEmpty() || candidates.all { it in cachedEssentialPackages }) {
            queryRecentUsageStatsPackage()?.let { candidates.add(it) }
        }

        return candidates.toList()
    }

    private fun queryRecentUsageStatsPackage(): String? {
        if (!hasUsageStatsPermission(this)) return null
        return runCatching {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 3_000L
            val usageEvents = usm.queryEvents(beginTime, endTime)
            val event = UsageEvents.Event()
            var latestForegroundPkg: String? = null
            var latestTimestamp = 0L
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.timeStamp >= latestTimestamp &&
                    !event.packageName.isNullOrBlank()
                ) {
                    latestTimestamp = event.timeStamp
                    latestForegroundPkg = event.packageName
                }
            }
            latestForegroundPkg
        }.getOrNull()
    }

    private fun engageShield(
        blockedPackage: String,
        appLabel: String,
        reason: String,
        windowName: String,
        endText: String,
        useHomeAction: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        if (blockedPackage != lastLoggedPackage || now - lastLoggedMs > 1200L) {
            lastLoggedPackage = blockedPackage
            lastLoggedMs = now
            Log.d(TAG, "Blocking $blockedPackage ($reason)")
            FocusLockManager.getInstance(this).recordBlockedAttempt(
                packageName = blockedPackage,
                appLabel = appLabel
            )
        }

        if (now - lastHomeActionMs > 220L) {
            lastHomeActionMs = now
            if (useHomeAction) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }

        showOrUpdateOverlay(
            blockedPackage = blockedPackage,
            appLabel = appLabel,
            reason = reason,
            windowName = windowName,
            endText = endText
        )

        if (now - lastLaunchRedirectMs > 850L) {
            lastLaunchRedirectMs = now
            openMainAppShield(blockedPackage, appLabel, reason)
        }

        mainHandler.removeCallbacks(followUpEnforceRunnable)
        mainHandler.postDelayed(followUpEnforceRunnable, 240L)
        mainHandler.postDelayed(followUpEnforceRunnable, 650L)
    }

    private fun showBriefToastPill(message: String) {
        mainHandler.post {
            runCatching {
                removeToastPillImmediate()
                val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.also {
                    windowManager = it
                } ?: return@runCatching

                val density = resources.displayMetrics.density
                fun dp(v: Int): Int = (v * density).toInt()

                val pillBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#EE10161D"))
                    cornerRadius = dp(999).toFloat()
                    setStroke(dp(1), Color.parseColor("#2EE6A6"))
                }
                val pill = TextView(this).apply {
                    text = message
                    setTextColor(Color.parseColor("#F8FAFC"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTypeface(null, Typeface.BOLD)
                    background = pillBg
                    setPadding(dp(18), dp(10), dp(18), dp(10))
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dp(48)
                }
                wm.addView(pill, params)
                toastPillView = pill
                mainHandler.removeCallbacks(dismissToastRunnable)
                mainHandler.postDelayed(dismissToastRunnable, 1800L)
            }
        }
    }

    private fun removeToastPill() {
        mainHandler.post { removeToastPillImmediate() }
    }

    private fun removeToastPillImmediate() {
        mainHandler.removeCallbacks(dismissToastRunnable)
        val view = toastPillView ?: return
        toastPillView = null
        runCatching { windowManager?.removeViewImmediate(view) }
    }

    private fun showOrUpdateOverlay(
        blockedPackage: String,
        appLabel: String,
        reason: String,
        windowName: String,
        endText: String
    ) {
        mainHandler.post {
            runCatching {
                activeShieldPackage = blockedPackage
                activeShieldLabel = appLabel
                activeShieldReason = reason

                val isTamper = reason == REASON_TAMPER
                val isTest = reason == REASON_TEST_SHIELD
                val isYtNonStudy = reason == REASON_YT_NON_STUDY

                val badgeText = when {
                    isTest -> "SHIELD TEST OK"
                    isYtNonStudy -> "YOUTUBE STUDY MODE"
                    isTamper -> "SETTINGS GUARDED"
                    else -> "FOCUS SHIELD ACTIVE"
                }
                val titleText = when {
                    isTest -> "Blocker overlay works!"
                    isYtNonStudy -> "Study Channels Only"
                    isTamper -> "Lock rules are protected"
                    else -> "Paused $appLabel"
                }
                val bodyText = when {
                    isTest -> "Your accessibility shield overlay rendered properly on this device. Tap either button below to dismiss."
                    isYtNonStudy -> endText
                    isTamper -> "App Info, Uninstall, and Accessibility Settings are guarded during $windowName (active until $endText). Start the cooldown timer inside Focus Companion if you genuinely need to change rules."
                    else -> "$windowName is active until $endText.\nTake one slow breath — no shame, just a gentle redirect back to your current step."
                }
                val primaryLabel = when {
                    isTest -> "Close Test Shield"
                    isYtNonStudy -> "Search Study Channels"
                    else -> "Return to Focus Companion"
                }
                val secondaryLabel = when {
                    isTest -> "Done"
                    isYtNonStudy -> "Open Shield Rules"
                    else -> "Go to Home Screen"
                }

                val hasExitPassword = !isTest && !isYtNonStudy && FocusLockManager.getInstance(this).hasExitPassword()

                val existing = overlayView
                if (existing != null && existing.isAttachedToWindow) {
                    overlayBadgeView?.text = badgeText
                    overlayTitleView?.text = titleText
                    overlayBodyView?.text = bodyText
                    overlayPrimaryButton?.text = primaryLabel
                    overlaySecondaryButton?.text = secondaryLabel
                    overlayPasswordButton?.visibility = if (hasExitPassword) View.VISIBLE else View.GONE
                    return@runCatching
                } else if (existing != null) {
                    overlayView = null
                }

                val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.also {
                    windowManager = it
                }
                if (wm == null) {
                    if (!isTest && !isYtNonStudy) openMainAppShield(blockedPackage, appLabel, reason)
                    return@runCatching
                }

                val overlay = buildShieldView(
                    badgeText = badgeText,
                    titleText = titleText,
                    bodyText = bodyText,
                    primaryLabel = primaryLabel,
                    secondaryLabel = secondaryLabel,
                    isTest = isTest,
                    showPasswordExit = hasExitPassword
                )
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                wm.addView(overlay, params)
                overlay.requestFocus()
                overlayView = overlay
                if (!isTest) {
                    ensureActiveMonitorRunning()
                }
            }.onFailure { err ->
                lastServiceError = err.message ?: err.javaClass.simpleName
                overlayView = null
                if (reason != REASON_TEST_SHIELD && reason != REASON_YT_NON_STUDY) {
                    openMainAppShield(blockedPackage, appLabel, reason)
                }
            }
        }
    }

    private fun buildShieldView(
        badgeText: String,
        titleText: String,
        bodyText: String,
        primaryLabel: String,
        secondaryLabel: String,
        isTest: Boolean = false,
        showPasswordExit: Boolean = false
    ): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F505070C"))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(24), dp(48), dp(24), dp(48))
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (isTest || activeShieldReason == REASON_YT_NON_STUDY) {
                        removeOverlay()
                    } else {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                    true
                } else {
                    false
                }
            }
        }

        val cardBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#111923"), Color.parseColor("#0A0D14"))
        ).apply {
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), Color.parseColor("#2EE6A6"))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBackground
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }

        val badgeBg = GradientDrawable().apply {
            setColor(Color.parseColor("#0D2E24"))
            cornerRadius = dp(999).toFloat()
            setStroke(dp(1), Color.parseColor("#2EE6A6"))
        }
        val badge = TextView(this).apply {
            text = badgeText
            setTextColor(Color.parseColor("#2EE6A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.1f
            background = badgeBg
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        overlayBadgeView = badge

        val title = TextView(this).apply {
            text = titleText
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
        }
        overlayTitleView = title

        val body = TextView(this).apply {
            text = bodyText
            setTextColor(Color.parseColor("#B6C2D2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(0, 0, 0, dp(24))
        }
        overlayBodyView = body

        val primaryBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2EE6A6"))
            cornerRadius = dp(16).toFloat()
        }
        val primaryButton = Button(this).apply {
            text = primaryLabel
            isAllCaps = false
            setTextColor(Color.parseColor("#04120C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            background = primaryBg
            minHeight = dp(52)
            setOnClickListener {
                val pkg = activeShieldPackage ?: packageName
                val label = activeShieldLabel ?: "Blocked App"
                val reason = activeShieldReason
                removeOverlay()
                when {
                    isTest -> Unit
                    reason == REASON_YT_NON_STUDY -> {
                        homeGracePackage = pkg
                        homeGraceUntilMs = SystemClock.elapsedRealtime() + 850L
                    }
                    else -> openMainAppShield(pkg, label, reason)
                }
            }
        }
        overlayPrimaryButton = primaryButton

        val secondaryBg = GradientDrawable().apply {
            setColor(Color.parseColor("#141923"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.parseColor("#263143"))
        }
        val secondaryButton = Button(this).apply {
            text = secondaryLabel
            isAllCaps = false
            setTextColor(Color.parseColor("#E3ECE7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = secondaryBg
            minHeight = dp(48)
            setOnClickListener {
                val reason = activeShieldReason
                when {
                    isTest -> removeOverlay()
                    reason == REASON_YT_NON_STUDY -> {
                        removeOverlay()
                        openFocusLockScreen()
                    }
                    else -> {
                        homeGracePackage = activeShieldPackage
                        homeGraceUntilMs = SystemClock.elapsedRealtime() + 850L
                        removeOverlay()
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
            }
        }
        overlaySecondaryButton = secondaryButton

        val passwordButton = Button(this).apply {
            text = "Exit Lock with Password"
            isAllCaps = false
            setTextColor(Color.parseColor("#2EE6A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = secondaryBg
            minHeight = dp(48)
            visibility = if (showPasswordExit) View.VISIBLE else View.GONE
            setOnClickListener {
                val pkg = activeShieldPackage ?: packageName
                val label = activeShieldLabel ?: "Blocked App"
                val reason = activeShieldReason
                removeOverlay()
                openMainAppShield(pkg, label, reason, openPasswordExit = true)
            }
        }
        overlayPasswordButton = passwordButton

        card.addView(
            badge,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            primaryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            secondaryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )
        card.addView(
            passwordButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return root
    }

    private fun openFocusLockScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOCUS_LOCK, true)
        }
        runCatching { startActivity(intent) }
    }

    private fun openMainAppShield(
        blockedPackage: String,
        appLabel: String,
        reason: String,
        openPasswordExit: Boolean = false
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(MainActivity.EXTRA_BLOCKED_APP_NAME, appLabel)
            putExtra(MainActivity.EXTRA_LOCK_BLOCKED_REASON, reason)
            if (openPasswordExit) {
                putExtra(MainActivity.EXTRA_OPEN_PASSWORD_EXIT, true)
            }
        }
        runCatching { startActivity(intent) }
    }

    private fun removeOverlay() {
        mainHandler.post {
            val view = overlayView ?: return@post
            overlayView = null
            overlayTitleView = null
            overlayBodyView = null
            overlayBadgeView = null
            overlayPrimaryButton = null
            overlaySecondaryButton = null
            overlayPasswordButton = null
            activeShieldReason = REASON_BLOCKED_APP
            runCatching {
                windowManager?.removeViewImmediate(view)
            }
        }
    }

    private fun buildEssentialPackages(): Set<String> {
        val set = mutableSetOf(
            packageName,
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.settings",
            "com.miui.securitycenter"
        )

        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { set.add(it) }

            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .forEach { set.add(it) }
        }

        runCatching {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.mapNotNull { it.packageName }?.forEach { set.add(it) }
        }
        runCatching {
            val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ime?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }

        return set
    }

    private fun collectNodeTexts(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        maxTokens: Int,
        depth: Int
    ) {
        if (node == null || out.size >= maxTokens || depth > 8) return
        runCatching {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            val count = node.childCount
            for (i in 0 until count) {
                if (out.size >= maxTokens) break
                collectNodeTexts(node.getChild(i), out, maxTokens, depth + 1)
            }
        }
    }

    companion object {
        private const val TAG = "FocusBlockerService"
        const val REASON_BLOCKED_APP = "blocked_app"
        const val REASON_TAMPER = "tamper_attempt"
        const val REASON_YT_NON_STUDY = "youtube_non_study"
        private const val REASON_TEST_SHIELD = "test_shield"
        private const val ACTIVE_MONITOR_INTERVAL_MS = 600L
        private const val CONTENT_INSPECT_THROTTLE_MS = 180L
        private const val MAX_INSPECT_DEPTH = 18
        private const val MAX_INSPECT_NODES = 220
        private const val MAX_YT_INSPECT_NODES = 240
        private const val MAX_CHILDREN_PER_NODE = 20
        private const val APPROVED_YT_FULLSCREEN_GRACE_MS = 3 * 60 * 60_000L

        private val YOUTUBE_PACKAGES = setOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.rvx.android.youtube"
        )

        private val INSTAGRAM_PACKAGES = setOf(
            "com.instagram.android",
            "com.instagram.lite"
        )

        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.brave.browser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.mi.globalbrowser"
        )

        private val WATCHED_VIDEO_PACKAGES = YOUTUBE_PACKAGES + INSTAGRAM_PACKAGES + BROWSER_PACKAGES

        private val IGNORED_YT_UI_LABELS = setOf(
            "home", "shorts", "subscriptions", "you", "library",
            "like", "dislike", "share", "remix", "download", "save",
            "clip", "thanks", "subscribe", "subscribed", "comments",
            "play video", "pause video", "next video", "previous video",
            "enter fullscreen", "exit fullscreen", "more options",
            "search", "cast", "notifications"
        )

        @Volatile
        private var activeInstance: FocusBlockerAccessibilityService? = null

        @Volatile
        var isMainActivityResumed: Boolean = false
            private set

        @Volatile
        var isConnectedLive: Boolean = false
            private set

        @Volatile
        var eventsReceivedCount: Long = 0L
            private set

        @Volatile
        var lastForegroundPackage: String? = null
            private set

        @Volatile
        var lastServiceError: String? = null
            private set

        fun setMainActivityResumed(resumed: Boolean) {
            isMainActivityResumed = resumed
            if (resumed) {
                val instance = activeInstance ?: return
                if (instance.activeShieldReason != REASON_TEST_SHIELD) {
                    instance.removeOverlay()
                }
            }
        }

        fun notifyRulesChanged() {
            activeInstance?.onRulesUpdated()
        }

        fun triggerTestShield(): Boolean {
            val service = activeInstance ?: return false
            service.showOrUpdateOverlay(
                blockedPackage = service.packageName,
                appLabel = "Test App",
                reason = REASON_TEST_SHIELD,
                windowName = "Test",
                endText = "now"
            )
            return true
        }

        fun isServiceEnabled(context: Context): Boolean {
            if (isConnectedLive) return true
            val expectedComponent = ComponentName(context, FocusBlockerAccessibilityService::class.java)
            return runCatching {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                enabledServices.any {
                    it.resolveInfo?.serviceInfo?.let { si ->
                        si.packageName == expectedComponent.packageName && si.name == expectedComponent.className
                    } == true
                }
            }.getOrDefault(false)
        }

        fun isAccessibilitySettingOn(context: Context): Boolean {
            if (isServiceEnabled(context)) return true
            val expectedComponent = ComponentName(context, FocusBlockerAccessibilityService::class.java)
            val enabledSetting = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull() ?: return false

            val flat = expectedComponent.flattenToString()
            val shortFlat = expectedComponent.flattenToShortString()
            return enabledSetting.split(':').any {
                it.equals(flat, ignoreCase = true) || it.equals(shortFlat, ignoreCase = true)
            }
        }

        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            return runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(true)
        }

        fun hasOverlayPermission(context: Context): Boolean {
            return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
        }

        @Suppress("DEPRECATION")
        fun hasUsageStatsPermission(context: Context): Boolean {
            return runCatching {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                } else {
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName
                    )
                }
                mode == AppOpsManager.MODE_ALLOWED
            }.getOrDefault(false)
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun openAppInfoSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun openOverlayPermissionSettings(context: Context) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { openAppInfoSettings(context) }
        }

        fun openUsageStatsSettings(context: Context) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
                .onFailure { openAppInfoSettings(context) }
        }
    }
}
