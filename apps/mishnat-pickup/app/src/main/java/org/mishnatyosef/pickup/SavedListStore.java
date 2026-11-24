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
import java.security.MessageDigest;
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
    private final File imageRoot;
    SavedListStore(Context context) {
        File files=context.getApplicationContext().getFilesDir();
        root = new File(files, "saved_lists");
        imageRoot = new File(files, "saved_list_images");
        if (!root.exists()) root.mkdirs();
        if (!imageRoot.exists()) imageRoot.mkdirs();
    }

    synchronized String cacheImage(File source) {
        if(source==null||!source.isFile()||source.length()<=0)return "";
        File parent=source.getParentFile();
        if(parent!=null&&parent.equals(imageRoot))return source.getAbsolutePath();
        try{
            String name=source.getName();int dot=name.lastIndexOf('.');
            String ext=dot>=0?name.substring(dot+1).toLowerCase(java.util.Locale.ROOT):"jpg";
            if(!ext.matches("jpg|jpeg|png|webp"))ext="jpg";
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(FileInputStream in=new FileInputStream(source)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:digest.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
            File target=new File(imageRoot,"local_"+hex+"."+ext);
            if(target.isFile()&&target.length()>0)return target.getAbsolutePath();
            File temp=new File(imageRoot,target.getName()+".tmp");
            try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.getFD().sync();
            }
            if(target.exists()&&!target.delete()){temp.delete();return "";}
            if(!temp.renameTo(target)){temp.delete();return "";}
            return target.getAbsolutePath();
        }catch(Exception e){return "";}
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
                if(product!=null){
                    String imagePath=product.optString("image_file","");
                    if(!imagePath.isEmpty()){
                        File image=new File(imagePath);
                        if(!image.isAbsolute())image=new File(orderFile.getParentFile(),imagePath);
                        product.put("image_file",cacheImage(image));
                    }
                }
            }
            String owner=data.optString("owner","");
            String ownerKey=owner.isEmpty()?"noowner":owner.substring(0,Math.min(12,owner.length()));
            String id = sourceId + "_" + ownerKey + "_" + orderId;
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

    synchronized String rebindSource(String id,String sourceId,String sourceLabel) {
        try {
            JSONObject data=readEntry(id);
            String orderId=String.valueOf(data.optLong("order_id",-1));
            String owner=data.optString("owner","");
            String ownerKey=owner.isEmpty()?"noowner":owner.substring(0,Math.min(12,owner.length()));
            String newId=sourceId+"_"+ownerKey+"_"+orderId;
            data.put("_saved_list_id",newId);
            data.put("_source_id",sourceId);
            data.put("_source_label",sourceLabel==null?"רשימה שמורה":sourceLabel);
            File target=fileFor(newId),temp=new File(root,target.getName()+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            if(target.exists()&&!target.delete()){temp.delete();return "";}
            if(!temp.renameTo(target)){temp.delete();return "";}
            if(!newId.equals(id))fileFor(id).delete();
            return newId;
        }catch(Exception e){return "";}
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
