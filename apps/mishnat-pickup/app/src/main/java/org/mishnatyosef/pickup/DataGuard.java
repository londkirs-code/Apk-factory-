package org.mishnatyosef.pickup;

import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Centralized ownership and validity checks for the offline pickup snapshot. */
final class DataGuard {
    private DataGuard() {}
    enum Verdict { SAME, CONTENT_CHANGED, NEW_ORDER, NEW_ACCOUNT }

    static final String K_OWNER = "meta_owner";
    static final String K_ORDER = "meta_order_id";
    static final String K_PRINT = "meta_fingerprint";
    static final String COLLECTED_PREFIX = "collected_";
    static final String[] CACHE_DIRS = {
            "pickup_order_cache", "pickup_order_cache_tmp", "pickup_order_cache_backup"
    };

    /** Stable account identity from JWT subject/user claims; never use token IDs or expiry claims. */
    static String ownerFromToken(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length < 2) return "";
            byte[] raw = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(raw, StandardCharsets.UTF_8));
            for (String key : new String[]{"sub", "user_id", "userId", "uid", "email"}) {
                String value = payload.optString(key, "").trim();
                if (!value.isEmpty() && !"null".equalsIgnoreCase(value))
                    return sha256(value.toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) { }
        return "";
    }

    /** Fallback for opaque access tokens when the orders response includes account identity. */
    static String ownerFromOrders(JSONArray orders) {
        if (orders == null) return "";
        for (int i = 0; i < orders.length(); i++) {
            String value = ownerInObject(orders.optJSONObject(i));
            if (!value.isEmpty()) return sha256(value.toLowerCase(Locale.ROOT));
        }
        return "";
    }

    private static String ownerInObject(JSONObject object) {
        if (object == null) return "";
        for (String key : new String[]{"user_id", "userId", "customer_id", "customerId",
                "client_id", "clientId", "account_id", "accountId", "owner_id", "ownerId", "email"}) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        for (String key : new String[]{"user", "customer", "client", "account", "profile", "owner"}) {
            JSONObject nested = object.optJSONObject(key);
            if (nested == null) continue;
            for (String id : new String[]{"id", "user_id", "customer_id", "client_id", "email"}) {
                String value = nested.optString(id, "").trim();
                if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
            }
        }
        return "";
    }

    static String fingerprint(List<MainActivity.ProductRow> rows) {
        List<String> parts = new ArrayList<>();
        for (MainActivity.ProductRow row : rows) {
            parts.add(row.key.length() + ":" + row.key + row.amount.length() + ":" + row.amount
                    + row.name.length() + ":" + row.name + row.note.length() + ":" + row.note);
        }
        Collections.sort(parts);
        StringBuilder joined = new StringBuilder();
        for (String part : parts) joined.append(part).append('\n');
        return sha256(joined.toString());
    }

    /** A collected mark remains valid only while product key AND requested amount match. */
    static String tick(MainActivity.ProductRow row) { return row.key + "|" + row.amount; }

    static Verdict judge(SharedPreferences prefs, String owner, long orderId, String fingerprint) {
        String oldOwner = prefs.getString(K_OWNER, "");
        String oldOrder = prefs.getString(K_ORDER, "");
        String oldFingerprint = prefs.getString(K_PRINT, "");
        if (owner == null || owner.isEmpty() || oldOwner.isEmpty() || !owner.equals(oldOwner))
            return Verdict.NEW_ACCOUNT;
        if (!String.valueOf(orderId).equals(oldOrder)) return Verdict.NEW_ORDER;
        if (!fingerprint.equals(oldFingerprint)) return Verdict.CONTENT_CHANGED;
        return Verdict.SAME;
    }

    static void saveOwner(SharedPreferences prefs, String owner) {
        prefs.edit().putString(K_OWNER, owner == null ? "" : owner).commit();
    }

    static void saveMeta(SharedPreferences prefs, String owner, long orderId, String fingerprint) {
        prefs.edit().putString(K_OWNER, owner).putString(K_ORDER, String.valueOf(orderId))
                .putString(K_PRINT, fingerprint).commit();
    }

    static void clearOrderMeta(SharedPreferences prefs) {
        prefs.edit().remove(K_ORDER).remove(K_PRINT).commit();
    }

    /** Disk-only invalidation. All calls must run through the single-thread cache worker. */
    static void wipeCacheFiles(File filesDir) {
        for (String name : CACHE_DIRS) deleteRecursively(new File(filesDir, name));
    }

    /** Clears every saved order value, but never the login token. */
    static void wipeOrderData(File filesDir, SharedPreferences prefs) {
        wipeCacheFiles(filesDir);
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : new ArrayList<>(prefs.getAll().keySet())) {
            if (key.startsWith(COLLECTED_PREFIX) || key.startsWith("meta_")) edit.remove(key);
        }
        edit.commit();
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
