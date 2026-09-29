package com.orbital.ui

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.power.PowerAwareScheduler

class AutomationSettingsActivity : ComponentActivity() {

    private lateinit var powerAwareScheduler: PowerAwareScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        powerAwareScheduler = PowerAwareScheduler(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0C14),
                    surface = Color(0xFF131626),
                    primary = Color(0xFF7C3AED)
                )
            ) {
                AutomationSettingsScreen(
                    onBack = { finish() },
                    onSave = { enabled, startHour, workWifi, homeWifi ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_FINE_LOCATION), 1)
                        if (enabled) {
                            powerAwareScheduler.scheduleDailyBriefing(startHour)
                            powerAwareScheduler.saveWifiAutomations(workWifi, homeWifi)
                            Toast.makeText(this, "Automation schedule updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Automation disabled", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(
    onBack: () -> Unit,
    onSave: (Boolean, Int, String, String) -> Unit
) {
    var isEnabled by remember { mutableStateOf(true) }
    var dailySummaryEnabled by remember { mutableStateOf(true) }
    var voiceTranscriptionEnabled by remember { mutableStateOf(true) }
    var memoryCleanupEnabled by remember { mutableStateOf(true) }
    var startHour by remember { mutableStateOf(2) }
    var workWifi by remember { mutableStateOf("") }
    var homeWifi by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Automation & Power",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F111A))
            )
        },
        containerColor = Color(0xFF0A0C14)
    ) { padding ->
        val context = androidx.compose.ui.platform.LocalContext.current
        val secureStorage = remember { com.orbital.data.SecureStorage(context) }
        var selectedApprovalMode by remember { mutableStateOf(secureStorage.getActionApprovalMode()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Action Approval / Execution Mode Card (Antigravity Style)
            Text(
                text = "ACTION EXECUTION MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131626)),
                border = BorderStroke(1.dp, Color(0xFF222842))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    com.orbital.data.ActionApprovalMode.values().forEach { mode ->
                        val isSelected = selectedApprovalMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF161A2C),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF8B5CF6) else Color(0xFF262D4A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedApprovalMode = mode
                                    secureStorage.saveActionApprovalMode(mode)
                                    Toast.makeText(context, "Action mode set to ${mode.displayName}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.emoji,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFC084FC),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "BACKGROUND AUTOMATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Master Automation Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) Color(0xFF191D34) else Color(0xFF131626)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) Color(0xFF7C3AED).copy(alpha = 0.6f) else Color(0xFF222842)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEnabled) Color(0xFF7C3AED).copy(alpha = 0.2f)
                                    else Color(0xFF222842)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = if (isEnabled) Color(0xFFA78BFA) else Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Master Automation Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Run background routines when device is charging",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF7C3AED),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1F2438)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Automation Tasks Section
            Text(
                text = "AUTOMATION TASKS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131626)),
                border = BorderStroke(1.dp, Color(0xFF222842))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    TaskItemRow(
                        icon = Icons.Default.DateRange,
                        iconTint = Color(0xFF38BDF8),
                        title = "Daily Interaction Summary",
                        description = "Synthesizes key conversations & learnings at night",
                        checked = dailySummaryEnabled && isEnabled,
                        enabled = isEnabled,
                        onCheckedChange = { dailySummaryEnabled = it }
                    )

                    HorizontalDivider(
                        color = Color(0xFF222842),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    TaskItemRow(
                        icon = Icons.Default.Call,
                        iconTint = Color(0xFFA855F7),
                        title = "Voice Memo Transcription",
                        description = "Processes offline voice audio while plugged in",
                        checked = voiceTranscriptionEnabled && isEnabled,
                        enabled = isEnabled,
                        onCheckedChange = { voiceTranscriptionEnabled = it }
                    )

                    HorizontalDivider(
                        color = Color(0xFF222842),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    TaskItemRow(
                        icon = Icons.Default.Refresh,
                        iconTint = Color(0xFF10B981),
                        title = "Context & Memory Cleanup",
                        description = "Prunes transient scratchpads and cache buffers",
                        checked = memoryCleanupEnabled && isEnabled,
                        enabled = isEnabled,
                        onCheckedChange = { memoryCleanupEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Window Schedule Section
            Text(
                text = "OPTIMIZED EXECUTION WINDOW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA78BFA),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131626)),
                border = BorderStroke(1.dp, Color(0xFF222842))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Briefing",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Briefing arrives near your selected hour. Android may delay it briefly to save power.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )

                    TimeHourPickerCard(
                        label = "Briefing hour",
                        hour = startHour,
                        onHourChange = { startHour = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "WI-FI ROUTINES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA78BFA), letterSpacing = 1.sp)
            OutlinedTextField(value = workWifi, onValueChange = { workWifi = it }, label = { Text("Work Wi-Fi name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = homeWifi, onValueChange = { homeWifi = it }, label = { Text("Home Wi-Fi name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            Spacer(modifier = Modifier.height(24.dp))

            // Save CTA Button
            Button(
                onClick = { onSave(isEnabled, startHour, workWifi, homeWifi) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text(
                    text = "Save Configuration",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TaskItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else Color(0xFF64748B),
                    fontSize = 13.5.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF7C3AED),
                uncheckedThumbColor = Color(0xFF64748B),
                uncheckedTrackColor = Color(0xFF1F2438)
            )
        )
    }
}

@Composable
fun TimeHourPickerCard(
    label: String,
    hour: Int,
    onHourChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedHour = String.format("%02d:00", hour)
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF191D30),
        border = BorderStroke(1.dp, Color(0xFF282F4E))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { onHourChange((hour - 1 + 24) % 24) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("-", color = Color(0xFFA78BFA), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$displayHour $amPm",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "($formattedHour)",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onHourChange((hour + 1) % 24) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("+", color = Color(0xFFA78BFA), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
