package dev.og69.eab.update

import dev.og69.eab.BuildConfig

object VersionComparator {

    /** Strip leading "v" / "V" from release tags. */
    fun normalizeTag(tag: String): String {
        val t = tag.trim()
        if (t.length > 1 && (t[0] == 'v' || t[0] == 'V') && t[1].isDigit()) {
            return t.substring(1)
        }
        return t
    }

    /**
     * @return negative if [remoteVersion] is older than current app, 0 if equal, positive if remote is newer.
     */
    fun compareToCurrent(remoteVersion: String): Int {
        val current = BuildConfig.VERSION_NAME.trim()
        return compareSemver(normalizeTag(remoteVersion), current)
    }

    fun isRemoteNewer(remoteTag: String): Boolean = compareToCurrent(remoteTag) > 0

    /**
     * Compare two semver-like strings (major.minor.patch…); non-numeric segments treated as 0.
     */
    fun compareSemver(a: String, b: String): Int {
        val pa = a.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val pb = b.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            when {
                va > vb -> return 1
                va < vb -> return -1
            }
        }
        return 0
    }
}
