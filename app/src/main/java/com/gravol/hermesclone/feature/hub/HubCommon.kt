package com.gravol.hermesclone.feature.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gravol.hermesclone.core.network.DashboardApiClient
import com.gravol.hermesclone.core.network.NetworkResult
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// ─────────────────────────────────────────────────────────────
// v0.2.7 Bot Hub — shared building blocks (official parity)
// ─────────────────────────────────────────────────────────────

/** Official cards sit on #0C1A3C over the #000022 bg; sections use small caps headers. */
val HubCardShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
            )
        },
    ) { pad ->
        content(Modifier.padding(pad))
    }
}

@Composable
fun HubSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** One tappable card row: icon · label · trailing value · chevron. */
@Composable
fun HubRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    sub: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = HubCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (sub != null) {
                    Text(
                        sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Text(
                    value, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Card that groups several HubRows with no gaps (Integrations/Work/Agent cards). */
@Composable
fun HubCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = HubCardShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(content = content)
    }
}

/** Row inside a HubCard (no own surface). */
@Composable
fun HubInnerRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── JSON access helpers (tolerant, the gateway shapes vary per version) ──

fun JsonElement.str(key: String): String? =
    (this as? JsonObject)?.get(key)?.let { if (it is JsonNull) null else it.jsonPrimitive.contentOrNull }

fun JsonElement.num(key: String): Double? =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

fun JsonElement.bool(key: String): Boolean? =
    (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

fun JsonElement.arr(key: String): JsonArray =
    (this as? JsonObject)?.get(key) as? JsonArray ?: JsonArray(emptyList())

val JsonElement.itemsArray: JsonArray
    get() = if (this is JsonArray) this else arr("items")

// ── Small shared state helper ──

sealed interface Load<T> {
    data class Loading<T>(val value: T? = null) : Load<T>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed<T>(val message: String, val value: T? = null) : Load<T>
}

/** Load once on first composition; returns state + a reload trigger. */
@Composable
fun <T> rememberLoad(
    key: Any? = Unit,
    load: suspend () -> Pair<T?, String?>,
): Pair<Load<T>?, () -> Unit> {
    var state by remember(key) { mutableStateOf<Load<T>?>(null) }
    var tick by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key, tick) {
        state = Load.Loading()
        val (value, err) = load()
        state = when {
            value != null -> Load.Ready(value)
            else -> Load.Failed(err ?: "Could not load", null)
        }
    }
    return state to { tick++ }
}

/** Toast-free inline action feedback: run an apiSend, return error string or null. */
suspend fun apiCall(path: String, method: String, body: String? = null): String? =
    when (val r = DashboardApiClient.apiSend(path, method, body)) {
        is NetworkResult.Success -> null
        is NetworkResult.HttpError -> "HTTP ${r.code}"
        is NetworkResult.Error -> r.exception.message ?: "network error"
    }

