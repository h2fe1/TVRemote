package com.tvremote.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvremote.app.AppEntry
import com.tvremote.app.AppViewModel
import com.tvremote.app.theme.Palette
import kotlin.math.absoluteValue

@Composable
fun AppDrawer(
    visible: Boolean,
    viewModel: AppViewModel,
    onClose: () -> Unit,
) {
    val apps by viewModel.apps.collectAsState()
    val loading by viewModel.appsLoading.collectAsState()
    val error by viewModel.appsError.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 12 }),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF070708))
                .clickable(
                    interactionSource = androidx.compose.runtime.remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null,
                ) {},
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "App",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Palette.TextPrimary,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "轻点图标直接在 Apple TV 上打开",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextTertiary,
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "刷新 App 列表",
                        onClick = { viewModel.loadApps(force = true) },
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    SoftIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        onClick = onClose,
                        size = 38.dp,
                    )
                }
                Spacer(Modifier.height(22.dp))
                when {
                    loading && apps == null -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Palette.TextSecondary, strokeWidth = 2.dp)
                    }
                    error != null -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.Danger,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "重试",
                            color = Palette.TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { viewModel.loadApps(force = true) }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                        )
                    }
                    apps.isNullOrEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("暂无可启动的 App", color = Palette.TextTertiary)
                    }
                    else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        val columns = if (maxWidth > 600.dp) 6 else 4
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            items(apps.orEmpty(), key = { it.bundleId }) { app ->
                                AppTile(app = app) {
                                    viewModel.launchApp(app.bundleId)
                                    onClose()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    val system = systemAppVisual(app.bundleId)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (system != null) {
                        Brush.verticalGradient(
                            listOf(system.second, system.second.copy(alpha = 0.75f)),
                        )
                    } else {
                        Brush.verticalGradient(appGradient(app.bundleId))
                    },
                )
                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (system != null) {
                Icon(
                    system.first,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(27.dp),
                )
            } else {
                Text(
                    app.name.trim().firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            app.name,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun systemAppVisual(bundleId: String): Pair<ImageVector, Color>? = when (bundleId) {
    "com.apple.TVWatchList" -> Icons.Rounded.Tv to Color(0xFF2C2C2E)
    "com.apple.TVMusic" -> Icons.Rounded.MusicNote to Color(0xFFFA243C)
    "com.apple.TVAppStore" -> Icons.Rounded.Apps to Color(0xFF0A84FF)
    "com.apple.podcasts" -> Icons.Rounded.Podcasts to Color(0xFF9B4DE0)
    "com.apple.TVPhotos" -> Icons.Rounded.PhotoLibrary to Color(0xFFF5A623)
    "com.apple.TVSearch" -> Icons.Rounded.Search to Color(0xFF636366)
    "com.apple.TVSettings" -> Icons.Rounded.Settings to Color(0xFF636366)
    "com.apple.Fitness" -> Icons.Rounded.FitnessCenter to Color(0xFF30D158)
    "com.apple.Sing" -> Icons.Rounded.Mic to Color(0xFF5E5CE6)
    "com.apple.facetime" -> Icons.Rounded.Videocam to Color(0xFF30D158)
    "com.apple.TVHomeSharing" -> Icons.Rounded.Computer to Color(0xFF636366)
    "com.apple.TestFlight" -> Icons.Rounded.FlightTakeoff to Color(0xFF0A84FF)
    else -> null
}

private val appPalette = listOf(
    Color(0xFF2C6BED),
    Color(0xFFE5484D),
    Color(0xFFF5A623),
    Color(0xFF30A46C),
    Color(0xFF8E4EC6),
    Color(0xFF00A2C7),
    Color(0xFFD6409F),
    Color(0xFF6E56CF),
)

private fun appGradient(bundleId: String): List<Color> {
    val hash = bundleId.fold(0) { acc, c -> acc * 31 + c.code }.absoluteValue
    val base = appPalette[hash % appPalette.size]
    return listOf(base, base.copy(alpha = 0.72f))
}
