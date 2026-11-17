/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `core/.../innertube/models/YouTubeLocale.kt`
// (v15.1.0, c7f0e24) — hanya paket yang diubah ke Taskify. Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String, // geolocation
    val hl: String, // host language
)
