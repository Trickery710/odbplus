package com.odbplus.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.odbplus.app.parts.PartsViewModel
import com.odbplus.app.parts.data.CommonRetailers
import com.odbplus.app.parts.data.PartPriority
import com.odbplus.app.parts.data.searchUrl
import com.odbplus.app.tools.ToolsViewModel
import com.odbplus.app.tools.data.ToolCategory
import com.odbplus.app.ui.theme.CyanPrimary
import com.odbplus.app.ui.theme.DarkBackground
import com.odbplus.app.ui.theme.DarkBorder
import com.odbplus.app.ui.theme.DarkSurface
import com.odbplus.app.ui.theme.DarkSurfaceHigh
import com.odbplus.app.ui.theme.DarkSurfaceVariant
import com.odbplus.app.ui.theme.RedError
import com.odbplus.app.ui.theme.TextPrimary
import com.odbplus.app.ui.theme.TextSecondary
import com.odbplus.app.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsAndToolsScreen(
    onNavigateToAi: () -> Unit,
    partsViewModel: PartsViewModel = hiltViewModel(),
    toolsViewModel: ToolsViewModel = hiltViewModel()
) {
    val parts by partsViewModel.parts.collectAsState()
    val toolsUiState by toolsViewModel.uiState.collectAsState()
    val context = LocalContext.current

    var partsExpanded by remember { mutableStateOf(true) }
    var toolsExpanded by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Parts & Tools",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (parts.isNotEmpty()) {
                        IconButton(onClick = { partsViewModel.clearAllParts() }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear All Parts",
                                tint = RedError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { paddingValues ->
        // Single LazyColumn avoids nested scroll issues with the two inner Column sections
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Parts Card ────────────────────────────────────────────────────
            item {
                SectionCard(
                    title = "Recommended Parts",
                    icon = Icons.Filled.ShoppingCart,
                    accentColor = CyanPrimary,
                    count = parts.size,
                    isExpanded = partsExpanded,
                    onToggle = { partsExpanded = !partsExpanded }
                ) {
                    if (parts.isEmpty()) {
                        // Inline empty state (no full-screen layout needed here)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = CyanPrimary.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No parts recommended yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Chat with AI diagnostics to get part recommendations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            val urgentParts = parts.filter {
                                it.priority == PartPriority.CRITICAL || it.priority == PartPriority.HIGH
                            }
                            val otherParts = parts.filter {
                                it.priority != PartPriority.CRITICAL && it.priority != PartPriority.HIGH
                            }

                            if (urgentParts.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(RedError)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "URGENT PARTS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = RedError,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                urgentParts.forEach { part ->
                                    PartCard(
                                        part = part,
                                        onRemove = { partsViewModel.removePart(part.id) },
                                        onShopClick = { retailer ->
                                            val url = retailer.searchUrl(part.name, part.partNumber)
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        }
                                    )
                                }
                            }

                            if (otherParts.isNotEmpty()) {
                                if (urgentParts.isNotEmpty()) Spacer(Modifier.height(4.dp))
                                Text(
                                    "OTHER RECOMMENDED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                otherParts.forEach { part ->
                                    PartCard(
                                        part = part,
                                        onRemove = { partsViewModel.removePart(part.id) },
                                        onShopClick = { retailer ->
                                            val url = retailer.searchUrl(part.name, part.partNumber)
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        }
                                    )
                                }
                            }

                            // Info footer
                            Surface(
                                color = DarkSurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = CyanPrimary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Parts are recommended by AI based on diagnostic codes and symptoms. Always verify compatibility with your specific vehicle.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Tools Card ────────────────────────────────────────────────────
            item {
                SectionCard(
                    title = "Tool Ordering",
                    subtitle = "Find tools at local & online stores",
                    icon = Icons.Filled.Build,
                    accentColor = CyanPrimary,
                    isExpanded = toolsExpanded,
                    onToggle = { toolsExpanded = !toolsExpanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExpandableToolCategory(
                            category = ToolCategory.ODB_ADAPTERS,
                            tools = toolsUiState.odbAdapters,
                            isExpanded = toolsUiState.expandedCategory == ToolCategory.ODB_ADAPTERS,
                            onToggle = { toolsViewModel.toggleCategory(ToolCategory.ODB_ADAPTERS) }
                        )
                        ExpandableToolCategory(
                            category = ToolCategory.MULTIMETERS,
                            tools = toolsUiState.multimeters,
                            isExpanded = toolsUiState.expandedCategory == ToolCategory.MULTIMETERS,
                            onToggle = { toolsViewModel.toggleCategory(ToolCategory.MULTIMETERS) }
                        )
                        ExpandableToolCategory(
                            category = ToolCategory.TESTING_TOOLS,
                            tools = toolsUiState.testingTools,
                            isExpanded = toolsUiState.expandedCategory == ToolCategory.TESTING_TOOLS,
                            onToggle = { toolsViewModel.toggleCategory(ToolCategory.TESTING_TOOLS) }
                        )
                        ExpandableToolCategory(
                            category = ToolCategory.AI_RECOMMENDED,
                            tools = toolsUiState.aiRecommendedTools,
                            isExpanded = toolsUiState.expandedCategory == ToolCategory.AI_RECOMMENDED,
                            onToggle = { toolsViewModel.toggleCategory(ToolCategory.AI_RECOMMENDED) },
                            showAiReason = true,
                            emptyMessage = "No AI recommendations yet. Use the AI Chat to get job-specific tool recommendations."
                        )
                    }
                }
            }

            // Bottom breathing room
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/**
 * Collapsible section card used for both the Parts and Tools sections.
 * Renders a header row with title, icon, optional count badge, and expand/collapse chevron.
 * The [content] lambda is placed inside an [AnimatedVisibility] block.
 */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    count: Int? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) Modifier.border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) DarkSurfaceHigh else DarkSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            // ── Header row ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            if (count != null && count > 0) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = accentColor.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp
                                  else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            // ── Expandable content ────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 18.dp
                    )
                ) {
                    HorizontalDivider(
                        color = DarkBorder,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    content()
                }
            }
        }
    }
}
