package org.mishnatyosef.pickup;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persistent list archive. Product images are deliberately not copied into this archive. */
final class SavedListStore {
    static final class Entry {
        final String id, sourceId, sourceLabel, title, orderId, owner, updatedAt;
        final int itemCount;
        final long archivedAt;
        Entry(String id, String sourceId, String sourceLabel, String title, String orderId,
              String owner, String updatedAt, int itemCount, long archivedAt) {
            this.id=id; this.sourceId=sourceId; this.sourceLabel=sourceLabel; this.title=title;
            this.orderId=orderId; this.owner=owner; this.updatedAt=updatedAt;
            this.itemCount=itemCount; this.archivedAt=archivedAt;
        }
    }

    private final File root;
    SavedListStore(Context context) {
        root = new File(context.getApplicationContext().getFilesDir(), "saved_lists");
        if (!root.exists()) root.mkdirs();
    }

    synchronized boolean archive(String sourceId, String sourceLabel, File orderFile,
                                 SharedPreferences prefs) {
        if (sourceId == null || sourceId.isEmpty() || orderFile == null || !orderFile.isFile()) return false;
        try {
            JSONObject data = read(orderFile);
            String orderId = String.valueOf(data.optLong("order_id", -1));
            if ("-1".equals(orderId)) return false;
            JSONArray products=data.optJSONArray("products");
            if(products!=null)for(int i=0;i<products.length();i++){
                JSONObject product=products.optJSONObject(i);
                if(product!=null)product.put("image_file","");
            }
            String id = sourceId + "_" + orderId;
            data.put("_saved_list_id", id);
            data.put("_source_id", sourceId);
            data.put("_source_label", sourceLabel == null ? "רשימה שמורה" : sourceLabel);
            data.put("_archived_at", System.currentTimeMillis());
            if (prefs != null) {
                data.put("_collected_ticks", prefs.getString(DataGuard.COLLECTED_PREFIX + orderId, "[]"));
                data.put("_partial_quantities", prefs.getString("partial_" + orderId, "{}"));
            }
            File target = new File(root, safeName(id) + ".json");
            File temp = new File(root, target.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (target.exists() && !target.delete()) { temp.delete(); return false; }
            if (!temp.renameTo(target)) { temp.delete(); return false; }
            return true;
        } catch (Exception e) { return false; }
    }

    synchronized List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        File[] files = root.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return entries;
        for (File file : files) {
            try {
                JSONObject o = read(file);
                JSONArray items = o.optJSONArray("products");
                entries.add(new Entry(o.optString("_saved_list_id", file.getName().replace(".json","")),
                        o.optString("_source_id"), o.optString("_source_label", "רשימה שמורה"),
                        o.optString("order_name", "רשימה " + o.optString("order_id")),
                        String.valueOf(o.optLong("order_id", -1)), o.optString("owner"),
                        o.optString("pickup_date", o.optString("updated_at", "")),
                        items == null ? 0 : items.length(), o.optLong("_archived_at", file.lastModified())));
            } catch (Exception ignored) { }
        }
        Collections.sort(entries, (a,b) -> Long.compare(b.archivedAt, a.archivedAt));
        return entries;
    }

    synchronized JSONObject readEntry(String id) throws Exception {
        File file = fileFor(id);
        if (!file.isFile()) throw new Exception("הרשימה השמורה אינה קיימת.");
        return read(file);
    }

    synchronized boolean install(String id, File cacheRoot) {
        try {
            JSONObject data = readEntry(id);
            data.remove("_saved_list_id");
            data.remove("_source_id");
            data.remove("_source_label");
            data.remove("_archived_at");
            data.remove("_collected_ticks");
            data.remove("_partial_quantities");
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) return false;
            File temp = new File(cacheRoot, "order.json.tmp");
            File target = new File(cacheRoot, "order.json");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            if (target.exists() && !target.delete()) { temp.delete(); return false; }
            return temp.renameTo(target);
        } catch (Exception e) { return false; }
    }

    synchronized boolean restoreProgress(String id, SharedPreferences prefs) {
        try {
            JSONObject data = readEntry(id);
            String orderId = String.valueOf(data.optLong("order_id", -1));
            String ticks = data.optString("_collected_ticks", "[]");
            String partial = data.optString("_partial_quantities", "{}");
            return prefs.edit().putString(DataGuard.COLLECTED_PREFIX + orderId, ticks)
                    .putString("partial_" + orderId, partial).commit();
        } catch (Exception e) { return false; }
    }

    synchronized boolean delete(String id) {
        File file = fileFor(id);
        return !file.exists() || file.delete();
    }

    private File fileFor(String id) { return new File(root, safeName(id) + ".json"); }
    private static String safeName(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9_-]", "_"); }
    private static JSONObject read(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
