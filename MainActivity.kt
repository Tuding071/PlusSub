package com.plussub

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
            }
        }
        
        setContent {
            PlusSubApp()
        }
    }
}

// Data classes
data class Bar(
    val index: Int,
    val startTime: Float,
    val endTime: Float,
    var chord: String = "---",
    var enabled: Boolean = true,
    val frequency: Float = 0f
)

data class SongInfo(
    val uri: Uri? = null,
    val name: String = "No song loaded",
    val duration: Float = 0f,
    val path: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSubApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    var songInfo by remember { mutableStateOf(SongInfo()) }
    var bars by remember { mutableStateOf(listOf<Bar>()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    var subBassOctave by remember { mutableStateOf(-1) } // -1 = one octave down
    var subBassVolume by remember { mutableStateOf(0.7f) }
    var eqBass by remember { mutableStateOf(0.5f) }
    var eqMid by remember { mutableStateOf(0.5f) }
    var eqTreble by remember { mutableStateOf(0.5f) }
    var zoomLevel by remember { mutableStateOf(16) } // Show 16 bars at a time
    
    // Media player
    val mediaPlayer = remember { MediaPlayer() }
    val equalizer = remember { mutableStateOf<Equalizer?>(null) }
    
    // File picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            songInfo = SongInfo(
                uri = it,
                name = it.lastPathSegment ?: "Selected song",
                duration = getAudioDuration(context, it),
                path = it.path ?: ""
            )
            
            // Generate mock bars based on duration
            bars = generateMockBars(songInfo.duration)
            
            // Setup media player
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(context, it)
                mediaPlayer.prepare()
                
                // Setup equalizer
                equalizer.value = Equalizer(0, mediaPlayer.audioSessionId)
                equalizer.value?.apply {
                    enabled = true
                    usePreset(0)
                }
                
                Toast.makeText(context, "Song loaded: ${songInfo.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading song", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Playback control
    fun togglePlayback() {
        if (songInfo.uri == null) {
            Toast.makeText(context, "Load a song first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
        } else {
            mediaPlayer.start()
            isPlaying = true
            
            // Update position
            scope.launch {
                while (isPlaying && mediaPlayer.isPlaying) {
                    currentPosition = mediaPlayer.currentPosition / 1000f
                    delay(100)
                }
            }
        }
    }
    
    // Main UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PlusSub", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { 
                        Toast.makeText(context, "Settings", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Waveform display (simplified)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw simple waveform
                    val width = size.width
                    val height = size.height
                    val midY = height / 2
                    
                    for (i in 0 until 50) {
                        val x = (i / 50f) * width
                        val waveHeight = (Math.sin(i * 0.5) * height / 3).toFloat()
                        drawLine(
                            color = Color.Blue,
                            start = androidx.compose.ui.geometry.Offset(x, midY - waveHeight),
                            end = androidx.compose.ui.geometry.Offset(x, midY + waveHeight),
                            strokeWidth = 2f
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Song info and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(songInfo.name, fontWeight = FontWeight.Medium)
                Text("${formatTime(currentPosition)} / ${formatTime(songInfo.duration)}")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Zoom control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ZOOM", fontSize = 12.sp)
                Slider(
                    value = zoomLevel.toFloat(),
                    onValueChange = { zoomLevel = it.toInt() },
                    valueRange = 4f..32f,
                    modifier = Modifier.weight(1f)
                )
                Text("$zoomLevel bars", fontSize = 12.sp)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // FL Style Bar Grid
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0))
            ) {
                Column {
                    // Bar numbers
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.LightGray)
                    ) {
                        items(bars.take(zoomLevel).size) { index ->
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Bar ${index + 1}", fontSize = 10.sp)
                            }
                        }
                    }
                    
                    // Chords
                    LazyRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(bars.take(zoomLevel).size) { index ->
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(bars[index].chord, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    // FL Style blocks (enabled bars)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(bars.take(zoomLevel)) { index, bar ->
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(40.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (bar.enabled) Color(0xFF4CAF50)
                                        else Color(0xFFBDBDBD)
                                    )
                                    .clickable {
                                        bars = bars.toMutableList().apply {
                                            this[index] = bar.copy(enabled = !bar.enabled)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sub Bass Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SUB BASS", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                    
                    // Octave
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Octave:")
                        Row {
                            listOf(-2, -1, 0).forEach { octave ->
                                FilterChip(
                                    selected = subBassOctave == octave,
                                    onClick = { subBassOctave = octave },
                                    label = { 
                                        Text(if (octave == 0) "Normal" else "${octave} oct") 
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                    
                    // Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Volume:", modifier = Modifier.width(60.dp))
                        Slider(
                            value = subBassVolume,
                            onValueChange = { subBassVolume = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(subBassVolume * 100).toInt()}%")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // EQ Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("EQ (Song Only)", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                    
                    // Bass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bass", modifier = Modifier.width(60.dp))
                        Slider(
                            value = eqBass,
                            onValueChange = { eqBass = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Mid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mid", modifier = Modifier.width(60.dp))
                        Slider(
                            value = eqMid,
                            onValueChange = { eqMid = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Treble
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Treble", modifier = Modifier.width(60.dp))
                        Slider(
                            value = eqTreble,
                            onValueChange = { eqTreble = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { filePicker.launch("audio/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("📂 LOAD")
                }
                
                Button(
                    onClick = { 
                        Toast.makeText(context, "Analyzing chords...", Toast.LENGTH_SHORT).show()
                        bars = bars.mapIndexed { index, bar ->
                            bar.copy(
                                chord = listOf("C", "G", "Am", "F", "Dm", "Em")[index % 6],
                                frequency = listOf(65.4f, 98f, 110f, 87.3f, 73.4f, 82.4f)[index % 6]
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                ) {
                    Text("🎵 ANALYZE")
                }
                
                Button(
                    onClick = { togglePlayback() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(if (isPlaying) "⏸️ PAUSE" else "▶️ PLAY")
                }
                
                Button(
                    onClick = {
                        Toast.makeText(context, "Saving mixed audio...", Toast.LENGTH_SHORT).show()
                        // Save logic would go here
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("💾 SAVE")
                }
            }
        }
    }
}

// Helper functions
fun getAudioDuration(context: android.content.Context, uri: Uri): Float {
    return try {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(context, uri)
        mediaPlayer.prepare()
        val duration = mediaPlayer.duration / 1000f
        mediaPlayer.release()
        duration
    } catch (e: Exception) {
        180f // Default 3 minutes
    }
}

fun generateMockBars(duration: Float): List<Bar> {
    val barCount = (duration / 4).toInt() // Assume 4 seconds per bar
    val bars = mutableListOf<Bar>()
    for (i in 0 until barCount) {
        val start = i * 4f
        val end = (i + 1) * 4f
        bars.add(
            Bar(
                index = i,
                startTime = start,
                endTime = end,
                chord = listOf("C", "G", "Am", "F")[i % 4],
                enabled = i % 3 != 0, // Enable most bars
                frequency = listOf(65.4f, 98f, 110f, 87.3f)[i % 4]
            )
        )
    }
    return bars
}

fun formatTime(seconds: Float): String {
    val minutes = (seconds / 60).toInt()
    val secs = (seconds % 60).toInt()
    return String.format("%d:%02d", minutes, secs)
}
