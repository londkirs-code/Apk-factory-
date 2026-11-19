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

/** זיהוי בעלות, גרסת תוכן וטיפול עקבי במטמון האישי. */
final class DataGuard {
    private DataGuard() {}
    enum Verdict { SAME, CONTENT_CHANGED, NEW_ORDER, NEW_ACCOUNT }

    static final String K_OWNER="meta_owner", K_ORDER="meta_order_id", K_PRINT="meta_fingerprint";
    static final String COLLECTED_PREFIX="collected_";
    static final String[] CACHE_DIRS={"pickup_order_cache","pickup_order_cache_tmp","pickup_order_cache_backup"};

    static String ownerFromToken(String token) {
        try {
            String[] parts=token.split("\\.");
            if(parts.length<2) return "";
            byte[] raw=Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP);
            JSONObject json=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            for(String key:new String[]{"sub","user_id","userId","uid","id","email"}) {
                String value=json.optString(key,"").trim();
                if(!value.isEmpty()) return sha256(value.toLowerCase(java.util.Locale.ROOT));
            }
        } catch(Exception ignored) {}
        return "";
    }

    /** גיבוי לזיהוי כאשר הטוקן אינו JWT: מחפש מזהה חשבון/לקוח בתשובת ההזמנות. */
    static String ownerFromOrders(JSONArray orders) {
        if(orders==null) return "";
        for(int i=0;i<orders.length();i++) {
            JSONObject order=orders.optJSONObject(i);
            String found=ownerInObject(order);
            if(!found.isEmpty()) return sha256(found.toLowerCase(java.util.Locale.ROOT));
        }
        return "";
    }

    private static String ownerInObject(JSONObject object) {
        if(object==null) return "";
        String[] keys={"user_id","userId","customer_id","customerId","client_id","clientId","account_id","accountId","owner_id","ownerId","email"};
        for(String key:keys) {
            String value=object.optString(key,"").trim();
            if(!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
        }
        for(String key:new String[]{"user","customer","client","account","profile","owner"}) {
            Object nested=object.opt(key);
            if(nested instanceof JSONObject) {
                JSONObject child=(JSONObject)nested;
                for(String id:new String[]{"id","user_id","customer_id","client_id","email"}) {
                    String value=child.optString(id,"").trim();
                    if(!value.isEmpty() && !"null".equalsIgnoreCase(value)) return value;
                }
            }
        }
        return "";
    }

    static String fingerprint(List<MainActivity.ProductRow> rows) {
        List<String> parts=new ArrayList<>();
        for(MainActivity.ProductRow row:rows) {
            parts.add(row.key.length()+":"+row.key+row.amount.length()+":"+row.amount
                    +row.name.length()+":"+row.name+row.note.length()+":"+row.note);
        }
        Collections.sort(parts);
        StringBuilder joined=new StringBuilder();
        for(String part:parts) joined.append(part).append('\n');
        return sha256(joined.toString());
    }

    static String tick(MainActivity.ProductRow row) { return row.key+"|"+row.amount; }

    static Verdict judge(SharedPreferences prefs,String owner,long orderId,String fingerprint) {
        String oldOwner=prefs.getString(K_OWNER,"");
        String oldOrder=prefs.getString(K_ORDER,"");
        String oldPrint=prefs.getString(K_PRINT,"");
        if(owner==null||owner.isEmpty()||oldOwner.isEmpty()||!owner.equals(oldOwner)) return Verdict.NEW_ACCOUNT;
        if(oldOrder.isEmpty()||!oldOrder.equals(String.valueOf(orderId))) return Verdict.NEW_ORDER;
        if(!oldPrint.equals(fingerprint)) return Verdict.CONTENT_CHANGED;
        return Verdict.SAME;
    }

    static void saveOwner(SharedPreferences prefs,String owner) {
        prefs.edit().putString(K_OWNER,owner==null?"":owner).commit();
    }

    static void saveMeta(SharedPreferences prefs,String owner,long orderId,String fingerprint) {
        prefs.edit().putString(K_OWNER,owner).putString(K_ORDER,String.valueOf(orderId))
                .putString(K_PRINT,fingerprint).commit();
    }

    static void clearOrderMeta(SharedPreferences prefs) {
        prefs.edit().remove(K_ORDER).remove(K_PRINT).commit();
    }

    /** מוחק קבצי הזמנה, סימונים ומטא, אך לא טוקן התחברות. */
    static void wipeOrderData(File filesDir,SharedPreferences prefs) {
        for(String name:CACHE_DIRS) deleteRecursively(new File(filesDir,name));
        SharedPreferences.Editor edit=prefs.edit();
        for(String key:new ArrayList<>(prefs.getAll().keySet())) {
            if(key.startsWith(COLLECTED_PREFIX)||key.startsWith("meta_")) edit.remove(key);
        }
        edit.commit();
    }

    static void wipeCacheOnly(File filesDir,SharedPreferences prefs) {
        for(String name:CACHE_DIRS) deleteRecursively(new File(filesDir,name));
        clearOrderMeta(prefs);
    }

    static void deleteRecursively(File file) {
        if(file==null||!file.exists()) return;
        File[] children=file.listFiles();
        if(children!=null) for(File child:children) deleteRecursively(child);
        file.delete();
    }

    static String sha256(String value) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:digest) out.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
            return out.toString();
        } catch(Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
