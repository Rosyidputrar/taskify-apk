package com.rosyidputrar.taskify.ui.tools

import android.content.Context
import androidx.core.content.edit
import com.rosyidputrar.taskify.core.domain.tools.ChatStory
import com.rosyidputrar.taskify.core.domain.tools.ChatTurn
import com.rosyidputrar.taskify.core.domain.tools.ChatVisualTheme
import com.rosyidputrar.taskify.core.domain.tools.TypingDifficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ChatTypeSavedData(
    val theme: ChatVisualTheme = ChatVisualTheme.Paper,
    val bestWpm: Map<String, Int> = emptyMap(),
    val bestAccuracy: Map<String, Int> = emptyMap(),
    val completedCount: Int = 0,
    val customStories: List<ChatStory> = emptyList(),
)

/**
 * Penyimpanan lokal untuk Modul Tools › ChatType:
 * menyimpan tema pilihan, rekor WPM & akurasi per cerita, serta cerita hasil generate AI.
 */
class ChatTypeStore(context: Context) {
    private val prefs = context.getSharedPreferences("taskify_chattype", Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(read())
    val data: StateFlow<ChatTypeSavedData> = _data.asStateFlow()

    fun setTheme(theme: ChatVisualTheme) {
        prefs.edit { putString(K_THEME, theme.name) }
        _data.value = _data.value.copy(theme = theme)
    }

    fun recordResult(storyId: String, wpm: Int, accuracyPct: Int) {
        val cur = _data.value
        val nextWpm = cur.bestWpm.toMutableMap()
        val nextAcc = cur.bestAccuracy.toMutableMap()
        if (wpm > (nextWpm[storyId] ?: 0)) nextWpm[storyId] = wpm
        if (accuracyPct > (nextAcc[storyId] ?: 0)) nextAcc[storyId] = accuracyPct
        val count = cur.completedCount + 1
        prefs.edit {
            putStringSet(K_BEST_WPM, nextWpm.map { (k, v) -> "$k=$v" }.toSet())
            putStringSet(K_BEST_ACC, nextAcc.map { (k, v) -> "$k=$v" }.toSet())
            putInt(K_COMPLETED, count)
        }
        _data.value = cur.copy(bestWpm = nextWpm, bestAccuracy = nextAcc, completedCount = count)
    }

    fun saveCustomStory(story: ChatStory) {
        val cur = _data.value
        val updated = (listOf(story) + cur.customStories.filterNot { it.id == story.id }).take(MAX_CUSTOM_STORIES)
        prefs.edit { putString(K_CUSTOM_STORIES, encodeStories(updated)) }
        _data.value = cur.copy(customStories = updated)
    }

    fun deleteCustomStory(storyId: String) {
        val cur = _data.value
        val updated = cur.customStories.filterNot { it.id == storyId }
        prefs.edit { putString(K_CUSTOM_STORIES, encodeStories(updated)) }
        _data.value = cur.copy(customStories = updated)
    }

    /** Keluar akun: hapus semua rekor & cerita kustom di perangkat ini. */
    fun clear() {
        prefs.edit { clear() }
        _data.value = ChatTypeSavedData()
    }

    private fun read(): ChatTypeSavedData {
        val theme = prefs.getString(K_THEME, null)?.let { n ->
            ChatVisualTheme.entries.firstOrNull { it.name == n }
        } ?: ChatVisualTheme.Paper
        val wpm = prefs.getStringSet(K_BEST_WPM, emptySet()).orEmpty().mapNotNull { e ->
            e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: return@mapNotNull null) }
        }.toMap()
        val acc = prefs.getStringSet(K_BEST_ACC, emptySet()).orEmpty().mapNotNull { e ->
            e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: return@mapNotNull null) }
        }.toMap()
        val completed = prefs.getInt(K_COMPLETED, 0)
        val custom = decodeStories(prefs.getString(K_CUSTOM_STORIES, null))
        return ChatTypeSavedData(theme, wpm, acc, completed, custom)
    }

    private fun encodeStories(stories: List<ChatStory>): String {
        val arr = JSONArray()
        for (s in stories) {
            val turnsArr = JSONArray()
            for (t in s.turns) {
                turnsArr.put(JSONObject().put("partner", t.partnerMessage).put("reply", t.targetReply))
            }
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("title", s.title)
                    .put("category", s.category)
                    .put("partnerName", s.partnerName)
                    .put("partnerRole", s.partnerRole)
                    .put("difficulty", s.difficulty.name)
                    .put("theme", s.suggestedTheme.name)
                    .put("turns", turnsArr),
            )
        }
        return arr.toString()
    }

    private fun decodeStories(raw: String?): List<ChatStory> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val turnsArr = obj.optJSONArray("turns") ?: continue
                    val turns = buildList {
                        for (j in 0 until turnsArr.length()) {
                            val t = turnsArr.optJSONObject(j) ?: continue
                            val p = t.optString("partner").trim()
                            val r = t.optString("reply").trim()
                            if (p.isNotEmpty() && r.isNotEmpty()) add(ChatTurn(p, r))
                        }
                    }
                    if (turns.isEmpty()) continue
                    val diff = TypingDifficulty.entries.firstOrNull { it.name == obj.optString("difficulty") } ?: TypingDifficulty.Standard
                    val theme = ChatVisualTheme.entries.firstOrNull { it.name == obj.optString("theme") } ?: ChatVisualTheme.Messenger
                    add(
                        ChatStory(
                            id = obj.optString("id").ifEmpty { "custom-$i" },
                            title = obj.optString("title").ifEmpty { "Cerita AI" },
                            category = obj.optString("category").ifEmpty { "Kreasi AI" },
                            partnerName = obj.optString("partnerName").ifEmpty { "Rekan Chat" },
                            partnerRole = obj.optString("partnerRole").ifEmpty { "Lawan Bicara" },
                            difficulty = diff,
                            suggestedTheme = theme,
                            turns = turns,
                            isAiGenerated = true,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val K_THEME = "theme"
        const val K_BEST_WPM = "best_wpm"
        const val K_BEST_ACC = "best_acc"
        const val K_COMPLETED = "completed_count"
        const val K_CUSTOM_STORIES = "custom_stories_json"
        const val MAX_CUSTOM_STORIES = 30
    }
}
