/* API protocol adapted from BiliSponsorSkip SponsorBlockClient.kt (GPL-3.0).
 * Read-only integration; see THIRD_PARTY_NOTICES.md. */
package com.xjw.bilifix.in.feature.sponsor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

final class SponsorClient {
    private volatile HttpURLConnection active;
    List<SponsorSegments.Segment> fetch(VideoKey key) throws Exception {
        String bvid = key.bvid();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bvid.getBytes(StandardCharsets.UTF_8));
        String prefix = String.format(java.util.Locale.ROOT, "%02x%02x", hash[0] & 255, hash[1] & 255);
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://www.bsbsb.top/api/skipSegments/" + prefix).openConnection();
        active = connection;
        try {
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Origin", "BiliSponsorSkip");
            connection.setRequestProperty("X-Ext-Version", "0.13.0");
            connection.setRequestProperty("User-Agent", "BiliFix-SponsorSkip/1");
            int status = connection.getResponseCode();
            if (status == 404) return Collections.emptyList();
            if (status != 200) throw new java.io.IOException("HTTP " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (output.size() + size > 2 * 1024 * 1024) throw new java.io.IOException("Segment response too large");
                    output.write(buffer, 0, size);
                }
                return SponsorSegments.parse(output.toString("UTF-8"), bvid, key.cid);
            }
        } finally {
            active = null;
            connection.disconnect();
        }
    }
    void cancel() { HttpURLConnection connection = active; if (connection != null) connection.disconnect(); }
}
