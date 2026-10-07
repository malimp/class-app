package com.example.classapp;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/** Text helpers: Persian/Arabic normalization for search and matching, and file decoding. */
final class Text {
    private Text() {}

    /** Normalizes text for comparison only (never for display). */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u064A' || c == '\u0649') c = '\u06CC';          // ي ى -> ی
            else if (c == '\u0643') c = '\u06A9';                      // ك -> ک
            else if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            else if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            else if (c == '\u200C' || c == '\u00A0') c = ' ';          // ZWNJ / NBSP -> space
            else if (c == '\u200D' || c == '\u200E' || c == '\u200F' || c == '\u0640'
                    || c == '\u0670' || c == '\uFEFF' || (c >= '\u064B' && c <= '\u0652')) continue;
            b.append(c);
        }
        return b.toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Converts Persian/Arabic digits to ASCII digits and trims; other characters are untouched. */
    static String digits(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u06F0' && c <= '\u06F9') c = (char) ('0' + (c - '\u06F0'));
            else if (c >= '\u0660' && c <= '\u0669') c = (char) ('0' + (c - '\u0660'));
            b.append(c);
        }
        return b.toString().trim();
    }

    /** Decodes a text file: UTF-8 (BOM allowed); falls back to windows-1256 for old Persian files. */
    static String decode(byte[] bytes) {
        int off = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) off = 3;
        try {
            return Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, off, bytes.length - off)).toString();
        } catch (CharacterCodingException e) {
            try {
                return new String(bytes, off, bytes.length - off, Charset.forName("windows-1256"));
            } catch (RuntimeException e2) {
                return new String(bytes, off, bytes.length - off, Charset.forName("ISO-8859-1"));
            }
        }
    }
}
