package com.example.automation

import android.graphics.Rect
import com.example.data.local.datastore.PandaSettings
import kotlin.math.abs

enum class AquaEventCategory(val code: String, val displayLabel: String) {
    PHONE_COMMUNICATION("PHONE", "Phone & Communication"),
    DEVICE_PERFORMANCE("PERFORMANCE", "Device Performance & Battery"),
    NETWORK("NETWORK", "Network & Connectivity"),
    DEVICE_INTERACTIONS("INTERACTIONS", "Device & Audio Interactions"),
    PANDANEX_SYSTEM("SYSTEM_ALERTS", "PandaNex System Alerts")
}

enum class AquaEventPriority(val rank: Int, val code: String, val displayLabel: String) {
    NORMAL(1, "NORMAL", "Standard"),
    IMPORTANT(2, "IMPORTANT", "Important"),
    CRITICAL(3, "CRITICAL", "Critical");

    companion object {
        fun meetsFilter(priority: AquaEventPriority, filterCode: String): Boolean {
            return when (filterCode.uppercase()) {
                "CRITICAL_ONLY" -> priority == CRITICAL
                "IMPORTANT_ONLY" -> priority.rank >= IMPORTANT.rank
                else -> true
            }
        }
    }
}

enum class AquaDisplayMode {
    HIDDEN,
    EXPANDED,
    MINIMIZED
}

enum class AquaActiveCallPhase(val isCallActive: Boolean, val label: String) {
    IDLE(false, "No Active Call"),
    INCOMING_RINGING(true, "Incoming Call Ringing"),
    OUTGOING_OR_ONGOING(true, "Call In Progress")
}

data class AquaDynamicsEvent(
    val id: String,
    val signatureKey: String,       // Identifies this condition instance for dismiss-lock & deduplication
    val conditionGroupKey: String,  // Identifies underlying condition family (e.g. "BATTERY_LOW", "WIFI_STATE")
    val category: AquaEventCategory,
    val priority: AquaEventPriority,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val iconType: String,           // CALL_MISSED, CALL_DONE, CPU, THERMAL, BATTERY, CHARGING, WIFI, NETWORK, ROTATION, AUDIO, SENSOR, ALERT
    val accentColorHex: Long = 0xFF00E5FFL,
    val timestampMs: Long = System.currentTimeMillis()
)

data class CameraSafeAnchorOffset(
    val topMarginPx: Int,
    val horizontalOffsetPx: Int,
    val avoidsCenterCutout: Boolean
)

data class AquaDynamicsEngineSnapshot(
    val isEnabled: Boolean = true,
    val displayMode: AquaDisplayMode = AquaDisplayMode.HIDDEN,
    val activeEvent: AquaDynamicsEvent? = null,
    val interactiveExpandFraction: Float = 0f, // 0f = compact minimized black capsule, 1f = full expanded card
    val callPhase: AquaActiveCallPhase = AquaActiveCallPhase.IDLE,
    val isSuppressedByCall: Boolean = false,
    val isSuppressedByFullScreen: Boolean = false,
    val dismissedSignatures: Set<String> = emptySet(),
    val recentEvents: List<AquaDynamicsEvent> = emptyList(),
    val statusSummary: String = "Standby — Waiting for real device events"
)

/**
 * Pure, deterministic state machine & gatekeeper for PandaNex Aqua Dynamics.
 *
 * Enforces:
 * 1. Active call suppression (incoming, outgoing, ongoing calls hide Aqua Dynamics immediately;
 *    never covers the system dialer; restores normal operation when call returns to IDLE).
 * 2. Zero unexpected startup/foreground activation (never emits on baseline initialization or app open).
 * 3. Strict dismissed-event lock (once dismissed, identical signature never reopens; only a genuinely
 *    new event signature or cleared-then-retriggered condition opens Aqua Dynamics).
 * 4. Deduplication, priority preemption, category toggles, priority filter, and configurable cooldowns.
 * 5. Smooth interactive drag progress (0f minimized black pill <-> 1f expanded card) with touch-slop
 *    guard against accidental scrolling activation.
 */
