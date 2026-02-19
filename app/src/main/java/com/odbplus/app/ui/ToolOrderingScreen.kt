package com.odbplus.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.tools.ToolsViewModel
import com.odbplus.app.tools.data.Tool
import com.odbplus.app.tools.data.ToolCategory
import com.odbplus.app.tools.data.ToolRetailer
import com.odbplus.app.tools.data.ToolRetailers
import com.odbplus.app.tools.data.searchUrl
import com.odbplus.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolOrderingScreen(
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Tool Ordering",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Find tools at local & online stores",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ODB Adapters Section
            item {
                ExpandableToolCategory(
                    category = ToolCategory.ODB_ADAPTERS,
                    tools = uiState.odbAdapters,
                    isExpanded = uiState.expandedCategory == ToolCategory.ODB_ADAPTERS,
                    onToggle = { viewModel.toggleCategory(ToolCategory.ODB_ADAPTERS) }
                )
            }

            // Multimeters Section
            item {
                ExpandableToolCategory(
                    category = ToolCategory.MULTIMETERS,
                    tools = uiState.multimeters,
                    isExpanded = uiState.expandedCategory == ToolCategory.MULTIMETERS,
                    onToggle = { viewModel.toggleCategory(ToolCategory.MULTIMETERS) }
                )
            }

            // Testing Tools Section
            item {
                ExpandableToolCategory(
                    category = ToolCategory.TESTING_TOOLS,
                    tools = uiState.testingTools,
                    isExpanded = uiState.expandedCategory == ToolCategory.TESTING_TOOLS,
                    onToggle = { viewModel.toggleCategory(ToolCategory.TESTING_TOOLS) }
                )
            }

            // AI Recommended Tools Section
            item {
                ExpandableToolCategory(
                    category = ToolCategory.AI_RECOMMENDED,
                    tools = uiState.aiRecommendedTools,
                    isExpanded = uiState.expandedCategory == ToolCategory.AI_RECOMMENDED,
                    onToggle = { viewModel.toggleCategory(ToolCategory.AI_RECOMMENDED) },
                    showAiReason = true,
                    emptyMessage = "No AI recommendations yet. Use the AI Chat to get job-specific tool recommendations."
                )
            }
        }
    }
}

@Composable
fun ExpandableToolCategory(
    category: ToolCategory,
    tools: List<Tool>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    showAiReason: Boolean = false,
    emptyMessage: String? = null
) {
    val iconColor = when (category) {
        ToolCategory.AI_RECOMMENDED -> AmberSecondary
        else -> CyanPrimary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) Modifier.border(
                    width = 1.dp,
                    color = iconColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(14.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) DarkSurfaceHigh else DarkSurfaceVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column {
            // Category Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (category) {
                            ToolCategory.ODB_ADAPTERS -> Icons.Filled.Cable
                            ToolCategory.MULTIMETERS -> Icons.Filled.ElectricalServices
                            ToolCategory.TESTING_TOOLS -> Icons.Filled.Build
                            ToolCategory.AI_RECOMMENDED -> Icons.Filled.Psychology
                        },
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${tools.size} items",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded)
                        Icons.Filled.KeyboardArrowUp
                    else
                        Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (tools.isEmpty() && emptyMessage != null) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        tools.forEach { tool ->
                            ToolItemCard(
                                tool = tool,
                                showAiReason = showAiReason
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun ToolItemCard(
    tool: Tool,
    showAiReason: Boolean = false
) {
    val context = LocalContext.current
    var showStoreDialog by remember { mutableStateOf(false) }
    var storeType by remember { mutableStateOf<StoreType?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = DarkBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            if (tool.priceRange != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Est. ${tool.priceRange}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showAiReason && tool.aiRecommendationReason != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = AmberSecondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, AmberSecondary.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = AmberSecondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tool.aiRecommendationReason,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberOnContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Shop buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        storeType = StoreType.LOCAL
                        showStoreDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(
                        Icons.Filled.Store,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AmberSecondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Local", fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        storeType = StoreType.ONLINE
                        showStoreDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = TextOnAccent
                    )
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Online", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Store selection dialog
    if (showStoreDialog && storeType != null) {
        StoreSelectionDialog(
            tool = tool,
            storeType = storeType!!,
            onDismiss = { showStoreDialog = false },
            onStoreSelected = { retailer ->
                val url = retailer.searchUrl(tool.name)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                showStoreDialog = false
            }
        )
    }
}

enum class StoreType { LOCAL, ONLINE }

@Composable
fun StoreSelectionDialog(
    tool: Tool,
    storeType: StoreType,
    onDismiss: () -> Unit,
    onStoreSelected: (ToolRetailer) -> Unit
) {
    val stores = if (storeType == StoreType.LOCAL)
        ToolRetailers.localStores()
    else
        ToolRetailers.onlineStores()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        title = {
            Text(
                text = if (storeType == StoreType.LOCAL) "Shop at Local Stores" else "Shop Online",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Search for: ${tool.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.height(4.dp))
                stores.forEach { retailer ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStoreSelected(retailer) },
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (retailer.isLocal)
                                    Icons.Filled.Store
                                else
                                    Icons.Filled.Language,
                                contentDescription = null,
                                tint = if (retailer.isLocal) AmberSecondary else CyanPrimary
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = retailer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
