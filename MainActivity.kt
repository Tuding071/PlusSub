package com.plussub

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var gridLayout: LinearLayout
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        gridLayout = findViewById(R.id.gridLayout)
        
        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        }
        
        // Load button
        findViewById<Button>(R.id.btnLoad).setOnClickListener {
            Toast.makeText(this, "Load song clicked", Toast.LENGTH_SHORT).show()
            generateMockGrid()
        }
        
        // Play button
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            Toast.makeText(this, "Play clicked", Toast.LENGTH_SHORT).show()
        }
        
        // Save button
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Toast.makeText(this, "Save clicked", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun generateMockGrid() {
        gridLayout.removeAllViews()
        val chords = arrayOf("C", "G", "Am", "F", "C", "G", "Dm", "Em")
        
        for (i in chords.indices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Bar number
            row.addView(TextView(this).apply {
                text = "Bar ${i+1}"
                layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
            })
            
            // Chord
            row.addView(TextView(this).apply {
                text = chords[i]
                layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            })
            
            // Checkbox
            row.addView(CheckBox(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
                isChecked = i % 2 == 0
            })
            
            gridLayout.addView(row)
        }
        
        statusText.text = "Detected ${chords.size} bars"
    }
}
