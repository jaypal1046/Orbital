package com.orbital.skills.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbital.skills.MobileSkill
import com.orbital.skills.MobileSkillRegistry
import com.orbital.skills.SkillCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSkillsSheet(
    skillRegistry: MobileSkillRegistry,
    onDismiss: () -> Unit,
    onRunSkillPrompt: (String) -> Unit
) {
    val skills by skillRegistry.skills.collectAsState()
    var selectedCategory by remember { mutableStateOf<SkillCategory?>(null) }
    val filteredSkills = remember(skills, selectedCategory) {
        if (selectedCategory == null) skills else skills.filter { it.category == selectedCategory }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F121F),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚡ Mobile Skills Library",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Modular on-device skills powering autonomous AI actions",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedCategory == null
                    Surface(
                        color = if (isAll) Color(0xFF7C3AED) else Color(0xFF1B2035),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { selectedCategory = null }
                    ) {
                        Text(
                            text = "All (${skills.size})",
                            fontSize = 12.sp,
                            color = if (isAll) Color.White else Color(0xFFCBD5E1),
                            fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                items(SkillCategory.values()) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        color = if (isSelected) cat.accentColor else Color(0xFF1B2035),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(cat.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.displayName.split(" ").first(),
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Skills List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                items(filteredSkills, key = { it.id }) { skill ->
                    MobileSkillCard(
                        skill = skill,
                        onToggle = { skillRegistry.toggleSkill(skill.id) },
                        onRunPrompt = { prompt ->
                            onDismiss()
                            onRunSkillPrompt(prompt)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MobileSkillCard(
    skill: MobileSkill,
    onToggle: () -> Unit,
    onRunPrompt: (String) -> Unit
) {
    Surface(
        color = Color(0xFF161A2C),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (skill.isEnabled) skill.category.accentColor.copy(alpha = 0.5f) else Color(0xFF242B46)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(skill.category.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(skill.icon, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = skill.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = skill.category.displayName,
                            color = skill.category.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Switch(
                    checked = skill.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = skill.category.accentColor,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E243B)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = skill.description,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (skill.examplePrompts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "💡 Quick Actions:",
                    color = Color(0xFFA78BFA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    skill.examplePrompts.forEach { example ->
                        Surface(
                            color = Color(0xFF1D223A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF283256)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRunPrompt(example) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▶", fontSize = 10.sp, color = skill.category.accentColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = example,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
