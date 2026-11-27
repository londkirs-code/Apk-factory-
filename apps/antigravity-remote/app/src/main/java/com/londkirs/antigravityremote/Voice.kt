package com.londkirs.antigravityremote

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Voice messages. The model cannot hear audio, so a recording is also transcribed and the text is
 * what gets sent. On Android 13+ the same microphone stream is saved as a WAV file (playable in the
 * bubble) and fed to the speech recognizer. On older Android the recognizer uses the microphone
 * itself, so only the transcript is kept.
 */
class VoiceRecorder(private val ctx: Context, private val listener: Listener) {
    interface Listener {
        fun onLevel(level: Float)
        fun onPartial(text: String)
        fun onFinished(file: File?, transcript: String, durationMs: Long)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var recognizer: SpeechRecognizer? = null
    private var pipeWrite: ParcelFileDescriptor? = null
    private var file: File? = null
    private var started = 0L
    private var transcript = ""
    private var partial = ""
    private var stopped = false
    private var cancelled = false
    private var finalReceived = false
    private var audioDone = false

    val savesAudio get() = Build.VERSION.SDK_INT >= 33

    fun start() {
        started = System.currentTimeMillis()
        if (savesAudio) startWithAudio() else startRecognizerOnly()
    }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60_000L)

    private fun makeRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return null
        return SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if (!savesAudio) listener.onLevel(((rmsdB + 2) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Keep whatever was recognised so far; recording itself may still be fine.
                    if (transcript.isBlank()) transcript = partial
                    finalReceived = true
                    if (!savesAudio && !stopped && transcript.isBlank() && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        listener.onError(ctx.tr("זיהוי הדיבור לא זמין במכשיר", "Speech recognition is not available"))
                    }
                    maybeFinish()
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript = listOf(transcript, t).filter { it.isNotBlank() }.joinToString(" ").ifBlank { partial }
                    finalReceived = true
                    maybeFinish()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    partial = t; listener.onPartial(t)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startRecognizerOnly() {
        recognizer = makeRecognizer()
        if (recognizer == null) { listener.onError(ctx.tr("זיהוי הדיבור לא זמין במכשיר", "Speech recognition is not available")); return }
        audioDone = true
        recognizer!!.startListening(recognizerIntent())
    }

    private fun startWithAudio() {
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { listener.onError(ctx.tr("אין גישה למיקרופון", "Microphone unavailable")); return }
        record = rec
        val dir = File(ctx.filesDir, "voice").apply { mkdirs() }
        val f = File(dir, "voice_${System.currentTimeMillis()}.wav")
        file = f
        var pipeOut: FileOutputStream? = null
        recognizer = makeRecognizer()
        if (recognizer != null && Build.VERSION.SDK_INT >= 33) {
            val pipe = ParcelFileDescriptor.createPipe()
            pipeWrite = pipe[1]
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            val intent = recognizerIntent()
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, rate)
            runCatching { recognizer!!.startListening(intent) }.onFailure { pipeOut = null; finalReceived = true }
        } else finalReceived = true
        running = true
        rec.startRecording()
        val out = pipeOut
        thread = Thread {
            val buf = ShortArray(rate / 10)
            val bytes = ByteArray(buf.size * 2)
            val wav = RandomAccessFile(f, "rw")
            wav.setLength(0); wav.write(ByteArray(44))
            var total = 0
            var pipeOk = out != null
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (k in 0 until n) {
                    val s = buf[k].toInt()
                    bytes[2 * k] = (s and 0xFF).toByte(); bytes[2 * k + 1] = (s shr 8 and 0xFF).toByte()
                    peak = maxOf(peak, Math.abs(s))
                }
                wav.write(bytes, 0, n * 2); total += n * 2
                if (pipeOk) pipeOk = runCatching { out!!.write(bytes, 0, n * 2) }.isSuccess
                val level = (peak / 12000f).coerceIn(0f, 1f)
                main.post { listener.onLevel(level) }
            }
            runCatching { rec.stop() }; rec.release()
            writeWavHeader(wav, total, rate)
            wav.close()
            runCatching { out?.close() }
            main.post { audioDone = true; maybeFinish() }
        }.apply { start() }
    }

    /** Stops recording; the result arrives in [Listener.onFinished] once the transcript is ready. */
    fun stop() {
        if (stopped) return
        stopped = true
        running = false
        if (!savesAudio) runCatching { recognizer?.stopListening() }
        // Give the recognizer a moment to deliver its final text.
        main.postDelayed({ if (!finalReceived) { finalReceived = true; if (transcript.isBlank()) transcript = partial; maybeFinish() } }, 4000)
        maybeFinish()
    }

    fun cancel() {
        cancelled = true
        stopped = true
        running = false
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        main.postDelayed({ file?.delete() }, 600)
    }

    private var done = false
    private fun maybeFinish() {
        if (done || cancelled || !stopped || !audioDone || !finalReceived) return
        done = true
        runCatching { recognizer?.destroy() }
        recognizer = null
        val f = file?.takeIf { it.isFile && it.length() > 44 }
        listener.onFinished(f, transcript.trim(), System.currentTimeMillis() - started)
    }

    private fun writeWavHeader(f: RandomAccessFile, dataLen: Int, rate: Int) {
        f.seek(0)
        fun i32(v: Int) { f.write(v and 0xFF); f.write(v shr 8 and 0xFF); f.write(v shr 16 and 0xFF); f.write(v shr 24 and 0xFF) }
        fun i16(v: Int) { f.write(v and 0xFF); f.write(v shr 8 and 0xFF) }
        f.writeBytes("RIFF"); i32(36 + dataLen); f.writeBytes("WAVE")
        f.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
        f.writeBytes("data"); i32(dataLen)
    }
}

/** One shared player for voice bubbles. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    var currentPath: String? = null; private set
    private var onChange: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    val isPlaying get() = player?.isPlaying == true
    val position get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    val duration get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    fun toggle(path: String, changed: () -> Unit) {
        if (currentPath == path && player != null) {
            if (player!!.isPlaying) player!!.pause() else player!!.start()
            onChange = changed; changed(); tick(); return
        }
        stop()
        val p = MediaPlayer()
        runCatching {
            p.setDataSource(path); p.prepare(); p.start()
        }.onFailure { p.release(); return }
        player = p; currentPath = path; onChange = changed
        p.setOnCompletionListener { stop() }
        changed(); tick()
    }

    private fun tick() {
        main.removeCallbacksAndMessages(null)
        main.post(object : Runnable {
            override fun run() {
                onChange?.invoke()
                if (isPlaying) main.postDelayed(this, 120)
            }
        })
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val cb = onChange
        runCatching { player?.stop() }; runCatching { player?.release() }
        player = null; currentPath = null; onChange = null
        cb?.invoke()
    }
}
