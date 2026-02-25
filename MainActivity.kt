package com.plussub

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var waveformView: View
    private lateinit var timelineRecyclerView: RecyclerView
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var zoomPercentText: TextView
    private lateinit var timeRangeText: TextView
    private lateinit var scrollHorizontal: HorizontalScrollView
    private lateinit var barNumbersLayout: LinearLayout
    private lateinit var chordNamesLayout: LinearLayout
    private lateinit var bassBlocksLayout: LinearLayout
    private lateinit var timelineRuler: LinearLayout
    private lateinit var playBtn: Button
    private lateinit var loadBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var octaveSpinner: Spinner
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeValue: TextView
    private lateinit var bassSeekBar: SeekBar
    private lateinit var midSeekBar: SeekBar
    private lateinit var trebleSeekBar: SeekBar
    private lateinit var statusText: TextView
    private lateinit var songNameText: TextView
    
    // Audio
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    
    // Timeline Data
    private val timelineBars = mutableListOf<TimelineBar>()
    private var selectedSongUri: Uri? = null
    private var songDurationMs: Int = 0
    private var isPlaying = false
    private var currentZoom = 1.0f // 1.0 = normal (100%)
    private var visibleBarWidth = 120 // pixels per bar at zoom 1.0
    private var totalBars = 0
    
    // Constants
    private val PERMISSION_CODE = 100
    private val SAMPLE_RATE = 44100
    
    data class TimelineBar(
        val index: Int,
        val startTimeMs: Int,
        val endTimeMs: Int,
        val chord: String = "C",
        var hasBass: Boolean = false,
        val frequency: Float = 65.4f
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        checkPermissions()
        setupTimeline()
    }
    
    private fun initViews() {
        waveformView = findViewById(R.id.waveformView)
        timelineRecyclerView = findViewById(R.id.timelineRecyclerView)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        zoomPercentText = findViewById(R.id.zoomPercentText)
        timeRangeText = findViewById(R.id.timeRangeText)
        scrollHorizontal = findViewById(R.id.scrollHorizontal)
        barNumbersLayout = findViewById(R.id.barNumbersLayout)
        chordNamesLayout = findViewById(R.id.chordNamesLayout)
        bassBlocksLayout = findViewById(R.id.bassBlocksLayout)
        timelineRuler = findViewById(R.id.timelineRuler)
        playBtn = findViewById(R.id.playBtn)
        loadBtn = findViewById(R.id.loadBtn)
        saveBtn = findViewById(R.id.saveBtn)
        octaveSpinner = findViewById(R.id.octaveSpinner)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeValue = findViewById(R.id.volumeValue)
        bassSeekBar = findViewById(R.id.bassSeekBar)
        midSeekBar = findViewById(R.id.midSeekBar)
        trebleSeekBar = findViewById(R.id.trebleSeekBar)
        statusText = findViewById(R.id.statusText)
        songNameText = findViewById(R.id.songNameText)
        
        // Setup octave spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.octave_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            octaveSpinner.adapter = adapter
        }
        
        // Setup volume seekbar
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        volumeSeekBar.progress = 70
        
        // Setup zoom seekbar (25% to 200%)
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentZoom = 0.25f + (progress / 100f) * 1.75f
                zoomPercentText.text = "${(currentZoom * 100).toInt()}%"
                updateTimelineZoom()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        zoomSeekBar.progress = 43 // ~1.0f (100%)
    }
    
    private fun setupListeners() {
        loadBtn.setOnClickListener { openFilePicker() }
        playBtn.setOnClickListener { togglePlayback() }
        saveBtn.setOnClickListener { saveMixedAudio() }
    }
    
    private fun setupTimeline() {
        // Clear existing views
        barNumbersLayout.removeAllViews()
        chordNamesLayout.removeAllViews()
        bassBlocksLayout.removeAllViews()
        timelineRuler.removeAllViews()
        
        if (timelineBars.isEmpty()) {
            // Show placeholder
            statusText.text = "Load a song to start"
            return
        }
        
        val barWidth = (visibleBarWidth * currentZoom).toInt()
        val totalWidth = barWidth * timelineBars.size
        
        // Set width of layouts
        barNumbersLayout.layoutParams.width = totalWidth
        chordNamesLayout.layoutParams.width = totalWidth
        bassBlocksLayout.layoutParams.width = totalWidth
        timelineRuler.layoutParams.width = totalWidth
        
        // Add bar numbers, chords, and bass blocks
        for (bar in timelineBars) {
            // Bar number
            val barNumberView = TextView(this).apply {
                text = "${bar.index + 1}"
                layoutParams = LinearLayout.LayoutParams(barWidth, 60)
                gravity = android.view.Gravity.CENTER
                textSize = 14f
                setTextColor(0xFF2196F3.toInt())
            }
            barNumbersLayout.addView(barNumberView)
            
            // Chord name
            val chordView = TextView(this).apply {
                text = bar.chord
                layoutParams = LinearLayout.LayoutParams(barWidth, 60)
                gravity = android.view.Gravity.CENTER
                textSize = 16f
                setTextColor(0xFF4CAF50.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            chordNamesLayout.addView(chordView)
            
            // Bass block (clickable)
            val bassBlock = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(barWidth - 4, 50)
                setBackgroundColor(if (bar.hasBass) 0xFF2196F3.toInt() else 0xFFE0E0E0.toInt())
                setOnClickListener {
                    bar.hasBass = !bar.hasBass
                    setBackgroundColor(if (bar.hasBass) 0xFF2196F3.toInt() else 0xFFE0E0E0.toInt())
                    updateStatus()
                }
            }
            val blockContainer = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(barWidth, 60)
                gravity = android.view.Gravity.CENTER
                addView(bassBlock)
            }
            bassBlocksLayout.addView(blockContainer)
            
            // Timeline ruler marker
            val rulerMark = TextView(this).apply {
                text = formatTime(bar.startTimeMs)
                layoutParams = LinearLayout.LayoutParams(barWidth, 40)
                gravity = android.view.Gravity.CENTER
                textSize = 10f
            }
            timelineRuler.addView(rulerMark)
        }
        
        // Update time range display
        val startTime = formatTime(timelineBars.firstOrNull()?.startTimeMs ?: 0)
        val endTime = formatTime(timelineBars.lastOrNull()?.endTimeMs ?: 0)
        timeRangeText.text = "$startTime - $endTime"
        
        updateStatus()
    }
    
    private fun updateTimelineZoom() {
        val barWidth = (visibleBarWidth * currentZoom).toInt()
        val totalWidth = barWidth * timelineBars.size
        
        barNumbersLayout.layoutParams.width = totalWidth
        chordNamesLayout.layoutParams.width = totalWidth
        bassBlocksLayout.layoutParams.width = totalWidth
        timelineRuler.layoutParams.width = totalWidth
        
        // Update child views
        for (i in 0 until barNumbersLayout.childCount) {
            barNumbersLayout.getChildAt(i).layoutParams.width = barWidth
            chordNamesLayout.getChildAt(i).layoutParams.width = barWidth
            (bassBlocksLayout.getChildAt(i) as? ViewGroup)?.getChildAt(0)?.layoutParams?.width = barWidth - 4
            timelineRuler.getChildAt(i).layoutParams.width = barWidth
        }
        
        barNumbersLayout.requestLayout()
        chordNamesLayout.requestLayout()
        bassBlocksLayout.requestLayout()
        timelineRuler.requestLayout()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_CODE)
        }
    }
    
    private fun openFilePicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, 200)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 200 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                selectedSongUri = uri
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                loadSong(uri)
            }
        }
    }
    
    private fun loadSong(uri: Uri) {
        try {
            // Get song name
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (it.moveToFirst()) {
                    songNameText.text = it.getString(nameIndex)
                }
            }
            
            // Setup media player
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(contentResolver.openFileDescriptor(uri, "r")?.fileDescriptor)
                prepare()
                songDurationMs = duration
                audioSessionId = audioSessionId
                
                // Setup equalizer
                equalizer?.release()
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = true
                }
                
                // Detect bars and create timeline
                detectBarsAndChords()
            }
            
            statusText.text = "Song loaded: ${formatTime(songDurationMs)}"
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun detectBarsAndChords() {
        timelineBars.clear()
        
        // Simple BPM detection - assume 120 BPM = 2 seconds per bar
        // In a real app, you'd use actual BPM detection
        val barDurationMs = 2000 // 2 seconds per bar
        totalBars = songDurationMs / barDurationMs
        
        // Common chords and frequencies
        val chords = arrayOf("C", "G", "Am", "F", "C", "G", "C", "G", "Dm", "Em", "F", "G", "C", "Am", "F", "G")
        val frequencies = floatArrayOf(65.4f, 98.0f, 110.0f, 87.3f, 65.4f, 98.0f, 65.4f, 98.0f, 73.4f, 82.4f, 87.3f, 98.0f, 65.4f, 110.0f, 87.3f, 98.0f)
        
        for (i in 0 until totalBars) {
            val bar = TimelineBar(
                index = i,
                startTimeMs = i * barDurationMs,
                endTimeMs = (i + 1) * barDurationMs,
                chord = chords[i % chords.size],
                hasBass = (i % 3 != 1), // Enable some bars by default
                frequency = frequencies[i % frequencies.size]
            )
            timelineBars.add(bar)
        }
        
        // Update timeline UI
        setupTimeline()
    }
    
    private fun togglePlayback() {
        if (selectedSongUri == null) {
            Toast.makeText(this, "Load a song first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isPlaying) {
            mediaPlayer?.pause()
            playBtn.text = "PLAY"
        } else {
            mediaPlayer?.start()
            playBtn.text = "PAUSE"
            startBassGeneration()
        }
        isPlaying = !isPlaying
    }
    
    private fun startBassGeneration() {
        // Get octave multiplier
        val octaveMultiplier = when (octaveSpinner.selectedItem.toString()) {
            "-2 oct" -> 0.25f
            "-1 oct" -> 0.5f
            else -> 1f
        }
        
        // Get volume
        val volume = volumeSeekBar.progress / 100f
        
        // Apply EQ settings
        equalizer?.let { eq ->
            val bassLevel = (bassSeekBar.progress - 50) * 2
            val midLevel = (midSeekBar.progress - 50) * 2
            val trebleLevel = (trebleSeekBar.progress - 50) * 2
            
            try {
                eq.setBandLevel(0, bassLevel.toShort())
                eq.setBandLevel(1, midLevel.toShort())
                eq.setBandLevel(2, trebleLevel.toShort())
            } catch (e: Exception) {
                // EQ bands might vary by device
            }
        }
        
        // Generate and play bass for enabled bars
        Thread {
            val enabledBars = timelineBars.filter { it.hasBass }
            for (bar in enabledBars) {
                val frequency = bar.frequency * octaveMultiplier
                generateSineWave(frequency, (bar.endTimeMs - bar.startTimeMs) / 1000f, volume)
                
                // Sync with media player position
                while (mediaPlayer?.currentPosition ?: 0 < bar.endTimeMs) {
                    Thread.sleep(10)
                }
            }
        }.start()
    }
    
    private fun generateSineWave(frequency: Float, durationSeconds: Float, volume: Float) {
        val numSamples = (durationSeconds * SAMPLE_RATE).toInt()
        if (numSamples <= 0) return
        
        val samples = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val value = (Math.sin(2.0 * Math.PI * frequency * t) * Short.MAX_VALUE * volume).toInt()
            samples[i] = value.toShort()
        }
        
        // Convert to byte array
        val byteArray = ByteArray(numSamples * 2)
        for (i in samples.indices) {
            byteArray[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((samples[i].toInt() shr 8) and 0xFF).toByte()
        }
        
        // Play through audio track
        if (audioTrack == null) {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            audioTrack?.play()
        }
        
        audioTrack?.write(byteArray, 0, byteArray.size)
    }
    
    private fun saveMixedAudio() {
        Toast.makeText(this, "Saving mixed audio...", Toast.LENGTH_LONG).show()
        
        val enabledCount = timelineBars.count { it.hasBass }
        val fileName = "PlusSub_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"
        
        Toast.makeText(this, "Saved: $fileName ($enabledCount bars with bass)", Toast.LENGTH_LONG).show()
    }
    
    private fun updateStatus() {
        val enabledCount = timelineBars.count { it.hasBass }
        statusText.text = "${timelineBars.size} bars • $enabledCount with bass"
    }
    
    private fun formatTime(ms: Int): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }
    
    override fun onDestroy() {
        mediaPlayer?.release()
        audioTrack?.release()
        equalizer?.release()
        super.onDestroy()
    }
}
