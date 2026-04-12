package com.example.aiassistant;

public class StringSimilarity {

    // Levenshtein distance algorithm
    public static int levenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j; // cost of insertions
                } else if (j == 0) {
                    dp[i][j] = i; // cost of deletions
                } else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), 
                                        dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1));
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // Jaro-Winkler similarity algorithm
    public static double jaroWinkler(String s1, String s2) {
        int jaroDistance = jaroDistance(s1, s2);
        int prefixLength = getCommonPrefixLength(s1, s2);
        return jaroDistance + (0.1 * prefixLength * (1 - jaroDistance));
    }

    private static int getCommonPrefixLength(String s1, String s2) {
        int maxPrefixLength = 4;
        int commonPrefix = 0;
        for (int i = 0; i < Math.min(s1.length(), s2.length()) && i < maxPrefixLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                commonPrefix++;
            } else {
                break;
            }
        }
        return commonPrefix;
    }

    private static double jaroDistance(String s1, String s2) {
        // Implementation of Jaro distance algorithm
        // ... (Implementation here)
        return 0; // Placeholder
    }
}