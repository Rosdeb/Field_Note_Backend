package com.task.fieldnotes.common;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

public final class Digests {
    private Digests() {}
    public static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