class AquaDynamicsGatekeeper(
    initialSettings: PandaSettings = PandaSettings()
) {
    private var settings: PandaSettings = initialSettings
    private val dismissedSignatures = LinkedHashSet<String>().apply {
        addAll(initialSettings.persistedDismissedSignatures)
    }
    private val lastFiredByConditionGroup = HashMap<String, Long>()
    private val lastSignatureByConditionGroup = HashMap<String, String>()
    private val recentHistory = ArrayDeque<AquaDynamicsEvent>()

    private var callPhase: AquaActiveCallPhase = AquaActiveCallPhase.IDLE
    private var fullScreenActive: Boolean = false
    private var displayMode: AquaDisplayMode = AquaDisplayMode.HIDDEN
    private var activeEvent: AquaDynamicsEvent? = null
    private var expandFraction: Float = 0f

    // Call lifecycle tracking for post-call summary & missed-call detection
    private var sawRingingInCurrentCall = false
    private var sawOffhookInCurrentCall = false
    private var offhookStartedAtMs: Long = 0L

    fun currentSnapshot(): AquaDynamicsEngineSnapshot {
        val suppressedByCall = callPhase.isCallActive
        val suppressedByFs = settings.hideAquaDynamicsInFullScreen && fullScreenActive
        val effectiveMode = if (!settings.aquaDynamicsEnabled || suppressedByCall || suppressedByFs || activeEvent == null) {
            AquaDisplayMode.HIDDEN
        } else {
            displayMode
        }

        val summary = when {
            !settings.aquaDynamicsEnabled -> "Disabled by user — Manual off state respected"
            suppressedByCall -> "Suppressed during active call (${callPhase.label})"
            suppressedByFs -> "Hidden while full-screen app/video is active"
            effectiveMode == AquaDisplayMode.EXPANDED && activeEvent != null ->
                "Expanded • ${activeEvent?.title}"
            effectiveMode == AquaDisplayMode.MINIMIZED && activeEvent != null ->
                "Minimized near camera • ${activeEvent?.title}"
            else -> "Standby — Ready for genuine device events"
        }

        return AquaDynamicsEngineSnapshot(
            isEnabled = settings.aquaDynamicsEnabled,
            displayMode = effectiveMode,
            activeEvent = if (effectiveMode == AquaDisplayMode.HIDDEN) null else activeEvent,
            interactiveExpandFraction = when (effectiveMode) {
                AquaDisplayMode.EXPANDED -> expandFraction.coerceIn(0f, 1f)
                AquaDisplayMode.MINIMIZED -> expandFraction.coerceIn(0f, 1f)
                AquaDisplayMode.HIDDEN -> 0f
            },
            callPhase = callPhase,
            isSuppressedByCall = suppressedByCall,
            isSuppressedByFullScreen = suppressedByFs,
            dismissedSignatures = dismissedSignatures.toSet(),
            recentEvents = recentHistory.toList(),
            statusSummary = summary
        )
    }

    @Synchronized
    fun updateSettings(newSettings: PandaSettings): AquaDynamicsEngineSnapshot {
        settings = newSettings
        if (newSettings.persistedDismissedSignatures.isNotEmpty()) {
            dismissedSignatures.addAll(newSettings.persistedDismissedSignatures)
            trimDismissedSet()
        }
        if (!newSettings.aquaDynamicsEnabled) {
            // Immediately close & clear active presentation when manually disabled
            displayMode = AquaDisplayMode.HIDDEN
            activeEvent = null
            expandFraction = 0f
        } else if (activeEvent != null && !isCategoryEnabled(activeEvent!!.category, newSettings)) {
            displayMode = AquaDisplayMode.HIDDEN
            activeEvent = null
            expandFraction = 0f
        }
        return currentSnapshot()
    }

    @Synchronized
    fun onFullScreenChanged(isFullScreen: Boolean): AquaDynamicsEngineSnapshot {
        fullScreenActive = isFullScreen
        if (settings.hideAquaDynamicsInFullScreen && isFullScreen) {
            // Hide active visual presentation while full screen is running
            if (displayMode == AquaDisplayMode.EXPANDED) {
                displayMode = AquaDisplayMode.MINIMIZED
                expandFraction = 0f
            }
        }
        return currentSnapshot()
    }

    /**
     * Handles incoming, outgoing, and ongoing call state transitions.
     * - Immediately hides any visible Aqua Dynamics overlay as soon as a call begins.
     * - Never displays Aqua Dynamics while a call is active.
     * - Restores normal readiness after the call ends, and optionally surfaces a Missed Call
     *   or Post-Call Summary event once the system call screen is no longer active.
     */
    @Synchronized
    fun onCallPhaseChanged(
        newPhase: AquaActiveCallPhase,
        nowMs: Long = System.currentTimeMillis()
    ): AquaDynamicsEngineSnapshot {
        val previous = callPhase
        if (previous == newPhase) return currentSnapshot()
        callPhase = newPhase

        when (newPhase) {
            AquaActiveCallPhase.INCOMING_RINGING -> {
                sawRingingInCurrentCall = true
                // Immediately hide any currently visible Aqua Dynamics overlay so call UI is untouched
                hideImmediatelyForCall()
            }

            AquaActiveCallPhase.OUTGOING_OR_ONGOING -> {
                if (!sawOffhookInCurrentCall) {
                    sawOffhookInCurrentCall = true
                    offhookStartedAtMs = nowMs
                }
                hideImmediatelyForCall()
            }

            AquaActiveCallPhase.IDLE -> {
                val wasRingingOnly = sawRingingInCurrentCall && !sawOffhookInCurrentCall
                val wasAnsweredOrOutgoing = sawOffhookInCurrentCall
                val durationSec = if (offhookStartedAtMs > 0L) {
                    ((nowMs - offhookStartedAtMs) / 1000L).coerceAtLeast(1L)
                } else 0L

                sawRingingInCurrentCall = false
                sawOffhookInCurrentCall = false
                offhookStartedAtMs = 0L

                // Call has ended — normal Aqua Dynamics operation is restored.
                if (wasRingingOnly && settings.aquaCatPhoneEnabled) {
                    submitEventInternal(
                        AquaDynamicsEvent(
                            id = "missed_call_$nowMs",
                            signatureKey = "CALL_MISSED_${nowMs / 3000L}",
                            conditionGroupKey = "CALL_MISSED",
                            category = AquaEventCategory.PHONE_COMMUNICATION,
                            priority = AquaEventPriority.IMPORTANT,
                            title = "Missed Call",
                            subtitle = "Incoming call ended unanswered",
                            badgeText = "MISSED",
                            iconType = "CALL_MISSED",
                            accentColorHex = 0xFFFFB300L,
                            timestampMs = nowMs
                        ),
                        nowMs = nowMs,
                        bypassCooldown = true
                    )
                } else if (wasAnsweredOrOutgoing && settings.aquaCatPhoneEnabled && settings.aquaPostCallSummaryEnabled) {
                    val mins = durationSec / 60L
                    val secs = durationSec % 60L
                    val durLabel = if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
                    submitEventInternal(
                        AquaDynamicsEvent(
                            id = "call_ended_$nowMs",
                            signatureKey = "CALL_ENDED_${nowMs / 3000L}",
                            conditionGroupKey = "CALL_ENDED",
                            category = AquaEventCategory.PHONE_COMMUNICATION,
                            priority = AquaEventPriority.NORMAL,
                            title = "Call Ended",
                            subtitle = "Duration $durLabel • Audio & sensors restored",
                            badgeText = durLabel,
                            iconType = "CALL_DONE",
                            accentColorHex = 0xFF00E5FFL,
                            timestampMs = nowMs
                        ),
                        nowMs = nowMs,
                        bypassCooldown = true
                    )
                }
            }
        }
        return currentSnapshot()
    }

    private fun hideImmediatelyForCall() {
        displayMode = AquaDisplayMode.HIDDEN
        activeEvent = null
        expandFraction = 0f
    }

    /**
     * Marks an underlying condition (e.g., "BATTERY_LOW" or "CPU_OVERLOAD") as cleared/recovered
     * so that if the condition genuinely happens again later, its signature can trigger anew.
     */
    @Synchronized
    fun markConditionCleared(conditionGroupKey: String) {
        val prevSig = lastSignatureByConditionGroup.remove(conditionGroupKey)
        if (prevSig != null) {
            dismissedSignatures.remove(prevSig)
        }
    }

    /**
     * Evaluates and submits a real device event to Aqua Dynamics.
     * Returns true if accepted & displayed, false if suppressed/filtered/deduplicated.
     */
    @Synchronized
    fun submitEvent(
        event: AquaDynamicsEvent,
        nowMs: Long = event.timestampMs,
        isUserPreview: Boolean = false
    ): Boolean {
        return submitEventInternal(
            event = event,
            nowMs = nowMs,
            bypassCooldown = isUserPreview,
            allowWhenDisabled = isUserPreview
        )
    }

    private fun submitEventInternal(
        event: AquaDynamicsEvent,
        nowMs: Long,
        bypassCooldown: Boolean = false,
        allowWhenDisabled: Boolean = false
    ): Boolean {
        // 1. Respect manual disabled state strictly
        if (!settings.aquaDynamicsEnabled && !allowWhenDisabled) return false

        // 2. NEVER display during an active incoming, outgoing, or ongoing phone call
        if (callPhase.isCallActive) return false

        // 3. Respect Full-Screen video/game suppression
        if (settings.hideAquaDynamicsInFullScreen && fullScreenActive && !allowWhenDisabled) return false

        // 4. Category & Priority filter checks
        if (!allowWhenDisabled) {
            if (!isCategoryEnabled(event.category, settings)) return false
            if (!AquaEventPriority.meetsFilter(event.priority, settings.aquaPriorityFilter)) return false
        }

        // 5. Never reopen a dismissed event with the same signature
        if (settings.aquaStrictDismissLock && dismissedSignatures.contains(event.signatureKey) && !allowWhenDisabled) {
            return false
        }

        // 6. Deduplicate identical currently visible signature (no repeated flashing)
        val current = activeEvent
        if (current != null && current.signatureKey == event.signatureKey && displayMode != AquaDisplayMode.HIDDEN && !allowWhenDisabled) {
            return false
        }

        // 7. Cooldown enforcement (Critical priority preempts ordinary cooldown if signature is new)
        if (!bypassCooldown) {
            val prevGroupTime = lastFiredByConditionGroup[event.conditionGroupKey]
            val prevSig = lastSignatureByConditionGroup[event.conditionGroupKey]
            val cooldown = settings.aquaEventCooldownMs.coerceAtLeast(2000L)

            if (prevGroupTime != null) {
                if (prevSig == event.signatureKey && (nowMs - prevGroupTime) < cooldown * 3) {
                    return false
                }
                if ((nowMs - prevGroupTime) < cooldown && event.priority != AquaEventPriority.CRITICAL) {
                    return false
                }
            }
            // Do not let a low-priority event overwrite an actively expanded CRITICAL alert within 4 seconds
            if (current != null &&
                displayMode == AquaDisplayMode.EXPANDED &&
                current.priority == AquaEventPriority.CRITICAL &&
                event.priority.rank < AquaEventPriority.CRITICAL.rank &&
                (nowMs - current.timestampMs) < 4500L
            ) {
                return false
            }
        }

        lastFiredByConditionGroup[event.conditionGroupKey] = nowMs
        lastSignatureByConditionGroup[event.conditionGroupKey] = event.signatureKey

        activeEvent = event.copy(timestampMs = nowMs)
        displayMode = AquaDisplayMode.EXPANDED
        expandFraction = 1f

        recentHistory.addFirst(activeEvent!!)
        while (recentHistory.size > 20) {
            recentHistory.removeLast()
        }
        return true
    }

    /**
     * Interactive vertical drag handler:
     * - Dragging upward toward camera (negative deltaY) smoothly collapses Expanded (1f) -> Minimized (0f).
     * - Dragging downward away from camera (positive deltaY) smoothly expands Minimized (0f) -> Expanded (1f).
     * - Ignores micro-movements smaller than touchSlopPx to prevent accidental triggers while scrolling.
     */
    @Synchronized
    fun onInteractiveVerticalDrag(
        totalDragDyPx: Float,
        fullTravelPx: Float = 130f,
        touchSlopPx: Float = 14f
    ): AquaDynamicsEngineSnapshot {
        if (activeEvent == null || callPhase.isCallActive) return currentSnapshot()
        if (abs(totalDragDyPx) < touchSlopPx) return currentSnapshot()

        val safeTravel = fullTravelPx.coerceAtLeast(48f)
        val effectiveDy = if (totalDragDyPx > 0f) totalDragDyPx - touchSlopPx else totalDragDyPx + touchSlopPx

        val base = when (displayMode) {
            AquaDisplayMode.EXPANDED -> 1f
            AquaDisplayMode.MINIMIZED -> 0f
            AquaDisplayMode.HIDDEN -> return currentSnapshot()
        }

        expandFraction = (base + (effectiveDy / safeTravel)).coerceIn(0f, 1f)
        return currentSnapshot()
    }

    /**
     * Settles an interactive drag gesture smoothly into MINIMIZED or EXPANDED based on progress & velocity.
     */
    @Synchronized
    fun settleInteractiveDrag(velocityY: Float = 0f): AquaDynamicsEngineSnapshot {
        if (activeEvent == null || displayMode == AquaDisplayMode.HIDDEN) return currentSnapshot()

        val shouldMinimize = when {
            velocityY < -420f -> true
            velocityY > 420f -> false
            displayMode == AquaDisplayMode.EXPANDED -> expandFraction < 0.68f
            else -> expandFraction < 0.35f
        }

        if (shouldMinimize) {
            displayMode = AquaDisplayMode.MINIMIZED
            expandFraction = 0f
        } else {
            displayMode = AquaDisplayMode.EXPANDED
            expandFraction = 1f
        }
        return currentSnapshot()
    }

    @Synchronized
    fun minimizeToCameraArea(): AquaDynamicsEngineSnapshot {
        if (activeEvent == null || displayMode == AquaDisplayMode.HIDDEN) return currentSnapshot()
        displayMode = AquaDisplayMode.MINIMIZED
        expandFraction = 0f
        return currentSnapshot()
    }

    @Synchronized
    fun expandFromMinimized(): AquaDynamicsEngineSnapshot {
        if (activeEvent == null || callPhase.isCallActive) return currentSnapshot()
        if (settings.hideAquaDynamicsInFullScreen && fullScreenActive) return currentSnapshot()
        displayMode = AquaDisplayMode.EXPANDED
        expandFraction = 1f
        return currentSnapshot()
    }

    /**
     * Dismisses the current Aqua Dynamics capsule and locks its signature so it never reopens
     * unless a genuinely new event occurs.
     */
    @Synchronized
    fun dismissActiveEvent(): Pair<AquaDynamicsEngineSnapshot, Set<String>> {
        val current = activeEvent
        if (current != null) {
            dismissedSignatures.add(current.signatureKey)
            trimDismissedSet()
        }
        displayMode = AquaDisplayMode.HIDDEN
        activeEvent = null
        expandFraction = 0f
        return currentSnapshot() to dismissedSignatures.toSet()
    }

    fun isCategoryEnabled(category: AquaEventCategory, s: PandaSettings = settings): Boolean {
        return when (category) {
            AquaEventCategory.PHONE_COMMUNICATION -> s.aquaCatPhoneEnabled
            AquaEventCategory.DEVICE_PERFORMANCE -> s.aquaCatPerformanceEnabled
            AquaEventCategory.NETWORK -> s.aquaCatNetworkEnabled
            AquaEventCategory.DEVICE_INTERACTIONS -> s.aquaCatInteractionsEnabled
            AquaEventCategory.PANDANEX_SYSTEM -> s.aquaCatSystemAlertsEnabled
        }
    }

    private fun trimDismissedSet() {
        while (dismissedSignatures.size > 40) {
            val first = dismissedSignatures.firstOrNull() ?: break
            dismissedSignatures.remove(first)
        }
    }

    companion object {
        /**
         * Computes safe top Y margin (px) so Aqua Dynamics floats cleanly around/below the front
         * camera cutout without covering or obstructing physical camera pixels.
         */
        fun computeCameraSafeAnchor(
            screenWidthPx: Int,
            cutoutRects: List<Rect>,
            safeInsetTopPx: Int,
            density: Float
        ): CameraSafeAnchorOffset {
            val minPaddingPx = (6f * density).toInt().coerceAtLeast(4)
            val centerX = screenWidthPx / 2
            val centerCutout = cutoutRects.firstOrNull { rect ->
                rect.top <= (48 * density).toInt() &&
                    rect.left <= (centerX + (44 * density).toInt()) &&
                    rect.right >= (centerX - (44 * density).toInt())
            }

            return if (centerCutout != null) {
                // Position cleanly below the bottom edge of the camera cutout so lens is 100% unobstructed
                val safeTop = (centerCutout.bottom + minPaddingPx).coerceAtLeast(safeInsetTopPx + minPaddingPx)
                CameraSafeAnchorOffset(
                    topMarginPx = safeTop,
                    horizontalOffsetPx = 0,
                    avoidsCenterCutout = true
                )
            } else {
                val baseTop = if (safeInsetTopPx > 0) {
                    safeInsetTopPx + minPaddingPx
                } else {
                    (10f * density).toInt()
                }
                CameraSafeAnchorOffset(
                    topMarginPx = baseTop,
                    horizontalOffsetPx = 0,
                    avoidsCenterCutout = false
                )
            }
        }
    }
}
