package com.rosyidputrar.taskify.core.innertube.pages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser saran — cermin tes `parseSuggestions` Worker. */
class SuggestionPageTest {

    @Test fun `sepuluh saran dari fixture 6 query 4 entitas`() {
        val list = parseSuggestions(FixtureHelper.json("suggest"))
        assertEquals(10, list.size)
        val queries = list.filter { it.type == "query" }
        val entities = list.filter { it.type != "query" }
        assertEquals(6, queries.size)
        assertEquals(4, entities.size)
    }

    @Test fun `entitas artis Tulus dan video Teh Hijau terbaca`() {
        val list = parseSuggestions(FixtureHelper.json("suggest"))
        val artist = list.firstOrNull { it.type == "artist" }
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", artist?.browseId)
        val video = list.firstOrNull { it.videoId == "5F28ye50-Kc" }
        assertEquals("video", video?.type)
        assertTrue(video?.text?.contains("Teh Hijau") == true)
    }

    @Test fun `respons kosong menghasilkan daftar kosong bukan error`() {
        assertTrue(parseSuggestions(FixtureHelper.json("next-empty")).isEmpty())
    }

    @Test fun `seksi tanpa isi dikenali dilewati`() {
        val raw = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"contents":[{"searchSuggestionsSectionRenderer":{"contents":[{"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"tulus"}]}}}]}}]}""",
        )
        val list = parseSuggestions(raw)
        assertEquals(1, list.size)
        assertEquals("tulus", list[0].text)
        assertEquals("query", list[0].type)
    }
}
