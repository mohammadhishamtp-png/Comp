package com.adhdcompanion.focus.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.adhdcompanion.focus.MainActivity

/**
 * Detects foreground app switches during active scheduled focus lock windows
 * (and 1-tap Quick Locks) and immediately shields the screen with a
 * TYPE_ACCESSIBILITY_OVERLAY + Home/Focus redirect when a distracting app opens.
 */
class FocusBlockerAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var powerManager: PowerManager? = null
    private var overlayView: View? = null
    private var overlayTitleView: TextView? = null
    private var overlayBodyView: TextView? = null
    private var overlayBadgeView: TextView? = null
    private var activeShieldPackage: String? = null
    private var activeShieldLabel: String? = null
    private var activeShieldReason: String = REASON_BLOCKED_APP

    private var lastContentCheckMs = 0L
    private var lastContentCheckPackage: String? = null
    private var lastLoggedMs = 0L
    private var lastLoggedPackage: String? = null
    private var lastHomeActionMs = 0L
    private var lastLaunchRedirectMs = 0L
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
                if (!eval.isBlockingActive) {
                    removeOverlay()
                    return@runCatching
                }
                val isInteractive = powerManager?.isInteractive ?: true
                if (isInteractive) {
                    inspectCurrentWindowsAndEnforce(event = null)
                }
                if (isConnectedLive && eval.isBlockingActive) {
                    isActiveMonitorScheduled = true
                    mainHandler.postDelayed(this, ACTIVE_MONITOR_INTERVAL_MS)
                }
            }
        }
    }

    private val followUpEnforceRunnable = Runnable {
        runCatching {
            val eval = FocusLockManager.getInstance(this).evaluateNow()
            if (eval.isBlockingActive) {
                inspectCurrentWindowsAndEnforce(event = null)
            } else {
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
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            cachedEssentialPackages = runCatching { buildEssentialPackages() }.getOrDefault(setOf(packageName))
            onRulesUpdated()
        }.onFailure { err ->
            lastServiceError = err.message ?: err.javaClass.simpleName
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            activeInstance = this
            isConnectedLive = true
            eventsReceivedCount += 1L

            if (event == null) return

            val eventPkg = runCatching { event.packageName?.toString() }.getOrNull()
            if (!eventPkg.isNullOrBlank()) {
                lastForegroundPackage = eventPkg
            }

            val lockManager = FocusLockManager.getInstance(this)
            val eval = lockManager.evaluateNow()
            if (!eval.isBlockingActive) {
                stopActiveMonitor()
                removeOverlay()
                return
            }

            ensureActiveMonitorRunning()

            val now = SystemClock.elapsedRealtime()
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                // Only throttle repeated content changes from the SAME package so we never drop an app switch
                if (eventPkg == lastContentCheckPackage && now - lastContentCheckMs < 140L) {
                    return
                }
                lastContentCheckMs = now
                lastContentCheckPackage = eventPkg
            }

            inspectCurrentWindowsAndEnforce(event)
        }.onFailure { err ->
            lastServiceError = err.message ?: err.javaClass.simpleName
        }
    }

    private fun inspectCurrentWindowsAndEnforce(event: AccessibilityEvent?) {
        val lockManager = FocusLockManager.getInstance(this)
        val eval = lockManager.evaluateNow()
        if (!eval.isBlockingActive) {
            stopActiveMonitor()
            removeOverlay()
            return
        }

        if (cachedEssentialPackages.isEmpty()) {
            cachedEssentialPackages = runCatching { buildEssentialPackages() }
                .getOrDefault(setOf(packageName))
        }

        val now = SystemClock.elapsedRealtime()
        val rootNode = runCatching { rootInActiveWindow }.getOrNull()
        val rootPkg = runCatching { rootNode?.packageName?.toString() }.getOrNull()
        if (!rootPkg.isNullOrBlank()) {
            lastForegroundPackage = rootPkg
        }

        val candidatePackages = collectCandidatePackages(event, rootPkg)

        // Check tamper attempts inside Settings / HyperOS Security / Package Installer
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

        // Check any visible distracting app (including split-screen or floating windows on tablets)
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

        // If user is inside Focus Companion itself (and no blocked window is visible), clear system overlay
        if (rootPkg == packageName && overlayView != null) {
            removeOverlay()
        }
    }

    private fun onRulesUpdated() {
        val lockManager = FocusLockManager.getInstance(this)
        val eval = lockManager.evaluateNow()
        if (eval.isBlockingActive) {
            ensureActiveMonitorRunning()
            mainHandler.post {
                runCatching { inspectCurrentWindowsAndEnforce(event = null) }
            }
        } else {
            stopActiveMonitor()
            removeOverlay()
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
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance === this) activeInstance = null
        isConnectedLive = false
        stopActiveMonitor()
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        isConnectedLive = false
        stopActiveMonitor()
        removeOverlay()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun collectCandidatePackages(event: AccessibilityEvent?, rootPkg: String?): List<String> {
        val candidates = LinkedHashSet<String>()
        rootPkg?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }
        runCatching { event?.packageName?.toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates.add(it) }

        // Inspect all visible application windows (handles tablet split-screen & floating windows)
        runCatching { windows }.getOrNull()?.forEach { win ->
            runCatching {
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    win.root?.packageName?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { candidates.add(it) }
                }
            }
        }
        return candidates.toList()
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
            FocusLockManager.getInstance(this).recordBlockedAttempt(
                packageName = blockedPackage,
                appLabel = appLabel
            )
        }

        showOrUpdateOverlay(
            blockedPackage = blockedPackage,
            appLabel = appLabel,
            reason = reason,
            windowName = windowName,
            endText = endText
        )

        if (now - lastHomeActionMs > 220L) {
            lastHomeActionMs = now
            if (useHomeAction) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }

        // Also bring Focus Companion's in-app shield forward if activity transition is permitted,
        // and schedule rapid follow-up checks in case the blocked app's launch animation beats HOME.
        if (now - lastLaunchRedirectMs > 850L) {
            lastLaunchRedirectMs = now
            openMainAppShield(blockedPackage, appLabel, reason)
        }

        mainHandler.removeCallbacks(followUpEnforceRunnable)
        mainHandler.postDelayed(followUpEnforceRunnable, 260L)
        mainHandler.postDelayed(followUpEnforceRunnable, 650L)
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
                val badgeText = when {
                    isTest -> "BLOCKER TEST OK"
                    isTamper -> "SETTINGS GUARDED"
                    else -> "FOCUS LOCK ACTIVE"
                }
                val titleText = when {
                    isTest -> "Blocker overlay works!"
                    isTamper -> "Lock rules are protected"
                    else -> "Paused $appLabel"
                }
                val bodyText = when {
                    isTest -> "Your accessibility shield overlay rendered properly on this device. Tap either button below to dismiss."
                    isTamper -> "App Info, Uninstall, and Accessibility Settings are guarded during $windowName (active until $endText). Start the cooldown timer inside Focus Companion if you genuinely need to change rules."
                    else -> "$windowName is active until $endText.\nTake one slow breath — no shame, just a gentle redirect back to your current step."
                }

                val existing = overlayView
                if (existing != null && existing.isAttachedToWindow) {
                    overlayBadgeView?.text = badgeText
                    overlayTitleView?.text = titleText
                    overlayBodyView?.text = bodyText
                    return@runCatching
                } else if (existing != null) {
                    // Stale detached view reference; clear before re-adding
                    overlayView = null
                }

                val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.also {
                    windowManager = it
                }
                if (wm == null) {
                    if (!isTest) openMainAppShield(blockedPackage, appLabel, reason)
                    return@runCatching
                }

                val overlay = buildShieldView(badgeText, titleText, bodyText, isTest = isTest)
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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
                overlayView = overlay
                ensureActiveMonitorRunning()
            }.onFailure { err ->
                lastServiceError = err.message ?: err.javaClass.simpleName
                overlayView = null
                if (reason != REASON_TEST_SHIELD) {
                    openMainAppShield(blockedPackage, appLabel, reason)
                }
            }
        }
    }

    private fun buildShieldView(
        badgeText: String,
        titleText: String,
        bodyText: String,
        isTest: Boolean = false
    ): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2101614"))
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }

        val cardBackground = GradientDrawable().apply {
            setColor(Color.parseColor("#1D2622"))
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), Color.parseColor("#385447"))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBackground
            setPadding(dp(24), dp(28), dp(24), dp(24))
        }

        val badgeBg = GradientDrawable().apply {
            setColor(Color.parseColor("#2A4236"))
            cornerRadius = dp(20).toFloat()
        }
        val badge = TextView(this).apply {
            text = badgeText
            setTextColor(Color.parseColor("#8ED8B0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            background = badgeBg
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        overlayBadgeView = badge

        val title = TextView(this).apply {
            text = titleText
            setTextColor(Color.parseColor("#F4F7F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(10))
        }
        overlayTitleView = title

        val body = TextView(this).apply {
            text = bodyText
            setTextColor(Color.parseColor("#C2CEC8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1.0f)
            setPadding(0, 0, 0, dp(24))
        }
        overlayBodyView = body

        val primaryBg = GradientDrawable().apply {
            setColor(Color.parseColor("#8ED8B0"))
            cornerRadius = dp(26).toFloat()
        }
        val returnButton = Button(this).apply {
            text = if (isTest) "Close Test Shield" else "Return to Focus Companion"
            isAllCaps = false
            setTextColor(Color.parseColor("#0D261A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            background = primaryBg
            minHeight = dp(52)
            setOnClickListener {
                val pkg = activeShieldPackage ?: packageName
                val label = activeShieldLabel ?: "Blocked App"
                val reason = activeShieldReason
                removeOverlay()
                if (!isTest) {
                    openMainAppShield(pkg, label, reason)
                }
            }
        }

        val secondaryBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(26).toFloat()
            setStroke(dp(1), Color.parseColor("#546B60"))
        }
        val homeButton = Button(this).apply {
            text = if (isTest) "Done" else "Go to Home Screen"
            isAllCaps = false
            setTextColor(Color.parseColor("#E3ECE7"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = secondaryBg
            minHeight = dp(48)
            setOnClickListener {
                if (!isTest) {
                    homeGracePackage = activeShieldPackage
                    homeGraceUntilMs = SystemClock.elapsedRealtime() + 850L
                    removeOverlay()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    removeOverlay()
                }
            }
        }

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
            returnButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        card.addView(
            homeButton,
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

    private fun openMainAppShield(blockedPackage: String, appLabel: String, reason: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(MainActivity.EXTRA_BLOCKED_APP_NAME, appLabel)
            putExtra(MainActivity.EXTRA_LOCK_BLOCKED_REASON, reason)
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
            "com.android.settings", // Only specific tamper screens inside Settings are blocked
            "com.miui.securitycenter" // Only specific tamper screens inside HyperOS Security are blocked
        )

        // Default home launcher so user is never trapped on home screen
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { set.add(it) }

            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .forEach { set.add(it) }
        }

        // Default input method (keyboard)
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
        const val REASON_BLOCKED_APP = "blocked_app"
        const val REASON_TAMPER = "tamper_attempt"
        private const val REASON_TEST_SHIELD = "test_shield"
        private const val ACTIVE_MONITOR_INTERVAL_MS = 650L

        @Volatile
        private var activeInstance: FocusBlockerAccessibilityService? = null

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

        /**
         * Returns true ONLY when the AccessibilityService is genuinely bound and running
         * (not marked as malfunctioning/crashed by Android).
         */
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

        /**
         * Returns true if the system Accessibility toggle is ON in Settings, even if
         * Android currently considers the service disconnected or malfunctioning.
         */
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
    }
}
