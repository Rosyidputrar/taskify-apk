package com.rosyidputrar.taskify.autofill

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec
import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import com.rosyidputrar.taskify.core.domain.sandi.AutofillMatch
import com.rosyidputrar.taskify.core.domain.sandi.AutofillTarget
import com.rosyidputrar.taskify.core.domain.sandi.MatchStrength
import com.rosyidputrar.taskify.core.domain.sandi.TrustState
import com.rosyidputrar.taskify.core.domain.sandi.trustState
import com.rosyidputrar.taskify.core.sandi.DeviceAuthException
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiSession
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import com.rosyidputrar.taskify.core.sandi.VaultStatus
import com.rosyidputrar.taskify.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Kredensial dari formulir yang menunggu brankas dibuka untuk disimpan (memori proses saja). */
data class PendingSave(val target: AutofillTarget, val username: String, val password: String) {
    /** Jangan pernah mencetak sandi ke log (data class mencetak semua field). */
    override fun toString(): String = "PendingSave(target=$target, username=$username, password=•••)"
}

internal object PendingSaves {
    private const val TTL_MS = 10 * 60_000L
    private val map = ConcurrentHashMap<String, Pair<Long, PendingSave>>()

    private fun purgeExpired(now: Long = System.currentTimeMillis()) {
        map.entries.removeIf { now - it.value.first > TTL_MS }
    }

    fun put(save: PendingSave): String {
        purgeExpired()
        return UUID.randomUUID().toString().also { map[it] = System.currentTimeMillis() to save }
    }

    fun take(token: String?): PendingSave? {
        purgeExpired()
        return token?.let { map.remove(it) }?.takeIf { System.currentTimeMillis() - it.first <= TTL_MS }?.second
    }

    /** Hapus semua kredensial tertunda — dipanggil saat brankas dikunci. */
    fun clear() {
        map.clear()
    }
}

enum class AutofillMode { Fill, Save }

enum class VaultPhase { Loading, NoVault, Locked, Unlocked }

data class AutofillUiState(
    val mode: AutofillMode,
    val target: AutofillTarget,
    val phase: VaultPhase = VaultPhase.Loading,
    val canUseDevice: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val items: List<SandiItem> = emptyList(),
    val matches: List<SandiItem> = emptyList(),
    val query: String = "",
    /** Mode simpan. */
    val pending: PendingSave? = null,
    val saveTarget: SandiItem? = null,
    val saved: Boolean = false,
) {
    /** Item yang ditampilkan di bawah "Semua item" (tanpa yang sudah ada di "Cocok"). */
    val others: List<SandiItem>
        get() {
            val q = query.trim().lowercase()
            val pool = if (q.isEmpty()) items.filter { it !in matches } else items
            return pool.filter { q.isEmpty() || it.payload.title.lowercase().contains(q) || it.payload.username.lowercase().contains(q) || it.payload.url.lowercase().contains(q) }
                .sortedWith(compareByDescending<SandiItem> { it.payload.favorite }.thenBy { it.payload.title.lowercase() })
        }
}

interface AutofillActions {
    fun unlock(password: String) {}
    fun unlockWithDevice() {}
    fun query(text: String) {}
    fun pick(item: SandiItem) {}
    fun save() {}
    fun cancel() {}
}

