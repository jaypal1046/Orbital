package com.orbital.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbital.data.CatalogModel
import com.orbital.data.ProviderInfo
import kotlinx.coroutines.launch

@Composable
fun EditModelsDialog(
    providerInfo: ProviderInfo,
    currentCandidates: List<CatalogModel>,
    currentScope: Set<String>?,
    onDismiss: () -> Unit,
    onSave: (Set<String>?) -> Unit,
    onCheckCatalogUpdates: suspend () -> List<CatalogModel> = { currentCandidates }
) {
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var candidates by remember(currentCandidates) { mutableStateOf(currentCandidates) }

    val allIds = remember(candidates) { candidates.map { it.modelId } }

    var allMode by remember { mutableStateOf(currentScope == null) }
    var selectedIds by remember {
        mutableStateOf(currentScope?.toSet() ?: allIds.toSet())
    }

    val isSelected: (String) -> Boolean = { id ->
        allMode || selectedIds.contains(id)
    }

    val allSelected = allIds.isNotEmpty() && allIds.all { isSelected(it) }
    val selectedCount = if (allMode) allIds.size else selectedIds.size

    fun toggleModel(id: String) {
        if (allMode) {
            allMode = false
            val next = allIds.toMutableSet()
            next.remove(id)
            selectedIds = next
        } else {
            val next = selectedIds.toMutableSet()
            if (next.contains(id)) {
                next.remove(id)
            } else {
                next.add(id)
            }
            selectedIds = next
        }
    }

    fun toggleAll() {
        if (allSelected) {
            allMode = false
            selectedIds = emptySet()
        } else {
            allMode = true
            selectedIds = allIds.toSet()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit models",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle / Description & Check catalog updates button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Choose which catalog models this key may serve. Select everything to clear the scope so future catalog models use it too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            isSyncing = true
                            coroutineScope.launch {
                                try {
                                    val updated = onCheckCatalogUpdates()
                                    if (updated.isNotEmpty()) {
                                        candidates = updated
                                        if (allMode) {
                                            selectedIds = updated.map { it.modelId }.toSet()
                                        }
                                    }
                                } finally {
                                    isSyncing = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF334155)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFF2563EB),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Text(
                                text = "Check catalog updates",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // "Select all" row with count
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { toggleAll() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { toggleAll() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF0F172A),
                            uncheckedColor = Color(0xFF94A3B8),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Select all",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$selectedCount of ${allIds.size} selected",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable candidates list in a bordered container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(candidates) { model ->
                            val checked = isSelected(model.modelId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleModel(model.modelId) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { toggleModel(model.modelId) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF0F172A),
                                        uncheckedColor = Color(0xFF94A3B8),
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = model.displayName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Size Badge (Frontier, Large, Medium, Small)
                                val sizeBadgeBg = when (model.sizeLabel.lowercase()) {
                                    "frontier" -> Color(0xFFF3E8FF)
                                    "large" -> Color(0xFFF1F5F9)
                                    "small" -> Color(0xFFF8FAFC)
                                    else -> Color(0xFFF1F5F9)
                                }
                                val sizeBadgeText = when (model.sizeLabel.lowercase()) {
                                    "frontier" -> Color(0xFF7E22CE)
                                    "large" -> Color(0xFF475569)
                                    "small" -> Color(0xFF64748B)
                                    else -> Color(0xFF64748B)
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(sizeBadgeBg)
                                        .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = model.sizeLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = sizeBadgeText
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Context Window Badge (e.g. 1M ctx, 33K ctx, 128K ctx, 262K ctx)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = model.contextWindow,
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Buttons: Cancel and Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (allSelected) {
                                onSave(null) // null clears the scope so future models use it too
                            } else {
                                onSave(selectedIds)
                            }
                        },
                        enabled = allMode || selectedIds.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F172A),
                            disabledContainerColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Text("Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
