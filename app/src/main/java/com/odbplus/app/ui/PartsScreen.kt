package com.odbplus.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.odbplus.app.parts.PartsViewModel
import com.odbplus.app.parts.data.*
import com.odbplus.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartsScreen(
    onNavigateToAi: () -> Unit,
    viewModel: PartsViewModel = hiltViewModel()
) {
    val parts by viewModel.parts.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recommended Parts",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (parts.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAllParts() }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = RedError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        if (parts.isEmpty()) {
            EmptyPartsState(
                onNavigateToAi = onNavigateToAi,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Urgent parts section
                val urgentParts = parts.filter {
                    it.priority == PartPriority.CRITICAL || it.priority == PartPriority.HIGH
                }
                if (urgentParts.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(RedError)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "URGENT PARTS",
                                style = MaterialTheme.typography.labelMedium,
                                color = RedError,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    items(urgentParts) { part ->
                        PartCard(
                            part = part,
                            onRemove = { viewModel.removePart(part.id) },
                            onShopClick = { retailer ->
                                val url = retailer.searchUrl(part.name, part.partNumber)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                }

                // Other parts section
                val otherParts = parts.filter {
                    it.priority != PartPriority.CRITICAL && it.priority != PartPriority.HIGH
                }
                if (otherParts.isNotEmpty()) {
                    item {
                        if (urgentParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "OTHER RECOMMENDED",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    items(otherParts) { part ->
                        PartCard(
                            part = part,
                            onRemove = { viewModel.removePart(part.id) },
                            onShopClick = { retailer ->
                                val url = retailer.searchUrl(part.name, part.partNumber)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        )
                    }
                }

                // Info footer
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = DarkSurfaceVariant
                        ),
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
                            Spacer(modifier = Modifier.width(10.dp))
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
}

@Composable
fun EmptyPartsState(
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = CyanPrimary.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Parts Recommended",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Chat with the AI diagnostics assistant to get part recommendations based on your vehicle's trouble codes and symptoms.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onNavigateToAi,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanPrimary,
                contentColor = TextOnAccent
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go to AI Diagnostics", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PartCard(
    part: RecommendedPart,
    onRemove: () -> Unit,
    onShopClick: (PartRetailer) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = DarkBorder,
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row with name and priority badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (part.partNumber != null) {
                        Text(
                            text = "P/N: ${part.partNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriorityBadge(priority = part.priority)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            modifier = Modifier.size(16.dp),
                            tint = TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category and DTC chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyanPrimary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = part.category.displayName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (part.relatedDtc != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RedError.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = part.relatedDtc,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = RedLight,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reason/description
            Text(
                text = part.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (part.estimatedPrice != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Est. Price: ${part.estimatedPrice}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CyanPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Retailer buttons
            Text(
                text = "SHOP AT",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(CommonRetailers.all()) { retailer ->
                    OutlinedButton(
                        onClick = { onShopClick(retailer) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CyanPrimary
                        )
                    ) {
                        Icon(
                            if (retailer.isLocal) Icons.Filled.Store else Icons.Filled.Language,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = retailer.name.split(" ").first(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriorityBadge(priority: PartPriority) {
    val badgeColor = Color(priority.colorHex)
    Surface(
        color = badgeColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = priority.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
