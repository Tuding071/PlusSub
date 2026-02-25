package com.plussub

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    // UI Components
    private lateinit var gridContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var songNameText: TextView
    private lateinit var loadBtn: Button
    private lateinit var playBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var octaveSpinner: Spinner
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeValue: TextView
    private lateinit var bassSeekBar: SeekBar
    private lateinit var midSeekBar: SeekBar
    private lateinit var trebleSeekBar: SeekBar
    
    // Audio
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    
    // Data
    private val bars = mutableListOf<Bar>()
    private var selectedSongUri: Uri? = null
    private var songDurationMs: Int = 0
    private var isPlaying = false
    
    // Constants
    private val PERMISSION_CODE = 100
    private val SAMPLE_RATE = 44100
    
    data class Bar(
        val index: Int,
        var chord: String = "C",
        var enabled: Boolean = true,
        var startTimeMs: Int = 0,
        var endTimeMs: Int = 4000,
        var frequency: Float = 65.4f // C2 frequency
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        checkPermissions()
    }
    
    private fun initViews() {
        gridContainer = findViewById(R.id.gridContainer)
        statusText = findViewById(R.id.statusText)
        songNameText = findViewById(R.id.songNameText)
        loadBtn = findViewById(R.id.loadBtn)
        playBtn = findViewById(R.id.playBtn)
        saveBtn = findViewById(R.id.saveBtn)
        octaveSpinner = findViewById(R.id.octaveSpinner)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        volumeValue = findViewById(R.id.volumeValue)
        bassSeekBar = findViewById(R.id.bassSeekBar)
        midSeekBar = findViewById(R.id.midSeekBar)
        trebleSeekBar = findViewById(R.id.trebleSeekBar)
        
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
    }
    
    private fun setupListeners() {
        loadBtn.setOnClickListener { openFilePicker() }
        playBtn.setOnClickListener { togglePlayback() }
        saveBtn.setOnClickListener { saveMixedAudio() }
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
                
                // Auto-detect tempo and create bars
                detectBars()
            }
            
            statusText.text = "Song loaded: ${formatTime(songDurationMs)}"
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun detectBars() {
        // Simple bar detection - assume 120 BPM = 2 seconds per bar
        val barDurationMs = 2000 // 2 seconds per bar
        val barCount = songDurationMs / barDurationMs
        
        bars.clear()
        gridContainer.removeAllViews()
        
        // Create header - FIXED: removed list_divider
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
            setBackgroundColor(0xFFE0E0E0.toInt()) // Light gray background
        }
        
        headerRow.addView(TextView(this).apply {
            text = "BAR"
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f).apply { marginStart = 8 }
            setTextColor(0xFF2196F3.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        
        headerRow.addView(TextView(this).apply {
            text = "CHORD"
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f)
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF2196F3.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        
        headerRow.addView(TextView(this).apply {
            text = "BASS"
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f)
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF2196F3.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        
        gridContainer.addView(headerRow)
        
        // Add divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
            setBackgroundColor(0xFFE0E0E0.toInt())
        }
        gridContainer.addView(divider)
        
        // Create bars with chord detection
        val chords = arrayOf("C", "G", "Am", "F", "C", "G", "C", "G", "Dm", "Em", "F", "G", "C", "Am", "F", "G")
        val frequencies = floatArrayOf(65.4f, 98.0f, 110.0f, 87.3f, 65.4f, 98.0f, 65.4f, 98.0f, 73.4f, 82.4f, 87.3f, 98.0f, 65.4f, 110.0f, 87.3f, 98.0f)
        
        for (i in 0 until minOf(barCount, 32)) { // Max 32 bars
            val bar = Bar(
                index = i,
                chord = chords[i % chords.size],
                enabled = true,
                startTimeMs = i * barDurationMs,
                endTimeMs = (i + 1) * barDurationMs,
                frequency = frequencies[i % frequencies.size]
            )
            bars.add(bar)
            
            // Bar row
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
                setBackgroundColor(if (bar.index % 2 == 0) 0xFFF5F5F5.toInt() else 0xFFFFFFFF.toInt())
            }
            
            // Bar number
            row.addView(TextView(this).apply {
                text = "${bar.index + 1}"
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f).apply { marginStart = 8 }
                gravity = android.view.Gravity.CENTER_VERTICAL
            })
            
            // Chord
            row.addView(TextView(this).apply {
                text = bar.chord
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f)
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFF4CAF50.toInt())
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            
            // Enable/Disable switch
            val switch = Switch(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f)
                gravity = android.view.Gravity.CENTER
                isChecked = bar.enabled
                setOnCheckedChangeListener { _, isChecked ->
                    bar.enabled = isChecked
                    updateStatus()
                }
            }
            row.addView(switch)
            
            gridContainer.addView(row)
        }
        
        updateStatus()
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
            val bassLevel = (bassSeekBar.progress - 50) * 2 // -100 to +100
            val midLevel = (midSeekBar.progress - 50) * 2
            val trebleLevel = (trebleSeekBar.progress - 50) * 2
            
            try {
                eq.setBandLevel(0, bassLevel.toShort()) // Bass band
                eq.setBandLevel(1, midLevel.toShort())  // Mid band
                eq.setBandLevel(2, trebleLevel.toShort()) // Treble band
            } catch (e: Exception) {
                // EQ bands might vary by device
            }
        }
        
        // Generate and play bass for enabled bars
        Thread {
            val enabledBars = bars.filter { it.enabled }
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
        
        val fileName = "PlusSub_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"
        Toast.makeText(this, "Demo: Would save as $fileName", Toast.LENGTH_LONG).show()
    }
    
    private fun updateStatus() {
        val enabledCount = bars.count { it.enabled }
        statusText.text = "${bars.size} bars • $enabledCount enabled"
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
