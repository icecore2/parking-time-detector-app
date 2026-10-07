package com.parktimedetector.update

/**
 * Utility for parsing and comparing application version strings and version codes.
 */
object VersionComparator {

    /**
     * Compares two version strings (e.g., "1.0", "v1.1", "1.0.2.1", "2.0-beta").
     * Returns:
     *   > 0 if [targetVersion] is strictly newer than [currentVersion]
     *   < 0 if [targetVersion] is older than [currentVersion]
     *   0 if both are identical in version components
     */
    fun compare(currentVersion: String, targetVersion: String): Int {
        val clean1 = cleanVersion(currentVersion)
        val clean2 = cleanVersion(targetVersion)

        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p2 != p1) {
                return p2.compareTo(p1)
            }
        }
        return 0
    }

    /**
     * Checks if target version represents a newer update than current installed version.
     * Uses [targetCode] vs [currentCode] if both are provided and positive,
     * otherwise falls back to semantic version string comparison.
     */
    fun isUpdateAvailable(
        currentVersion: String,
        currentCode: Int,
        targetVersion: String,
        targetCode: Int? = null
    ): Boolean {
        if (targetCode != null && targetCode > 0 && currentCode > 0) {
            if (targetCode > currentCode) return true
            if (targetCode < currentCode) return false
        }
        return compare(currentVersion, targetVersion) > 0
    }

    private fun cleanVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split("-", "_", "+")[0]
            .trim()
    }
}
