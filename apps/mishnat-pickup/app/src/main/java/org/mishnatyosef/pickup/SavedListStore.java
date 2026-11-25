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

    /** Save a standalone order/list snapshot without touching the active order cache. */
    synchronized boolean archiveSnapshot(String sourceId,String sourceLabel,JSONObject snapshot,SharedPreferences prefs) {
        if(sourceId==null||sourceId.isEmpty()||snapshot==null)return false;
        try {
            JSONObject data=new JSONObject(snapshot.toString());
            long orderId=data.optLong("order_id",-1);
            JSONArray products=data.optJSONArray("products");
            if(orderId<=0||products==null)return false;
            for(int i=0;i<products.length();i++) {
                JSONObject product=products.optJSONObject(i);
                if(product==null)continue;
                String imagePath=product.optString("image_file","");
                if(!imagePath.isEmpty()) {
                    File image=new File(imagePath);
                    if(!image.isAbsolute())image=new File(imageRoot,imagePath);
                    String cached=cacheImage(image);
                    if(!cached.isEmpty())product.put("image_file",cached);
                }
            }
            String owner=data.optString("owner","");
            String ownerKey=owner.isEmpty()?"noowner":owner.substring(0,Math.min(12,owner.length()));
            String id=sourceId+"_"+ownerKey+"_"+orderId;
            data.put("_saved_list_id",id).put("_source_id",sourceId)
                .put("_source_label",sourceLabel==null?"רשימה שמורה":sourceLabel)
                .put("_archived_at",System.currentTimeMillis());
            if(prefs!=null) {
                data.put("_collected_ticks",prefs.getString(DataGuard.COLLECTED_PREFIX+orderId,"[]"));
                data.put("_partial_quantities",prefs.getString("partial_"+orderId,"{}"));
            }
            File target=new File(root,safeName(id)+".json"),temp=new File(root,safeName(id)+".json.tmp");
            try(FileOutputStream out=new FileOutputStream(temp)) {
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            if(target.exists()&&!target.delete()){temp.delete();return false;}
            if(!temp.renameTo(target)){temp.delete();return false;}
            return true;
        } catch(Exception e) { return false; }
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

    /**
     * Merge the contents of one archived order into another saved list.
     * The target identity is retained; source archive is intentionally preserved.
     * Existing collection ticks remain valid only for rows whose quantity did not change.
     */
    synchronized boolean mergeEntries(String targetId,String sourceId) {
        if(targetId==null||sourceId==null||targetId.equals(sourceId))return false;
        try {
            JSONObject target=readEntry(targetId);
            JSONObject source=readEntry(sourceId);
            JSONArray targetRows=target.optJSONArray("products");
            JSONArray sourceRows=source.optJSONArray("products");
            if(targetRows==null)targetRows=new JSONArray();
            if(sourceRows==null||sourceRows.length()==0)return false;
            JSONArray merged=new JSONArray();
            java.util.Map<String,Integer> positions=new java.util.LinkedHashMap<>();
            java.util.Set<String> changed=new java.util.HashSet<>();
            for(int i=0;i<targetRows.length();i++) {
                JSONObject row=targetRows.optJSONObject(i);
                if(row==null)continue;
                JSONObject copy=new JSONObject(row.toString());
                String key=mergeKey(copy);
                if(!key.isEmpty()&&!positions.containsKey(key))positions.put(key,merged.length());
                merged.put(copy);
            }
            for(int i=0;i<sourceRows.length();i++) {
                JSONObject incoming=sourceRows.optJSONObject(i);
                if(incoming==null)continue;
                JSONObject copy=new JSONObject(incoming.toString());
                String key=mergeKey(copy);
                Integer at=key.isEmpty()?null:positions.get(key);
                if(at==null) {
                    if(!key.isEmpty())positions.put(key,merged.length());
                    merged.put(copy);
                    continue;
                }
                JSONObject existing=merged.optJSONObject(at);
                if(existing==null){merged.put(at,copy);continue;}
                double before=parseAmount(existing.optString("amount","1"));
                double add=parseAmount(copy.optString("amount","1"));
                double total=before+add;
                if(Double.isNaN(total)||Double.isInfinite(total)||total<=0)
                    throw new Exception("כמות לא תקינה במהלך מיזוג הרשימות.");
                if(Math.abs(total-before)>0.000001) {
                    existing.put("amount",formatAmount(total));
                    changed.add(existing.optString("key",key));
                }
                String note=existing.optString("note","");
                String incomingNote=copy.optString("note","");
                if(!incomingNote.isEmpty()&&!note.contains(incomingNote))
                    existing.put("note",note.isEmpty()?incomingNote:note+" · "+incomingNote);
            }
            target.put("products",merged);
            target.remove("fingerprint");
            target.put("fingerprint_version",2);
            target.put("_archived_at",System.currentTimeMillis());
            String targetName=target.optString("order_name","רשימה");
            String sourceName=source.optString("order_name","רשימה נוספת");
            if(!sourceName.isEmpty()&&!targetName.contains(sourceName))
                target.put("order_name",(targetName+" + "+sourceName).substring(0,Math.min(240,(targetName+" + "+sourceName).length())));
            JSONArray origins=target.optJSONArray("_merged_sources");
            if(origins==null)origins=new JSONArray();
            JSONObject origin=new JSONObject().put("order_id",source.optString("order_id",""))
                    .put("order_name",sourceName).put("merged_at",System.currentTimeMillis());
            origins.put(origin);target.put("_merged_sources",origins);

            if(!changed.isEmpty()) {
                JSONObject partial;
                try{partial=new JSONObject(target.optString("_partial_quantities","{}"));}
                catch(Exception ignored){partial=new JSONObject();}
                for(String key:changed)partial.remove(key);
                target.put("_partial_quantities",partial.toString());
            }
            File file=fileFor(targetId);
            return writeAtomic(file,target);
        } catch(Exception e) { return false; }
    }

    private static String mergeKey(JSONObject row) {
        String key=row.optString("key","").trim();
        if(!key.isEmpty())return key;
        return row.optString("name","").trim().toLowerCase(java.util.Locale.ROOT);
    }
    private static double parseAmount(String value) throws Exception {
        try {
            double amount=Double.parseDouble(value==null?"1":value.replace(',','.'));
            if(Double.isNaN(amount)||Double.isInfinite(amount)||amount<=0)throw new Exception();
            return amount;
        } catch(Exception e) { throw new Exception("כמות מוצר אינה תקינה."); }
    }
    private static String formatAmount(double value) {
        if(Math.abs(value-Math.rint(value))<0.000001)return String.valueOf((long)Math.rint(value));
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
    private static boolean writeAtomic(File target,JSONObject data) {
        File temp=new File(target.getParentFile(),target.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)) {
            out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        } catch(Exception e) { temp.delete();return false; }
        if(target.exists()&&!target.delete()){temp.delete();return false;}
        if(!temp.renameTo(target)){temp.delete();return false;}
        return true;
    }

    /** החלפת המוצרים של רשימה שמורה (סנכרון עם האתר). שאר השדות, כולל סימוני האיסוף, נשמרים. */
    synchronized boolean replaceProducts(String id, JSONArray products, String serverPrint) {
        try {
            File file = fileFor(id);
            if (!file.isFile()) return false;
            JSONObject data = read(file);
            data.put("products", products);
            data.put("_server_print", serverPrint == null ? "" : serverPrint);
            return writeAtomic(file, data);
        } catch (Exception e) { return false; }
    }

    synchronized boolean setServerPrint(String id, String serverPrint) {
        try {
            File file = fileFor(id);
            if (!file.isFile()) return false;
            JSONObject data = read(file);
            data.put("_server_print", serverPrint == null ? "" : serverPrint);
            return writeAtomic(file, data);
        } catch (Exception e) { return false; }
    }

    /** שמירת התקדמות האיסוף בתוך הרשימה השמורה (תצוגת כמה רשימות יחד). */
    synchronized boolean setProgress(String id, String ticks, String partials) {
        try {
            File file = fileFor(id);
            if (!file.isFile()) return false;
            JSONObject data = read(file);
            data.put("_collected_ticks", ticks == null ? "[]" : ticks);
            data.put("_partial_quantities", partials == null ? "{}" : partials);
            return writeAtomic(file, data);
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
