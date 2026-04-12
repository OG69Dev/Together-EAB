package dev.og69.eab.update

import dev.og69.eab.BuildConfig

/**
 * In-app updates from GitHub Releases. Values are read from the project **local.properties** file
 * (not committed) and exposed via [BuildConfig] in `app/build.gradle.kts`.
 *
 * Example for repo `OG69Dev/Together-EAB` (see `https://github.com/OG69Dev/Together-EAB`):
 * ```
 * github.owner=OG69Dev
 * github.repo=Together-EAB
 * ```
 *
 * Optional for a private repo or higher API rate limits:
 * ```
 * github.token=ghp_your_token_here
 * ```
 *
 * [isConfigured] is false until `github.owner` and `github.repo` are non-blank. Publish at least one
 * GitHub Release with an `.apk` asset; the app uses the latest release’s first APK.
 */
object UpdateConfig {
    val owner: String get() = BuildConfig.GITHUB_OWNER.trim()
    val repo: String get() = BuildConfig.GITHUB_REPO.trim()
    val token: String get() = BuildConfig.GITHUB_TOKEN.trim()

    fun isConfigured(): Boolean = owner.isNotEmpty() && repo.isNotEmpty()
}
