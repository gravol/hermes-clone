package com.gravol.hermesclone.feature.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.ui.orb.BlobAvatar
import com.gravol.hermesclone.ui.orb.recipeFor
import kotlinx.serialization.json.*

/**
 * v0.2.7 — Bot Screen hub, official parity: avatar + bot switcher, Model/Skills
 * card, Integrations (Connections/Plugins/MCP), Work (Projects/Routines/
 * Artifacts/Kanban), Agent (Soul/Memory/Usage), Advanced (Voice/Toolsets/
 * Messaging/Webhooks/Config/Keys/Logs). Rows navigate to wired screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotHubScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var bots by remember { mutableStateOf<List<com.gravol.hermesclone.core.network.JsonRpcClient.ProfileRow>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showSwitcher by remember { mutableStateOf(false) }
    var modelInfo by remember { mutableStateOf<JsonObject?>(null) }
    var skillCount by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(Unit) {
        try {
            val list = com.gravol.hermesclone.di.AppModule.provideRpcClient().profilesList()
            bots = list
            selected = list.firstOrNull { it.isDefault }?.name ?: list.firstOrNull()?.name
        } catch (e: Exception) {
            bots = emptyList()
        }
    }
    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("/api/model/info")) {
            is NetworkResult.Success -> modelInfo = r.data as? JsonObject
            else -> {}
        }
        when (val r = DashboardApiClient.apiGet("/api/skills")) {
            is NetworkResult.Success -> {
                val arr = r.data as? JsonArray ?: JsonArray(emptyList())
                val on = arr.count { it.bool("enabled") != false }
                skillCount = arr.size to on
            }
            else -> {}
        }
    }

    val botRow = bots?.firstOrNull { it.name == selected }
    val botName = if (selected == null) "Hermes" else (botRow?.displayName?.takeIf { it.isNotBlank() } ?: if (botRow?.isDefault == true) "Hermes" else selected!!)
    val avatarKey = (botRow?.displayName?.takeIf { it.isNotBlank() } ?: botName).lowercase()
    val model = modelInfo?.str("model") ?: "—"
    val provider = modelInfo?.str("provider") ?: ""

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Spacer(Modifier.weight(1f))
                    // New Agent lives in the official; provisioning is cloud-gated here.
                    IconButton(onClick = { onNavigate("hub-instances") }) {
                        Icon(Icons.Filled.Add, "Instances")
                    }
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(Icons.Filled.Settings, "Settings")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Bot switcher: selected bot big, others as small avatars beside it.
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                bots?.filter { it.name != botName }?.forEach { p ->
                    Box(
                        Modifier
                            .padding(end = 10.dp, bottom = 6.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selected = p.name },
                    ) {
                        BlobAvatar(recipe = recipeFor((p.displayName.ifBlank { if (p.isDefault) "Hermes" else p.name }).lowercase()), size = 44.dp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .clickable { showSwitcher = true },
            ) {
                BlobAvatar(recipe = recipeFor(avatarKey), size = 96.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(botName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))

            // Model + Skills card
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column {
                    HubInnerRow(Icons.Filled.Memory, "Model", model,
                        onClick = { onNavigate("hub-model") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.School, "Skills", skillCount?.first?.toString(),
                        onClick = { onNavigate("skills") })
                }
            }

            HubSectionHeader("Integrations")
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column {
                    HubInnerRow(Icons.Filled.SwapHoriz, "Connections",
                        onClick = { onNavigate("hub-connections") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Extension, "Plugins",
                        onClick = { onNavigate("hub-plugins") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Power, "MCP",
                        onClick = { onNavigate("hub-mcp") })
                }
            }

            HubSectionHeader("Work")
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column {
                    HubInnerRow(Icons.Filled.FolderOpen, "Projects",
                        onClick = { onNavigate("hub-projects") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Schedule, "Routines",
                        onClick = { onNavigate("cron") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Inventory2, "Artifacts",
                        onClick = { onNavigate("hub-artifacts") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.ViewKanban, "Kanban", "Not on gateway",
                        onClick = { })
                }
            }

            HubSectionHeader("Agent")
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column {
                    HubInnerRow(Icons.Filled.AutoAwesome, "Soul",
                        onClick = { onNavigate("soul") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Psychology, "Memory",
                        onClick = { onNavigate("hub-memory") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Insights, "Usage",
                        onClick = { onNavigate("hub-usage") })
                }
            }

            HubSectionHeader("Advanced")
            Surface(color = MaterialTheme.colorScheme.surface, shape = HubCardShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp,)) {
                Column {
                    HubInnerRow(Icons.Filled.GraphicEq, "Voice",
                        onClick = { onNavigate("hub-voice") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.BuildCircle, "Toolsets",
                        onClick = { onNavigate("hub-toolsets") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Forum, "Messaging",
                        onClick = { onNavigate("hub-messaging") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Webhook, "Webhooks",
                        onClick = { onNavigate("hub-webhooks") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Tune, "Config",
                        onClick = { onNavigate("config") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.VpnKey, "Keys",
                        onClick = { onNavigate("hub-keys") })
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    HubInnerRow(Icons.Filled.Article, "Logs",
                        onClick = { onNavigate("hub-logs") })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSwitcher) {
        AlertDialog(
            onDismissRequest = { showSwitcher = false },
            title = { Text("Switch bot") },
            text = {
                Column {
                    bots?.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = p.name; showSwitcher = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))) {
                                BlobAvatar(recipe = recipeFor((p.displayName.ifBlank { if (p.isDefault) "Hermes" else p.name }).lowercase()), size = 36.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (p.isDefault) "Hermes" else p.name)
                                if (p.description.isNotBlank()) {
                                    Text(
                                        p.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (p.name == selected) Icon(Icons.Filled.Check, null)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwitcher = false }) { Text("Close") } },
        )
    }
}
