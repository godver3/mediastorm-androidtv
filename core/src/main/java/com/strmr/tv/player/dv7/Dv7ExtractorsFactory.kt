package com.strmr.tv.player.dv7

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor
import com.strmr.tv.player.dv7.mkv.MatroskaExtractor as DvMatroskaExtractor

/**
 * The default extractors with Profile 7 video rewritten per [mode]: Matroska through the
 * vendored extractor (its RPU can ride in BlockAdditional, which the stock one drops), MP4 and
 * TS by wrapping the video track's output. Tracks that aren't Profile 7 pass through.
 */
@OptIn(UnstableApi::class)
internal class Dv7ExtractorsFactory(
    private val delegate: ExtractorsFactory,
    private val mode: Dv7Mode,
) : ExtractorsFactory {
    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var transcodeTextTracks = true

    // Passed on, so the delegate and the Matroska extractor handle subtitles as configured.
    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): ExtractorsFactory {
        subtitleParserFactory = factory
        delegate.setSubtitleParserFactory(factory)
        return this
    }

    @Deprecated("Media3 experimental API")
    override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory {
        transcodeTextTracks = enabled
        @Suppress("DEPRECATION")
        delegate.experimentalSetTextTrackTranscodingEnabled(enabled)
        return this
    }

    override fun createExtractors(): Array<Extractor> = wrapAll(delegate.createExtractors())

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        wrapAll(delegate.createExtractors(uri, responseHeaders))

    private fun wrapAll(extractors: Array<Extractor>): Array<Extractor> =
        extractors.map(::wrap).toTypedArray()

    private fun wrap(extractor: Extractor): Extractor = when (extractor.underlyingImplementation) {
        is MatroskaExtractor -> DvMatroskaExtractor(
            subtitleParserFactory,
            if (transcodeTextTracks) 0 else DvMatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA,
            Dv7MatroskaTransformer(mode),
        )
        is Mp4Extractor, is FragmentedMp4Extractor -> RewritingExtractor(extractor, annexB = false, mode)
        is TsExtractor -> RewritingExtractor(extractor, annexB = true, mode)
        else -> extractor
    }
}

/** An extractor whose video tracks go through [Dv7TrackOutput]. */
@OptIn(UnstableApi::class)
private class RewritingExtractor(
    private val delegate: Extractor,
    private val annexB: Boolean,
    private val mode: Dv7Mode,
) : Extractor {
    override fun init(output: ExtractorOutput) = delegate.init(object : ExtractorOutput {
        override fun track(id: Int, type: Int): TrackOutput {
            val track = output.track(id, type)
            return if (type == C.TRACK_TYPE_VIDEO) Dv7TrackOutput(track, annexB, mode) else track
        }

        override fun endTracks() = output.endTracks()

        override fun seekMap(seekMap: SeekMap) = output.seekMap(seekMap)
    })

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)

    override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)

    override fun release() = delegate.release()

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
}
