package com.faust.easychat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TokenUtilsTest {

    @Test
    void extractBearerToken_handlesValidAndWhitespaceVariants() {
        assertEquals("abc.def.ghi", TokenUtils.extractBearerToken("Bearer abc.def.ghi"));
        assertEquals("abc.def.ghi", TokenUtils.extractBearerToken("Bearer   abc.def.ghi  "));
        assertEquals("abc.def.ghi", TokenUtils.extractBearerToken("  Bearer  abc.def.ghi  "));
        assertEquals("abc.def.ghi", TokenUtils.extractBearerToken("bearer abc.def.ghi"));
        assertEquals("abc.def.ghi", TokenUtils.extractBearerToken("Bearer\tabc.def.ghi"));
    }

    @Test
    void extractBearerToken_returnsNullForMissingNonBearerOrBlank() {
        assertNull(TokenUtils.extractBearerToken(null));
        assertNull(TokenUtils.extractBearerToken(""));
        assertNull(TokenUtils.extractBearerToken("   "));
        assertNull(TokenUtils.extractBearerToken("Bearer"));
        assertNull(TokenUtils.extractBearerToken("Bearer "));
        assertNull(TokenUtils.extractBearerToken("Bearer   \t  "));
        assertNull(TokenUtils.extractBearerToken("BearerXabc"));
        assertNull(TokenUtils.extractBearerToken("Basic abc"));
    }

    @Test
    void normalizeToken_trimsAndStripsSurroundingQuotes() {
        assertEquals("refresh-1", TokenUtils.normalizeToken("refresh-1"));
        assertEquals("refresh-1", TokenUtils.normalizeToken("  refresh-1  "));
        assertEquals("refresh-1", TokenUtils.normalizeToken("\"refresh-1\""));
        assertEquals("refresh-1", TokenUtils.normalizeToken("  \"  refresh-1  \"  "));
        assertEquals("\"unmatched", TokenUtils.normalizeToken("\"unmatched"));
    }

    @Test
    void normalizeToken_returnsNullForBlankOrQuotedBlank() {
        assertNull(TokenUtils.normalizeToken(null));
        assertNull(TokenUtils.normalizeToken(""));
        assertNull(TokenUtils.normalizeToken("   "));
        assertNull(TokenUtils.normalizeToken("\"\""));
        assertNull(TokenUtils.normalizeToken("  \"   \"  "));
    }
}
