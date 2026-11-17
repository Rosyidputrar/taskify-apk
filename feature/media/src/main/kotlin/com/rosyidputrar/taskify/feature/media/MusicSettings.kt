package com.rosyidputrar.taskify.feature.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.media.MusicClient
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.RadioStations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSettingsSheet(state: MusicUiState, actions: MusicActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MusicSettingsContent(state, actions)
    }
}

@Composable
fun MusicSettingsContent(state: MusicUiState, actions: MusicActions) {
    val s = state.settings
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Pengaturan musik", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
        Text("KUALITAS AUDIO", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        MusicQuality.entries.forEach { q ->
            Row(Modifier.fillMaxWidth().selectable(s.quality == q, role = Role.RadioButton) { actions.setQuality(q) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(s.quality == q, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(q.label, style = MaterialTheme.typography.bodyLarge)
                    Text(q.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Berlaku untuk lagu berikutnya. Jalur konverter MP3 selalu memakai bitrate bawaannya.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("PEMUTARAN", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        SettingSwitch("Antrean lagu serupa", "Memutar satu lagu dari Cari atau Jelajah mengisi antrean dengan lagu serupa. Matikan untuk memutar seluruh daftar hasil.",
            s.similarQueue, actions::toggleSimilarQueue)
        SettingSwitch("Ratakan volume", "Lagu yang keras dilirihkan supaya volume antar lagu seragam.", s.normalize, actions::toggleNormalize)
        SettingSwitch("Lewati sponsor & intro (SponsorBlock)", "Melompati bagian non-musik dari data komunitas. Hanya saat audio bisa digeser.", s.sponsorBlock, actions::toggleSponsorBlock)
        SettingSwitch("Selalu pakai MP3 (konverter)", "Untuk perangkat yang gagal memutar opus/webm. Lebih lambat disiapkan dan tidak bisa digeser.", s.forceMp3, actions::toggleForceMp3)
        SettingSwitch("Arsipkan hasil konversi", "Menyimpan MP3 hasil konversi di server supaya bisa digeser dan cepat diputar ulang.", s.archive, actions::toggleArchive)
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch) { onToggle() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

// ------------------------------------------------------------------ Mode Fokus

data class FocusMusicState(val configured: Boolean = true, val stations: List<RadioStation> = RadioStations.FALLBACK, val playingId: String? = null, val playing: Boolean = false)

interface FocusMusicActions {
    fun toggle(station: RadioStation) {}
}

/** Pilihan "Musik fokus" di Mode Fokus: stasiun radio langsung tanpa vokal didahulukan. */
class FocusMusicViewModel(private val player: MusicPlayer, private val client: MusicClient) : ViewModel(), FocusMusicActions {
    private val stations = MutableStateFlow(RadioStations.FALLBACK)

    val state: StateFlow<FocusMusicState> = combine(stations, player.state) { st, p ->
        val cur = p.current?.takeIf { it.live }
        FocusMusicState(player.configured, st.sortedBy { it.vocals }, cur?.videoId?.removePrefix("live:"), cur != null && p.status == YtStatus.Playing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusMusicState(player.configured))

    init {
        if (player.configured) launchSafe { runCatching { client.stations() }.getOrNull()?.let { stations.value = it } }
    }

    override fun toggle(station: RadioStation) {
        val s = state.value
        when {
            s.playingId == station.id && s.playing -> player.togglePlay()
            s.playingId == station.id -> player.togglePlay()
            else -> player.playStation(station)
        }
    }
}

@Composable
fun FocusMusicCard(modifier: Modifier = Modifier) {
    val vm: FocusMusicViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.configured) return
    FocusMusicContent(state, vm, modifier)
}

/** Seksi "MUSIK FOKUS" bergaya sama dengan seksi lain di Mode Fokus (label indeks + isi polos, tanpa kartu). */
@Composable
fun FocusMusicContent(state: FocusMusicState, actions: FocusMusicActions, modifier: Modifier = Modifier) {
    val now = state.stations.firstOrNull { it.id == state.playingId }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MUSIK FOKUS", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Radio, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Music), modifier = Modifier.size(18.dp))
            Text(now?.let { (if (state.playing) "Memutar " else "Dijeda · ") + it.name + " · " + it.provider } ?: "Radio langsung tanpa iklan — ketuk untuk memutar",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.stations.forEach { st ->
                FilterChip(st.id == state.playingId && state.playing, onClick = { actions.toggle(st) }, label = { Text(st.name + if (st.vocals) " · vokal" else "") })
            }
        }
    }
}
