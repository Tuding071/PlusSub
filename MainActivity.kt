package com.plussub

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    
    private lateinit var gridContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var volumeValue: TextView
    private lateinit var octaveSpinner: Spinner
    private lateinit var volumeSeekBar: SeekBar
    
    private val bars = mutableListOf<Bar>()
    
    data class Bar(
        val index: Int,
        var chord: String = "C",
        var enabled: Boolean = true
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        gridContainer = findViewById(R.id.gridContainer)
        statusText = findViewById(R.id.statusText)
        volumeValue = findViewById(R.id.volumeValue)
        octaveSpinner = findViewById(R.id.octaveSpinner)
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        
        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
        
        // Setup spinner
        ArrayAdapter.createFromResource(this, R.array.octave_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                octaveSpinner.adapter = adapter
            }
        
        // Setup volume
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Buttons
        findViewById<Button>(R.id.loadBtn).setOnClickListener { loadSong() }
        findViewById<Button>(R.id.playBtn).setOnClickListener { playBass() }
        findViewById<Button>(R.id.saveBtn).setOnClickListener { Toast.makeText(this, "Save demo", Toast.LENGTH_SHORT).show() }
    }
    
    private fun loadSong() {
        gridContainer.removeAllViews()
        bars.clear()
        
        // Header
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply { text = "BAR"; layoutParams = LinearLayout.LayoutParams(0, 60, 1f) })
        header.addView(TextView(this).apply { text = "CHORD"; layoutParams = LinearLayout.LayoutParams(0, 60, 1f); gravity = android.view.Gravity.CENTER })
        header.addView(TextView(this).apply { text = "BASS"; layoutParams = LinearLayout.LayoutParams(0, 60, 1f); gravity = android.view.Gravity.CENTER })
        gridContainer.addView(header)
        
        // 16 bars
        val chords = arrayOf("C", "G", "Am", "F", "C", "G", "C", "G", "Dm", "Em", "F", "G", "C", "Am", "F", "G")
        for (i in 0..15) {
            val bar = Bar(i, chords[i], i % 3 != 1)
            bars.add(bar)
            
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = "${i+1}"; layoutParams = LinearLayout.LayoutParams(0, 60, 1f) })
            row.addView(TextView(this).apply { text = chords[i]; layoutParams = LinearLayout.LayoutParams(0, 60, 1f); gravity = android.view.Gravity.CENTER })
            
            val switch = Switch(this).apply {
                isChecked = bar.enabled
                setOnCheckedChangeListener { _, isChecked -> bar.enabled = isChecked }
            }
            row.addView(switch)
            gridContainer.addView(row)
        }
        
        statusText.text = "16 bars loaded"
    }
    
    private fun playBass() {
        val enabled = bars.count { it.enabled }
        Toast.makeText(this, "Playing $enabled bars with bass", Toast.LENGTH_SHORT).show()
    }
}
