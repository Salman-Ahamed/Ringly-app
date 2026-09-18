package com.ringly.app.call

import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

interface InCallActions {
    fun answer()
    fun reject()
    fun disconnect()
    fun setMute(muted: Boolean)
    fun setSpeaker(enabled: Boolean)
}

object NoOpInCallActions : InCallActions {
    override fun answer() = Unit
    override fun reject() = Unit
    override fun disconnect() = Unit
    override fun setMute(muted: Boolean) = Unit
    override fun setSpeaker(enabled: Boolean) = Unit
}

class TelecomCallActions(
    private val call: Call?,
    private val service: InCallService?
) : InCallActions {

    override fun answer() {
        runCatching { call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    }

    override fun reject() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call?.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call?.let { LegacyCallControl.invokeVoid(it, "reject", Boolean::class.javaPrimitiveType!!, false) }
            }
        }
    }

    override fun disconnect() {
        runCatching { call?.disconnect() }
    }

    override fun setMute(muted: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                service?.setMuted(muted)
            } else {
                call?.let { LegacyCallControl.invokeVoid(it, "setMute", Boolean::class.javaPrimitiveType!!, muted) }
            }
        }
    }

    override fun setSpeaker(enabled: Boolean) {
        val route = if (enabled) AudioManager.ROUTE_SPEAKER else AudioManager.ROUTE_EARPIECE
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                service?.setAudioRoute(route)
            } else {
                call?.let { LegacyCallControl.invokeVoid(it, "setAudioRoute", Int::class.javaPrimitiveType!!, route) }
            }
        }
    }
}

private object LegacyCallControl {

    fun invokeVoid(target: Any, methodName: String, paramType: Class<*>, value: Any) {
        runCatching {
            val method = target.javaClass.getMethod(methodName, paramType)
            method.isAccessible = true
            method.invoke(target, value)
        }
    }
}