class AutofillViewModel(
    private val sandi: SandiService,
    private val vault: AutofillVault,
    mode: AutofillMode,
    target: AutofillTarget,
    pending: PendingSave?,
) : ViewModel() {
    private val _state = MutableStateFlow(AutofillUiState(mode, target, pending = pending))
    val state: StateFlow<AutofillUiState> = _state.asStateFlow()
    private var autoPrompted = false

    init {
        // Brankas yang sudah lewat batas kunci otomatis dikunci dulu, tidak dipakai.
        sandi.session.openForAutofill()
        viewModelScope.launch {
            combine(sandi.status, sandi.items) { s, i -> s to i }.collect { (status, decrypted) ->
                val phase = when (status) {
                    VaultStatus.Loading -> VaultPhase.Loading
                    VaultStatus.NoVault -> VaultPhase.NoVault
                    is VaultStatus.Locked -> VaultPhase.Locked
                    is VaultStatus.Unlocked -> if (decrypted.ready) VaultPhase.Unlocked else VaultPhase.Loading
                }
                val canDevice = status is VaultStatus.Locked && sandi.trustAvailable() &&
                    sandi.trustMeta()?.let { trustState(it, status.key.record.wrappedKey, System.currentTimeMillis()) == TrustState.Ok } == true
                val items = decrypted.items.filter { it.payload.password.isNotEmpty() || it.payload.username.isNotEmpty() }
                _state.update { st ->
                    val matches = vault.matches(items, st.target)
                    st.copy(
                        phase = phase,
                        canUseDevice = canDevice,
                        items = items,
                        matches = matches,
                        saveTarget = st.pending?.let { p -> matches.firstOrNull { it.payload.username == p.username } },
                    )
                }
                // Brankas dikunci (lagi) selagi jendela ini terbuka: kredensial tertunda yang
                // belum diambil jangan bertahan melewati kunci.
                if (phase == VaultPhase.Locked) PendingSaves.clear()
                if (canDevice && !autoPrompted) {
                    autoPrompted = true
                    unlockWithDevice()
                }
            }
        }
    }

    fun unlock(password: String) {
        if (password.isEmpty()) return
        run {
            when (sandi.unlock(password, remember = false)) {
                SandiService.UnlockOutcome.WrongPassword -> _state.update { it.copy(error = "Master password salah.") }
                is SandiService.UnlockOutcome.Unlocked -> Unit
            }
        }
    }

    fun unlockWithDevice() = run {
        try {
            // unlockTrusted() mengembalikan false (bukan exception) bila tidak ada kunci / kunci
            // perangkat gagal dibaca: jangan diam — beri tahu pengguna.
            if (!sandi.unlockTrusted()) {
                _state.update { it.copy(error = "Tidak bisa membuka dengan sidik jari / kunci layar. Coba master password.") }
            }
        } catch (e: DeviceAuthException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun query(text: String) {
        sandi.session.touch() // interaksi picker menunda kunci idle
        _state.update { it.copy(query = text) }
    }

    /** Simpan kredensial tertunda; [onDone] dipanggil setelah berhasil. */
    fun save(onDone: () -> Unit) {
        val p = _state.value.pending ?: return
        sandi.session.touch()
        run {
            vault.save(_state.value.items, p.target, p.username, p.password)
            _state.update { it.copy(saved = true) }
            onDone()
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: SandiUserException) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Terjadi kesalahan.") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

/**
 * Jendela Isi Otomatis Sandi: buka brankas (sidik jari / master password), pilih item untuk
 * diisi, atau simpan sandi baru dari formulir. Jendela aman (FLAG_SECURE) dan tidak masuk Recents.
 */
class SandiAutofillActivity : FragmentActivity() {
    private val sandi: SandiService by inject()
    private val vault: AutofillVault by inject()
    private val prefs: AppPreferences by inject()

    private var form: ParsedForm? = null
    private var pending: PendingSave? = null

    private val viewModel: AutofillViewModel by viewModels {
        viewModelFactory {
            initializer {
                val target = form?.target ?: pending!!.target
                AutofillViewModel(sandi, vault, if (pending != null) AutofillMode.Save else AutofillMode.Fill, target, pending)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        when (intent.action) {
            ACTION_FILL -> form = AutofillParser.parse(structure(intent))
            ACTION_SAVE -> pending = PendingSaves.take(intent.getStringExtra(EXTRA_TOKEN))
        }
        if (form == null && pending == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val snapshot by prefs.snapshot.collectAsState(null)
            val dark = snapshot?.themeMode?.isDark() ?: isSystemInDarkTheme()
            TaskifyTheme(darkTheme = dark, spec = snapshot?.theme ?: ThemeSpec.Default) {
                val state by viewModel.state.collectAsState()
                AutofillContent(state, object : AutofillActions {
                    override fun unlock(password: String) = viewModel.unlock(password)
                    override fun unlockWithDevice() = viewModel.unlockWithDevice()
                    override fun query(text: String) = viewModel.query(text)
                    override fun pick(item: SandiItem) = fill(item)
                    override fun save() = viewModel.save { finishWith(RESULT_OK, null) }
                    override fun cancel() = finishWith(RESULT_CANCELED, null)
                })
            }
        }
    }

    private fun fill(item: SandiItem) {
        val f = form ?: return
        // TOCTOU: dataset snapshot dari buildResponse() bisa sudah basi — brankas mungkin sudah
        // terkunci antara layanan membangun daftar dan pengguna memilih item. Periksa ulang di
        // sini, dan tolak (bukan isi) bila brankas sudah tidak terbuka.
        if (!canFillNow(sandi.session)) {
            finishWith(RESULT_CANCELED, null)
            return
        }
        sandi.session.touch()
        val dataset = AutofillDatasets.item(this, f, item.payload)
        finishWith(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
    }

    private fun finishWith(code: Int, data: Intent?) {
        setResult(code, data)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun structure(intent: Intent): AssistStructure? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        else intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)

    companion object {
        const val ACTION_FILL = "com.rosyidputrar.taskify.autofill.FILL"
        const val ACTION_SAVE = "com.rosyidputrar.taskify.autofill.SAVE"
        private const val EXTRA_TOKEN = "token"

        fun fillSender(context: Context) =
            AutofillDatasets.sender(context, Intent(context, SandiAutofillActivity::class.java).setAction(ACTION_FILL), 1, mutable = true)

        fun saveSender(context: Context, save: PendingSave) = AutofillDatasets.sender(
            context,
            Intent(context, SandiAutofillActivity::class.java).setAction(ACTION_SAVE).putExtra(EXTRA_TOKEN, PendingSaves.put(save)),
            2, mutable = false,
        )

        /**
         * Predikat yang dipakai [SandiAutofillActivity.fill]: brankas harus MASIH terbuka saat
         * item dipilih (TOCTOU). `false` bila terkunci atau sudah lewat batas kunci otomatis.
         */
        internal fun canFillNow(session: SandiSession): Boolean = session.openForAutofill() != null

        /** Item yang dipilih manual tetapi tidak cocok dengan target → minta konfirmasi (anti-phishing). */
        fun needsConfirm(item: SandiItem, target: AutofillTarget): Boolean =
            AutofillMatch.strength(item.payload, target) == MatchStrength.None
    }
}
