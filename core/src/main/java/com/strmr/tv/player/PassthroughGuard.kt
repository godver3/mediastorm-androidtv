package com.strmr.tv.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatConfig
import androidx.media3.exoplayer.audio.AudioOutputProvider.FormatSupport
import androidx.media3.exoplayer.audio.AudioOutputProvider.OutputConfig
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import com.strmr.tv.app.AppLog
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Keeps bitstream passthrough (Dolby, DTS) off outputs that can't take it, so the audio is
 * decoded to PCM instead of stalling playback on an AudioTrack that never opens.
 *
 * On Android 13+ TVs Media3 trusts the platform's direct-playback profiles and skips its own
 * Bluetooth check; a box with surround formats forced on (Google TV Streamer) then offers
 * E-AC3 while the sound goes to a Bluetooth soundbar. Bluetooth routes get PCM only, and an
 * encoding whose passthrough track fails to open is dropped for that route for the rest of the
 * process; either way the renderer is told and the track is reselected onto a decoder.
 */
@OptIn(UnstableApi::class)
class PassthroughGuard(context: Context, delegate: AudioOutputProvider) : ForwardingAudioOutputProvider(delegate) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val listeners = CopyOnWriteArrayList<AudioOutputProvider.Listener>()

    override fun getFormatSupport(formatConfig: FormatConfig): FormatSupport {
        if (isEncoded(formatConfig)) {
            val route = routedDevice(formatConfig)
            if (route != null && route.isBluetooth()) return FormatSupport.UNSUPPORTED
            val encoding = MimeTypes.getEncoding(formatConfig.format.sampleMimeType ?: "", formatConfig.format.codecs)
            if (failed.contains(key(encoding, route))) return FormatSupport.UNSUPPORTED
        }
        return super.getFormatSupport(formatConfig)
    }

    override fun getAudioOutput(config: OutputConfig): AudioOutput {
        try {
            return super.getAudioOutput(config)
        } catch (e: AudioOutputProvider.InitializationException) {
            if (isPcm(config.encoding) || config.isOffload) throw e
            val route = routedDevice(null)
            if (failed.add(key(config.encoding, route))) {
                AppLog.w("Player", "passthrough encoding=${config.encoding} channels=${Integer.bitCount(config.channelMask)} " +
                    "failed on ${route?.productName ?: "default route"}; decoding instead")
                listeners.forEach { it.onFormatSupportChanged() }
            }
            throw e
        }
    }

    override fun addListener(listener: AudioOutputProvider.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: AudioOutputProvider.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    private fun isEncoded(config: FormatConfig) = config.format.sampleMimeType != MimeTypes.AUDIO_RAW

    private fun isPcm(encoding: Int) = androidx.media3.common.util.Util.isEncodingLinearPcm(encoding)

    /** Where media audio goes now (Android 13+; earlier Media3 checks Bluetooth itself). */
    private fun routedDevice(config: FormatConfig?): AudioDeviceInfo? {
        config?.preferredDevice?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val attributes = (config?.audioAttributes ?: androidx.media3.common.AudioAttributes.DEFAULT).platformAudioAttributes
        return audioManager.getAudioDevicesForAttributes(attributes).firstOrNull()
    }

    private fun AudioDeviceInfo.isBluetooth() = type in BluetoothTypes

    companion object {
        /** Encodings whose passthrough failed, per output route, for this process. */
        private val failed: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

        private fun key(encoding: Int, route: AudioDeviceInfo?) = "$encoding@${route?.type}:${route?.address}"

        private val BluetoothTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }

        /** [DefaultRenderersFactory] whose audio sink goes through [PassthroughGuard]. */
        fun renderersFactory(context: Context): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                    .setAudioOutputProvider(PassthroughGuard(context, AudioTrackAudioOutputProvider.Builder(context).build()))
                    .build()
        }
    }
}
