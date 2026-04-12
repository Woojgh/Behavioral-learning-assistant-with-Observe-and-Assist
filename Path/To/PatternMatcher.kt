// Fuzzy matching implementation in PatternMatcher.kt

class PatternMatcher {
    // ... existing code ...

    // Method to calculate Levenshtein distance
    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    // Method to calculate Jaro-Winkler similarity
    fun jaroWinkler(s1: String, s2: String): Double {
        // ... implementation of Jaro-Winkler ...
        return similarity
    }

    // Improved confidence scoring
    fun calculateConfidenceScore(pattern: String, matchString: String): Double {
        val levenshteinScore = 1 - (levenshteinDistance(pattern, matchString) / maxOf(pattern.length, matchString.length).toDouble())
        val jaroWinklerScore = jaroWinkler(pattern, matchString)

        return (levenshteinScore + jaroWinklerScore) / 2
    }

    // Package-aware pattern matching
    fun matchPattern(pattern: String, input: String, packageContext: String): Boolean {
        return input.startsWith(pattern) && input.contains(packageContext)
    }
}