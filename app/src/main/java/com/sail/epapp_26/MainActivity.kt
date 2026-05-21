package com.sail.epapp_26

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.sail.epapp_26.ui.theme.EPAPP26Theme
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.DisableSelection

class MainActivity : ComponentActivity() {

    private val hasRecordPermissionState = mutableStateOf(false)

    private val recordPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasRecordPermissionState.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen/edge-to-edge (hide system bars).
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )

        // Prevent screen dimming/locking while app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize permission state once.
        hasRecordPermissionState.value =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        setContent {
            EPAPP26Theme {
                val ctx = LocalContext.current
                val hasPermission = remember { hasRecordPermissionState }

                val inputDeviceText = remember { mutableStateOf("input: (detecting…)") }
                val outputDeviceText = remember { mutableStateOf("output: (detecting…)") }

                // Update device labels even when not running audio.
                AudioDeviceMonitor(
                    context = ctx,
                    inputDeviceText = inputDeviceText,
                    outputDeviceText = outputDeviceText
                )

                DisposableEffect(Unit) {
                    if (!hasPermission.value) {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    onDispose { }
                }

                val passthrough = remember {
                    MicPassthrough(
                        context = ctx,
                        inputDeviceText = inputDeviceText,
                        outputDeviceText = outputDeviceText
                    )
                }
                DisposableEffect(Unit) {
                    onDispose { passthrough.stop() }
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    containerColor = Color.Black,
                    contentColor = Color.White
                ) { _ ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Device info at the top.
                        DeviceInfoLine(textState = inputDeviceText)
                        DeviceInfoLine(textState = outputDeviceText)

                        Spacer(modifier = Modifier.weight(1f))

                        // Editable labels above the buttons.
                        val leftLabel = remember { mutableStateOf(TextFieldValue("")) }
                        val rightLabel = remember { mutableStateOf(TextFieldValue("")) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            DisableSelection {
                                OutlinedTextField(
                                    value = leftLabel.value,
                                    onValueChange = { leftLabel.value = it },
                                    singleLine = true,
                                    modifier = Modifier.width(150.dp),
                                    placeholder = {
                                        Text(
                                            text = "...",
                                            color = Color.White.copy(alpha = 0.60f),
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.Black,
                                        unfocusedContainerColor = Color.Black,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = Color.Transparent
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.size(24.dp))

                            DisableSelection {
                                OutlinedTextField(
                                    value = rightLabel.value,
                                    onValueChange = { rightLabel.value = it },
                                    singleLine = true,
                                    modifier = Modifier.width(150.dp),
                                    placeholder = {
                                        Text(
                                            text = "...",
                                            color = Color.White.copy(alpha = 0.60f),
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.Black,
                                        unfocusedContainerColor = Color.Black,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = Color.Transparent
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HoldButton(
                                text = "left",
                                color = Color(0xFF1976D2),
                                enabled = hasPermission.value,
                                onHoldStart = { passthrough.start(Channel.LEFT) },
                                onHoldEnd = { passthrough.stop() }
                            )
                            Spacer(modifier = Modifier.size(24.dp))
                            HoldButton(
                                text = "right",
                                color = Color(0xFFD32F2F),
                                enabled = hasPermission.value,
                                onHoldStart = { passthrough.start(Channel.RIGHT) },
                                onHoldEnd = { passthrough.stop() }
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "©Gareth Power, SaIL, Guy's and St Thomas' NHS Foundation Trust",
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

private enum class Channel { LEFT, RIGHT }

@Composable
private fun DeviceInfoLine(textState: MutableState<String>) {
    Text(
        text = textState.value,
        color = Color.White,
        fontSize = 12.sp
    )
}

@Composable
private fun HoldButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val isHeld = remember { mutableStateOf(false) }

    val textColor = if (color.luminance() < 0.5f) Color.White else Color.Black
    val heldColor = Color.White

    Button(
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isHeld.value) 3.dp else 0.dp,
            color = if (isHeld.value) heldColor else Color.Transparent
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isHeld.value) color.copy(alpha = 0.80f) else color,
            contentColor = textColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 12.dp
        ),
        modifier = Modifier
            .size(width = 150.dp, height = 90.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isHeld.value = true
                        onHoldStart()
                        // If the press is cancelled (e.g., pointer jumps between buttons), ensure we stop.
                        waitForUpOrCancellation()
                        onHoldEnd()
                        isHeld.value = false
                    }
                }
            },
        onClick = { /* press-and-hold handled by pointer input */ }
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

/**
 * Simple, low-latency mono mic -> stereo output passthrough.
 *
 * - Captures from the phone mic (prefers TYPE_BUILTIN_MIC where supported).
 * - Does NOT enable Bluetooth SCO (which is the usual pathway that activates headset mics).
 * - Playback is normal media audio, so Bluetooth headsets can be used for output.
 */
private class MicPassthrough(
    private val context: Context,
    private val inputDeviceText: MutableState<String>,
    private val outputDeviceText: MutableState<String>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val sessionCounter = AtomicLong(0)

    @Volatile private var stopRequested = false

    private var lastInputDeviceId: Int? = null
    private var lastOutputDeviceId: Int? = null

    fun start(channel: Channel) {
        // Start a new session; any older session's stop/drain should be ignored.
        val sessionId = sessionCounter.incrementAndGet()

        // Hard-cancel current job so we never get "stuck audio" from a stale drain.
        job?.cancel()

        stopRequested = false
        job = scope.launch { runLoop(channel, sessionId) }
    }

    fun stop() {
        // Soft stop = drain.
        stopRequested = true
    }

    fun hardStop() {
        // Hard stop = immediate cancel (used for rapid retaps/switches).
        stopRequested = true
        job?.cancel()
        job = null
    }

    private fun isCurrentSession(sessionId: Long): Boolean = sessionCounter.get() == sessionId

    private fun runLoop(channel: Channel, sessionId: Long) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Avoid BT SCO mic routing.
        try {
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            am.stopBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            }
        } catch (_: Throwable) {
        }

        val sampleRate = 48_000
        val inChannelConfig = AudioFormat.CHANNEL_IN_MONO
        val pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minInBuf = AudioRecord.getMinBufferSize(sampleRate, inChannelConfig, pcmEncoding)
        val inBufBytes = (minInBuf * 2).coerceAtLeast(sampleRate / 10)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            inChannelConfig,
            pcmEncoding,
            inBufBytes
        )

        // Prefer built-in mic and reflect that in the UI label.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val builtIn = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            if (builtIn != null) {
                audioRecord.preferredDevice = builtIn
                updateDeviceLabelsIfChanged(builtIn, /* outDev = */ null)
            }
        }

        val outChannelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val minOutBuf = AudioTrack.getMinBufferSize(sampleRate, outChannelConfig, pcmEncoding)
        val outBufBytes = (minOutBuf * 2).coerceAtLeast(sampleRate / 10)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(pcmEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(outChannelConfig)
                    .build()
            )
            .setBufferSizeInBytes(outBufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
            }
            .build()

        val blockFrames = 240 // 5ms @ 48kHz
        val inBlock = ShortArray(blockFrames)
        val outBlock = ShortArray(blockFrames * 2)

        try {
            audioTrack.play()

            // Warmup
            val warmFrames = sampleRate / 20 // 50ms
            run {
                val warmBlock = ShortArray(blockFrames * 2)
                var written = 0
                while (written < warmFrames && scope.isActive && isCurrentSession(sessionId)) {
                    val framesThis = minOf(blockFrames, warmFrames - written)
                    audioTrack.write(warmBlock, 0, framesThis * 2)
                    written += framesThis
                }
            }

            if (!isCurrentSession(sessionId)) return

            audioRecord.startRecording()

            val drainTimeoutMs = 2000L
            val fadeMs = 80
            val fadeFrames = (sampleRate * fadeMs) / 1000

            var stoppedAtMs: Long? = null
            var fadeWrittenFrames = 0
            var framesWrittenTotal = warmFrames.toLong()

            while (scope.isActive && isCurrentSession(sessionId)) {
                if (stopRequested) {
                    if (stoppedAtMs == null) {
                        stoppedAtMs = android.os.SystemClock.uptimeMillis()
                        try {
                            audioRecord.stop()
                        } catch (_: Throwable) {
                        }
                    }

                    if (fadeWrittenFrames < fadeFrames) {
                        val framesThis = minOf(blockFrames, fadeFrames - fadeWrittenFrames)
                        var j = 0
                        for (i in 0 until framesThis) {
                            val g = 1f - (fadeWrittenFrames + i).toFloat() / fadeFrames.toFloat()
                            val s = (0f * g).toInt().toShort()
                            if (channel == Channel.LEFT) {
                                outBlock[j++] = s
                                outBlock[j++] = 0
                            } else {
                                outBlock[j++] = 0
                                outBlock[j++] = s
                            }
                        }
                        audioTrack.write(outBlock, 0, framesThis * 2)
                        fadeWrittenFrames += framesThis
                        framesWrittenTotal += framesThis.toLong()
                    }

                    val head = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        audioTrack.playbackHeadPosition.toLong()
                    } else {
                        framesWrittenTotal
                    }

                    val done = head >= framesWrittenTotal
                    val timedOut = (android.os.SystemClock.uptimeMillis() - (stoppedAtMs ?: 0L)) >= drainTimeoutMs
                    if (done || timedOut) break

                    Thread.yield()
                    continue
                }

                val read = audioRecord.read(inBlock, 0, inBlock.size)
                if (read <= 0) continue

                var j = 0
                for (i in 0 until read) {
                    val s = inBlock[i]
                    if (channel == Channel.LEFT) {
                        outBlock[j++] = s
                        outBlock[j++] = 0
                    } else {
                        outBlock[j++] = 0
                        outBlock[j++] = s
                    }
                }

                audioTrack.write(outBlock, 0, read * 2)
                framesWrittenTotal += read.toLong()

                // Device labels: input should stay as built-in mic; output is handled by AudioDeviceMonitor polling.
            }
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Throwable) {
            }
            try {
                audioTrack.stop()
            } catch (_: Throwable) {
            }
            audioRecord.release()
            audioTrack.release()

            // Only clear stopRequested if we are still the current session.
            if (isCurrentSession(sessionId)) {
                stopRequested = false
            }
        }
    }

    private fun updateDeviceLabelsIfChanged(inDev: AudioDeviceInfo?, outDev: AudioDeviceInfo?) {
        val inId = inDev?.id
        if (inDev != null && inId != lastInputDeviceId) {
            lastInputDeviceId = inId
            inputDeviceText.value = "input: ${describeDeviceStatic(inDev)}"
        }

        val outId = outDev?.id
        if (outDev != null && outId != lastOutputDeviceId) {
            lastOutputDeviceId = outId
            outputDeviceText.value = "output: ${describeDeviceStatic(outDev)}"
        }
    }

    companion object {
        fun describeDeviceStatic(dev: AudioDeviceInfo?): String {
            if (dev == null) return "(none)"
            val type = when (dev.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in mic"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth sco"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth a2dp"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "usb headset"
                AudioDeviceInfo.TYPE_HDMI -> "hdmi"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "line analog"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line digital"
                AudioDeviceInfo.TYPE_DOCK -> "dock"
                else -> "type=${dev.type}"
            }
            val name = dev.productName?.toString()?.takeIf { it.isNotBlank() }
            return if (name != null) "$type ($name)" else type
        }
    }
}

@Composable
private fun AudioDeviceMonitor(
    context: Context,
    inputDeviceText: MutableState<String>,
    outputDeviceText: MutableState<String>,
) {
    val lastIn = remember { mutableStateOf<Int?>(null) }
    val lastOut = remember { mutableStateOf<Int?>(null) }

    fun refresh(am: AudioManager) {
        val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val builtInMic = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val inDev = builtInMic ?: inputs.firstOrNull()

        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Prefer "real" listening outputs first and only fall back to speaker/earpiece.
        val preferredOutTypes = buildList {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Some devices report LE Audio for output.
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        }

        val outDev = outputs.firstOrNull { it.type in preferredOutTypes }
            ?: outputs.firstOrNull { it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE && it.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()

        if (inDev?.id != lastIn.value) {
            lastIn.value = inDev?.id
            inputDeviceText.value = "input: ${MicPassthrough.describeDeviceStatic(inDev)}"
        }
        if (outDev?.id != lastOut.value) {
            lastOut.value = outDev?.id
            outputDeviceText.value = "output: ${MicPassthrough.describeDeviceStatic(outDev)}"
        }
    }

    DisposableEffect(context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        refresh(am)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    refresh(am)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    refresh(am)
                }
            }
            am.registerAudioDeviceCallback(callback, null)
            onDispose { am.unregisterAudioDeviceCallback(callback) }
        } else {
            onDispose { }
        }
    }

    // Poll periodically because some BT stacks report device availability changes
    // without triggering a route change callback until audio starts.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            refresh(am)
            kotlinx.coroutines.delay(500)
        }
    }
}
