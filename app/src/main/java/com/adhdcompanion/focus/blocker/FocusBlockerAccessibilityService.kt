package com.adhdcompanion.focus.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.adhdcompanion.focus.MainActivity

/**
 * Detects foreground app switches during active scheduled focus lock windows
 * and immediately redirects back to Focus Companion's lock shield when a
 * distracting app opens — or when Settings/App Info/Uninstall tampering is
 * attempted against Focus Companion mid-session.
 */
class FocusBlockerAccessibilityService : AccessibilityService() {

    private var lastContentCheckMs = 0L
    private var lastRedirectMs = 0L
    private var lastRedirectedPackage: String? = null
    private var cachedEssentialPackages: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        cachedEssentialPackages = buildEssentialPackages()
        FocusLockManager.getInstance(this).evaluateAndSyncState(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val lockManager = FocusLockManager.getInstance(this)
        val eval = lockManager.evaluateNow()
        if (!eval.isBlockingActive) return

        val now = SystemClock.elapsedRealtime()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheckMs < 200L) return
            lastContentCheckMs = now
        }

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (cachedEssentialPackages.isEmpty()) {
            cachedEssentialPackages = buildEssentialPackages()
        }

        // 1. Guard against mid-session App Info / Uninstall / Accessibility disable tampering
        val screenTokens = mutableListOf<String>()
        event.text?.forEach { cs ->
            val str = cs?.toString()?.trim()
            if (!str.isNullOrEmpty()) screenTokens.add(str)
        }
        if (pkg == "com.android.settings" || pkg.contains("packageinstaller")) {
            collectNodeTexts(rootInActiveWindow, screenTokens, maxTokens = 45, depth = 0)
        }

        if (lockManager.isTamperAttempt(pkg, event.className?.toString(), screenTokens)) {
            redirectToShield(
                blockedPackage = pkg,
                appLabel = "Device Settings / App Info",
                reason = REASON_TAMPER,
                performHomeFirst = false
            )
            return
        }

        // 2. Block distracting foreground apps (or non-allowlisted apps in allow-list mode)
        if (lockManager.isPackageBlockedNow(pkg, cachedEssentialPackages)) {
            val label = lockManager.getAppLabel(pkg)
            redirectToShield(
                blockedPackage = pkg,
                appLabel = label,
                reason = REASON_BLOCKED_APP,
                performHomeFirst = true
            )
        }
    }

    override fun onInterrupt() {
        // No ongoing resources to tear down
    }

    private fun redirectToShield(
        blockedPackage: String,
        appLabel: String,
        reason: String,
        performHomeFirst: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        if (blockedPackage == lastRedirectedPackage && now - lastRedirectMs < 450L) {
            return
        }
        lastRedirectedPackage = blockedPackage
        lastRedirectMs = now

        FocusLockManager.getInstance(this).recordBlockedAttempt(
            packageName = blockedPackage,
            appLabel = appLabel
        )

        if (performHomeFirst) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(MainActivity.EXTRA_BLOCKED_APP_NAME, appLabel)
            putExtra(MainActivity.EXTRA_LOCK_BLOCKED_REASON, reason)
        }
        startActivity(intent)
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
            "com.android.settings" // Only specific tamper screens inside Settings are blocked
        )

        // Default home launcher so user is never trapped on home screen
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let { set.add(it) }

        packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .forEach { set.add(it) }

        // Default input method (keyboard)
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ime?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { set.add(it) }

        return set
    }

    private fun collectNodeTexts(
        node: AccessibilityNodeInfo?,
        out: MutableList<String>,
        maxTokens: Int,
        depth: Int
    ) {
        if (node == null || out.size >= maxTokens || depth > 8) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            if (out.size >= maxTokens) break
            collectNodeTexts(node.getChild(i), out, maxTokens, depth + 1)
        }
    }

    companion object {
        const val REASON_BLOCKED_APP = "blocked_app"
        const val REASON_TAMPER = "tamper_attempt"

        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, FocusBlockerAccessibilityService::class.java)
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledServices.any {
                    it.resolveInfo?.serviceInfo?.let { si ->
                        si.packageName == expectedComponent.packageName && si.name == expectedComponent.className
                    } == true
                }
            ) {
                return true
            }

            val enabledSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val flat = expectedComponent.flattenToString()
            val shortFlat = expectedComponent.flattenToShortString()
            return enabledSetting.split(':').any {
                it.equals(flat, ignoreCase = true) || it.equals(shortFlat, ignoreCase = true)
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
