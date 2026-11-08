package com.gravol.hermesclone.feature.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.DebugLog
import com.gravol.hermesclone.core.network.NetworkResult
import com.gravol.hermesclone.ui.orb.BlobAvatar
import com.gravol.hermesclone.ui.orb.recipeFor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * v0.2.13 — "Create a New Agent", official-parity form (crawled live):
 * centered orb avatar, Name (placeholder "agent-name"), Description
 * ("What is this agent good at?"), Default Model sheet, Soul sheet
 * (SOUL.md editor), Clone Config From sheet ("Nothing — fresh install" +
 * profiles, default selection = Hermes/default), Create Agent button.
 * Real backend: POST /api/profiles (web_models.ProfileCreate); non-default
 * soul is PUT /api/profiles/{name}/soul after creation.
 * Official "Edit agent image" opens Agent Studio (orb palettes) — not
 * implemented yet, so the orb here is a live preview, not a button.
 */
private const val SOUL_TEMPLATE =
    "# Who this agent is\n\nVoice, priorities, and what it refuses to do."

private fun JsonElement?.asArr(): List<JsonElement> =
    (this as? JsonArray)?.toList() ?: emptyList()

private data class PickModel(val provider: String, val model: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var model by remember { mutableStateOf<PickModel?>(null) }        // null = Server default
    var soul by remember { mutableStateOf<String?>(null) }            // null = keep default
    // Official default selection is the source profile ("Hermes" = default profile).
    var cloneFrom by remember { mutableStateOf<String?>("default") }
    var profiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) } // value, label
    var modelSheet by remember { mutableStateOf(false) }
    var soulSheet by remember { mutableStateOf(false) }
    var cloneSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("/api/profiles")) {
            is NetworkResult.Success -> {
                val arr = (r.data as? JsonObject)?.get("profiles").asArr()
                profiles = arr.mapNotNull { p ->
                    val o = p as? JsonObject ?: return@mapNotNull null
                    val n = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val label = if (o["is_default"]?.jsonPrimitive?.booleanOrNull == true) "Hermes"
                                else (o["display_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: n)
                    n to label
                }
            }
            else -> profiles = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create a New Agent") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Live orb preview (Agent Studio editing not implemented; this is a preview, not a fake button).
            BlobAvatar(
                recipe = recipeFor(name.ifBlank { "agent-name" }),
                size = 120.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                name, { name = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                label = { Text("Name") }, placeholder = { Text("agent-name") },
                singleLine = true,
            )
            OutlinedTextField(
                description, { description = it },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text("Description") },
                placeholder = { Text("What is this agent good at?") },
                minLines = 2,
            )
            FormRow("Default Model", model?.let { "${it.model}" } ?: "Server default") { modelSheet = true }
            FormRow("Soul", if (soul == null) "Default" else if (soul.isNullOrBlank()) "Empty" else "Edited") { soulSheet = true }
            FormRow(
                "Clone Config From",
                cloneFrom?.let { c ->
                    profiles?.find { it.first == c }?.second ?: c
                } ?: "Nothing — fresh install",
            ) { cloneSheet = true }

            error?.let {
                Text(it, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (name.isBlank() || busy) return@Button
                    busy = true; error = null
                    scope.launch {
                        val body = buildJsonObject {
                            put("name", name.trim())
                            description.trim().takeIf { it.isNotEmpty() }?.let { put("description", it) }
                            cloneFrom?.let { put("clone_from", it) }
                            model?.let { put("provider", it.provider); put("model", it.model) }
                        }
                        when (val r = DashboardApiClient.apiSend("/api/profiles", "POST", body.toString())) {
                            is NetworkResult.Success -> {
                                DebugLog.log("REST", "NewAgent", "created profile '${name.trim()}'")
                                // Non-default soul: best-effort follow-up write.
                                soul?.takeIf { s -> s != SOUL_TEMPLATE }?.let { s ->
                                    val sr = DashboardApiClient.apiSend(
                                        "/api/profiles/${java.net.URLEncoder.encode(name.trim(), "UTF-8")}/soul",
                                        "PUT",
                                        buildJsonObject { put("content", s) }.toString())
                                    if (sr !is NetworkResult.Success)
                                        DebugLog.log("REST", "NewAgent", "soul write failed for '${name.trim()}'")
                                }
                                created = true
                                onBack()
                            }
                            is NetworkResult.Error -> {
                                error = (r.exception.message ?: "create failed").take(160)
                                busy = false
                            }
                            is NetworkResult.HttpError -> {
                                // FastAPI errors are {"detail": "..."} — surface the detail only.
                                val detail = try {
                                    Json.parseToJsonElement(r.message).jsonObject["detail"]
                                        ?.jsonPrimitive?.contentOrNull
                                } catch (_: Exception) { null }
                                error = (detail ?: r.message).take(200)
                                busy = false
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Create Agent", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (modelSheet) ModelSheet(
            onDismiss = { modelSheet = false },
            onPick = { model = it; modelSheet = false },
        )
        if (soulSheet) SoulSheet(
            initial = soul ?: SOUL_TEMPLATE,
            onDismiss = { soulSheet = false },
            onDone = { soul = it; soulSheet = false },
        )
        if (cloneSheet) CloneFromSheet(
            profiles = profiles, current = cloneFrom,
            onDismiss = { cloneSheet = false },
            onPick = { cloneFrom = it; cloneSheet = false },
        )
    }
}

@Composable
private fun FormRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(onDismiss: () -> Unit, onPick: (PickModel?) -> Unit) {
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf<List<PickModel>?>(null) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        when (val r = DashboardApiClient.apiGet("/api/model/options")) {
            is NetworkResult.Success -> {
                val obj = r.data as? JsonObject
                models = obj?.get("providers").asArr().flatMap { p ->
                    val o = p as? JsonObject ?: return@flatMap emptyList()
                    val slug = o["slug"]?.jsonPrimitive?.contentOrNull ?: return@flatMap emptyList()
                    o.get("models").asArr().mapNotNull { m ->
                        m.jsonPrimitive.contentOrNull?.let { PickModel(slug, it) }
                    }
                }
            }
            else -> models = emptyList()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Default Model", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                filter, { filter = it },
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search all models") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
            )
            Row(
                Modifier.fillMaxWidth().clickable { onPick(null) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Server Default", fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
            }
            LazyColumn(Modifier.fillMaxHeight().padding(bottom = 24.dp)) {
                val shown = models?.filter {
                    filter.isBlank() || it.model.contains(filter, true) || it.provider.contains(filter, true)
                } ?: emptyList()
                items(shown, key = { "${it.provider}/${it.model}" }) { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(m.model)
                            Text(m.provider, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoulSheet(initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SOUL.md", style = MaterialTheme.typography.titleMedium)
                    Text("The persona this agent answers as.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onDone(text) }) { Text("Done") }
            }
            OutlinedTextField(
                text, { text = it },
                Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloneFromSheet(
    profiles: List<Pair<String, String>>?,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            ChoiceRow("Nothing — fresh install", current == null) { onPick(null) }
            profiles?.forEach { (value, label) ->
                ChoiceRow(label, current == value) { onPick(value) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, "Selected")
    }
}
