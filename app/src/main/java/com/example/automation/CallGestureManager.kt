package com.example.automation

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

data class CallGestureRuntimeState(
    val hasReadPhoneStatePermission: Boolean = false,
    val hasAnswerPhoneCallsPermission: Boolean = false,
    val isCallRinging: Boolean = false,
    val isCallActiveOrOffhook: Boolean = false,
    val externalHeadsetOrCarConnected: Boolean = false,
    val externalRouteDescription: String = "",
    val silencedCurrentCall: Boolean = false,
    val answeredCurrentCall: Boolean = false,
    val lastCallGestureStatus: String = "Standby — Waiting for incoming call"
)

/**
 * Manages incoming-call state detection, Bluetooth/Car audio conflict protection,
 * Pick Up to Silence (ringtone mute without declining), and Proximity Call Answer
 * using official Android TelecomManager & AudioManager APIs.
 */
class CallGestureManager(
    private val context: Context,
    private val onRingingStateChanged: (isRinging: Boolean, isAnyCallActive: Boolean) -> Unit
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager

    private var listenerRegistered = false
    private var modernCallback: Any? = null
    private var mutedRingStreamForCurrentCall = false

    @Volatile
    var runtimeState = CallGestureRuntimeState()
        private set

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class ModernCallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            processCallState(state)
        }
    }

    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            processCallState(state)
        }
    }

    fun hasReadPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAnswerPhoneCallsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun syncCallStateRegistration(shouldMonitorCalls: Boolean) {
        val readGranted = hasReadPhoneStatePermission()
        val answerGranted = hasAnswerPhoneCallsPermission()
        val (extActive, extLabel) = inspectExternalCallAudioRoute()

        runtimeState = runtimeState.copy(
            hasReadPhoneStatePermission = readGranted,
            hasAnswerPhoneCallsPermission = answerGranted,
            externalHeadsetOrCarConnected = extActive,
            externalRouteDescription = extLabel
        )

        val wantRegistered = shouldMonitorCalls && readGranted
        if (wantRegistered && !listenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cb = ModernCallStateCallback()
                    modernCallback = cb
                    telephonyManager?.registerTelephonyCallback(context.mainExecutor, cb)
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
                }
                listenerRegistered = true
            }
        } else if (!wantRegistered && listenerRegistered) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (modernCallback as? TelephonyCallback)?.let {
                        telephonyManager?.unregisterTelephonyCallback(it)
                    }
                    modernCallback = null
                } else {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
            listenerRegistered = false
            restoreMutedRingStreamIfNeeded()
        }
    }

    /**
     * Checks whether Bluetooth headset, wired/USB headset, or Android Auto is active so
     * Proximity Call Answer never interferes with hands-free or in-car call routing.
     */
    fun inspectExternalCallAudioRoute(): Pair<Boolean, String> {
        val inCarMode = runCatching {
            uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        }.getOrDefault(false)
        if (inCarMode) {
            return true to "Android Auto / Car Mode"
        }

        val outputs = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        for (dev in outputs) {
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return true to "Bluetooth Headset (SCO)"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    // Only treat A2DP as call-capable if SCO/BLE headset is also paired or mode is ringtone
                    if (outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
                        return true to "Bluetooth Headset"
                    }
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> return true to "Wired Headset"
                AudioDeviceInfo.TYPE_USB_HEADSET -> return true to "USB Headset"
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_HEARING_AID)
                    ) {
                        return true to "BLE Headset / Hearing Aid"
                    }
                }
            }
        }
        return false to ""
    }

    /**
     * Updates incoming/active call state from OS Telephony callbacks (or deterministic unit tests).
     */
    fun processCallState(callState: Int) {
        val ringing = callState == TelephonyManager.CALL_STATE_RINGING
        val offhook = callState == TelephonyManager.CALL_STATE_OFFHOOK
        val anyCall = ringing || offhook
        val (extActive, extLabel) = inspectExternalCallAudioRoute()

        if (!ringing && !offhook) {
            restoreMutedRingStreamIfNeeded()
        }

        runtimeState = runtimeState.copy(
            hasReadPhoneStatePermission = hasReadPhoneStatePermission(),
            hasAnswerPhoneCallsPermission = hasAnswerPhoneCallsPermission(),
            isCallRinging = ringing,
            isCallActiveOrOffhook = anyCall,
            externalHeadsetOrCarConnected = extActive,
            externalRouteDescription = extLabel,
            silencedCurrentCall = if (ringing) runtimeState.silencedCurrentCall else false,
            answeredCurrentCall = if (anyCall) runtimeState.answeredCurrentCall else false,
            lastCallGestureStatus = when {
                ringing && extActive -> "Incoming call • Proximity Answer paused ($extLabel)"
                ringing -> "Incoming call ringing • Call gestures armed"
                offhook -> "Call active • Motion gestures paused until call ends"
                else -> "Standby — Waiting for incoming call"
            }
        )

        onRingingStateChanged(ringing, anyCall)
    }

    /**
     * Silences an incoming ringing call without rejecting, declining, or disconnecting it.
     */
    fun silenceIncomingCallRinger(): Pair<Boolean, String> {
        if (!runtimeState.isCallRinging && audioManager?.mode != AudioManager.MODE_RINGTONE) {
            return false to "Skipped: No incoming call is currently ringing"
        }
        if (runtimeState.silencedCurrentCall) {
            return false to "Ringtone already silenced for this incoming call"
        }

        val silencedViaAudioStream = runCatching {
            val am = audioManager
            if (am != null && !am.isStreamMute(AudioManager.STREAM_RING)) {
                am.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                mutedRingStreamForCurrentCall = true
            }
            am != null
        }.getOrDefault(false)

        val detail = if (silencedViaAudioStream) {
            "Ringtone silenced via AudioManager (incoming call kept active)"
        } else {
            "Unable to silence ringtone (restricted by OEM audio policy)"
        }

        runtimeState = runtimeState.copy(
            silencedCurrentCall = silencedViaAudioStream,
            lastCallGestureStatus = detail
        )
        return silencedViaAudioStream to detail
    }

    /**
     * Answers an incoming ringing call using Android's official TelecomManager.acceptRingingCall() API.
     * Never uses hidden APIs or bypasses permissions.
     */
    @Suppress("DEPRECATION")
    fun answerIncomingRingingCall(): Pair<Boolean, String> {
        if (!runtimeState.isCallRinging && audioManager?.mode != AudioManager.MODE_RINGTONE) {
            return false to "Skipped: No incoming call is currently ringing"
        }
        if (runtimeState.answeredCurrentCall) {
            return false to "Incoming call already answered"
        }

        val (extActive, extLabel) = inspectExternalCallAudioRoute()
        if (extActive) {
            val msg = "Proximity Answer skipped ($extLabel connected)"
            runtimeState = runtimeState.copy(
                externalHeadsetOrCarConnected = true,
                externalRouteDescription = extLabel,
                lastCallGestureStatus = msg
            )
            return false to msg
        }

        if (!hasAnswerPhoneCallsPermission()) {
            val msg = "Answer Phone Calls permission required for Proximity Call Answer"
            runtimeState = runtimeState.copy(lastCallGestureStatus = msg)
            return false to msg
        }

        val tm = telecomManager ?: return false to "TelecomManager unavailable on this device"

        return try {
            tm.acceptRingingCall()
            val msg = "Incoming call answered via Proximity Near-Ear gesture"
            runtimeState = runtimeState.copy(
                answeredCurrentCall = true,
                lastCallGestureStatus = msg
            )
            true to msg
        } catch (se: SecurityException) {
            val msg = "Restricted by Android/OEM Dialer Policy (${se.javaClass.simpleName}) — answer on screen"
            runtimeState = runtimeState.copy(lastCallGestureStatus = msg)
            false to msg
        } catch (e: Exception) {
            val msg = "Call answer unsupported for this call type (${e.javaClass.simpleName})"
            runtimeState = runtimeState.copy(lastCallGestureStatus = msg)
            false to msg
        }
    }

    private fun restoreMutedRingStreamIfNeeded() {
        if (!mutedRingStreamForCurrentCall) return
        mutedRingStreamForCurrentCall = false
        runCatching {
            audioManager?.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
        }
    }
}
