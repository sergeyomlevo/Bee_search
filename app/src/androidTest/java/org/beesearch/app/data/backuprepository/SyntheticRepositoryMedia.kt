package org.beesearch.app.data.backuprepository

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/** Small deterministic media inputs for repository device tests; not production media code. */
internal object SyntheticRepositoryMedia {
    private const val WIDTH = 256
    private const val HEIGHT = 256
    private const val FRAME_COUNT = 3
    private const val CODEC_TIMEOUT_US = 5_000L
    private const val DRAIN_TIMEOUT_MS = 10_000L

    fun createJpeg(file: File, color: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "JPEG compression failed" }
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun createMp4(file: File) {
        file.parentFile?.mkdirs()
        val codecName = findYuvEncoder() ?: error("No AVC YUV420 encoder is available")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 2)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var codecStarted = false
        var muxerStarted = false
        var track = -1
        fun drain(): Boolean {
            val info = MediaCodec.BufferInfo()
            while (true) {
                when (val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "AVC output format changed twice" }
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (index >= 0) {
                        val output = codec.getOutputBuffer(index)
                        if (info.size > 0 && output != null) {
                            check(muxerStarted) { "AVC data before output format" }
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(track, output, info)
                        }
                        val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        if (eos) return true
                    }
                }
            }
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            codecStarted = true
            repeat(FRAME_COUNT) { frame ->
                val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                check(index >= 0) { "Timed out waiting for AVC input buffer" }
                val input = codec.getInputBuffer(index) ?: error("AVC input buffer unavailable")
                fillYuv420(input, Color.rgb(32 + frame * 24, 96, 160))
                codec.queueInputBuffer(index, 0, input.position(), frame * 500_000L, 0)
                drain()
            }
            val eosIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
            check(eosIndex >= 0) { "Timed out waiting to signal AVC end of stream" }
            codec.queueInputBuffer(eosIndex, 0, 0, FRAME_COUNT * 500_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
            var eos = false
            while (!eos && System.currentTimeMillis() < deadline) eos = drain()
            check(eos && muxerStarted) { "Timed out draining AVC output" }
        } finally {
            try {
                if (codecStarted) codec.stop()
            } finally {
                codec.release()
                try {
                    if (muxerStarted) muxer.stop()
                } finally {
                    muxer.release()
                }
            }
        }
    }

    private fun findYuvEncoder(): String? = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
        info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) } &&
            info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities?.isSizeSupported(WIDTH, HEIGHT) == true &&
            info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.contains(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
    }?.name

    private fun fillYuv420(buffer: ByteBuffer, color: Int) {
        buffer.clear()
        val y = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt().coerceIn(0, 255)
        val u = (-0.169 * Color.red(color) - 0.331 * Color.green(color) + 0.5 * Color.blue(color) + 128).toInt().coerceIn(0, 255)
        val v = (0.5 * Color.red(color) - 0.419 * Color.green(color) - 0.081 * Color.blue(color) + 128).toInt().coerceIn(0, 255)
        repeat(WIDTH * HEIGHT) { buffer.put(y.toByte()) }
        repeat(WIDTH * HEIGHT / 4) { buffer.put(u.toByte()) }
        repeat(WIDTH * HEIGHT / 4) { buffer.put(v.toByte()) }
    }
}
