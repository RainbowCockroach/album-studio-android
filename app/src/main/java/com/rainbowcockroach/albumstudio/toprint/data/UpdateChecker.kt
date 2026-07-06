package com.rainbowcockroach.albumstudio.toprint.data

import com.rainbowcockroach.albumstudio.toprint.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Outcome of an update check against the GitHub Releases API. */
sealed interface UpdateStatus {
    /** A newer release exists. [releaseUrl] is the human page to open in a browser. */
    data class Available(val versionName: String, val releaseUrl: String) : UpdateStatus

    /** Installed build is the latest published release. */
    data object UpToDate : UpdateStatus

    /** Check could not complete (offline, rate-limited, no releases yet, …). */
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Checks whether a newer APK has been published to the repo's GitHub Releases.
 *
 * The CI workflow keeps exactly one release, tagged `v1.0.<versionCode>` (see
 * `.github/workflows/release.yml`). We parse the trailing integer of the tag and compare
 * it to [BuildConfig.VERSION_CODE] — an integer comparison, so it's robust regardless of
 * how the version *name* is formatted. The repo is public, so the call is anonymous
 * (GitHub allows 60 unauthenticated requests/hour/IP — far more than this app needs).
 */
class UpdateChecker(private val client: OkHttpClient = defaultClient()) {

    fun check(): UpdateStatus = try {
        val url = "https://api.github.com/repos/" +
            "${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> UpdateStatus.UpToDate // no releases published yet
                !response.isSuccessful -> UpdateStatus.Failed("GitHub returned HTTP ${response.code}")
                else -> classify(response.body?.string().orEmpty())
            }
        }
    } catch (e: Exception) {
        UpdateStatus.Failed(e.message ?: "Network error")
    }

    private fun classify(body: String): UpdateStatus = try {
        val json = JSONObject(body)
        val tag = json.getString("tag_name")                       // e.g. "v1.0.7"
        val htmlUrl = json.optString("html_url").ifBlank {
            "https://github.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        }
        val latestCode = versionCodeOf(tag)
        if (latestCode != null && latestCode > BuildConfig.VERSION_CODE) {
            UpdateStatus.Available(tag.removePrefix("v"), htmlUrl)
        } else {
            UpdateStatus.UpToDate
        }
    } catch (e: Exception) {
        UpdateStatus.Failed("Malformed release response")
    }

    /** Trailing integer of a `v1.0.<code>` tag == the APK's versionCode. */
    private fun versionCodeOf(tag: String): Int? =
        Regex("(\\d+)\\s*$").find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        /** Installed version, for display in Settings ("Version 1.0.7"). */
        val installedVersionName: String get() = BuildConfig.VERSION_NAME
    }
}
