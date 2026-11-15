package com.faust.easychat.util;

/**
 * Shared token extraction and normalization helpers for security filters and auth services.
 */
public final class TokenUtils {

    private static final String BEARER_PREFIX = "Bearer";

    private TokenUtils() {
    }

    /**
     * Extracts and trims a Bearer token from an {@code Authorization} header.
     *
     * @return the normalized non-blank token, or {@code null} if missing, not Bearer, or blank
     */
    public static String extractBearerToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String trimmed = authHeader.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()
                || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || !Character.isWhitespace(trimmed.charAt(BEARER_PREFIX.length()))) {
            return null;
        }
        String token = trimmed.substring(BEARER_PREFIX.length() + 1).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Normalizes a raw token string (such as a refresh token) by trimming whitespace
     * and stripping one matching pair of surrounding double quotes.
     *
     * @return the normalized non-blank token, or {@code null} if null or blank
     */
    public static String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String token = rawToken.trim();
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1).trim();
        }
        return token.isEmpty() ? null : token;
    }
}
