package org.mishnatyosef.pickup;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Index and private storage for independent account/import sources. */
final class SourceRegistry {
    static final int MAX_SOURCES=10;
    static final String KIND_ACCOUNT="ACCOUNT", KIND_IMPORTED="IMPORTED";
    private static final String INDEX="mishnat_pickup_source_index", LIST="sources_v1", ACTIVE="active_source_id";
    static final class Source {
        final String id; String kind,label,ownerKey,state; boolean includeInCombined; long lastSyncAt; int generation;
        Source(String i,String k,String l,String o,String s,boolean c,long t,int g){id=i;kind=k;label=l;ownerKey=o;state=s;includeInCombined=c;lastSyncAt=t;generation=g;}
        JSONObject json() throws Exception{return new JSONObject().put("id",id).put("kind",kind).put("label",label).put("ownerKey",ownerKey).put("state",state).put("includeInCombined",includeInCombined).put("lastSyncAt",lastSyncAt).put("generation",generation);}
    }
    private final Context context; private final SharedPreferences index; private final List<Source> sources=new ArrayList<>(); private String active="";
    SourceRegistry(Context c){context=c.getApplicationContext();index=context.getSharedPreferences(INDEX,Context.MODE_PRIVATE);load();}
    synchronized List<Source> all(){return new ArrayList<>(sources);}
    synchronized Source active(){return find(active);}
    synchronized Source find(String id){if(id==null)return null;for(Source s:sources)if(id.equals(s.id))return s;return null;}
    synchronized Source findByOwner(String owner){if(owner==null||owner.isEmpty())return null;for(Source s:sources)if(owner.equals(s.ownerKey))return s;return null;}
    synchronized boolean canAdd(){return sources.size()<MAX_SOURCES;}
    synchronized Source create(String kind,String label,String owner)throws Exception{
        if(!canAdd())throw new Exception("אפשר לשמור עד 10 מקורות בלבד.");
        String id=hash(kind+":"+String.valueOf(owner)+":"+System.nanoTime()).substring(0,24);
        Source s=new Source(id,kind,clean(label),owner==null?"":owner,"NEVER_SYNCED",true,0,1);sources.add(s);active=id;save();return s;
    }
    synchronized void setActive(String id)throws Exception{if(find(id)==null)throw new Exception("החשבון אינו קיים.");active=id;save();}
    synchronized void rename(String id,String label){Source s=find(id);if(s!=null){s.label=clean(label);save();}}
    synchronized void markNeedsLogin(String id){Source s=find(id);if(s!=null){s.state="LOGIN_EXPIRED";s.generation++;save();}}
    synchronized void markSynced(String id){Source s=find(id);if(s!=null){s.state="OK";s.lastSyncAt=System.currentTimeMillis();save();}}
    synchronized void remove(String id){Source s=find(id);if(s==null)return;s.generation++;sources.remove(s);if(id.equals(active))active=sources.isEmpty()?"":sources.get(0).id;save();deleteTree(sourceDir(id));prefs(id).edit().clear().commit();}
    synchronized int generation(String id){Source s=find(id);return s==null?-1:s.generation;}
    SharedPreferences prefs(String id){return context.getSharedPreferences("mishnat_source_"+id,Context.MODE_PRIVATE);}
    File sourceDir(String id){File d=new File(new File(context.getFilesDir(),"sources"),id);if(!d.exists())d.mkdirs();return d;}
    synchronized boolean migrateLegacy(SharedPreferences old){
        if(!sources.isEmpty())return false;
        File oldCache=new File(context.getFilesDir(),"pickup_order_cache");
        String token=old.getString("encrypted_access_token",""),owner=old.getString(DataGuard.K_OWNER,"");
        if(token.isEmpty()&&!oldCache.exists()&&old.getAll().isEmpty())return false;
        String seed=owner.isEmpty()?hash(token.isEmpty()?"legacy-offline":token):owner;
        String id="legacy_"+seed.substring(0,Math.min(20,seed.length()));
        Source s=new Source(id,KIND_ACCOUNT,"חשבון שמור",owner,token.isEmpty()?"LOGIN_EXPIRED":"OK",true,System.currentTimeMillis(),1);
        SharedPreferences.Editor e=prefs(id).edit();
        for(Map.Entry<String,?> x:old.getAll().entrySet()){Object v=x.getValue();String k=x.getKey();
            if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);}
        if(!e.commit())return false;
        File target=sourceDir(id);
        for(String name:new String[]{"pickup_order_cache","pickup_order_cache_tmp","pickup_order_cache_backup"}){
            File from=new File(context.getFilesDir(),name);if(from.exists()&&!copy(from,new File(target,name)))return false;}
        sources.add(s);active=id;save();return true;
    }
    private void load(){try{JSONArray a=new JSONArray(index.getString(LIST,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)sources.add(new Source(o.optString("id"),o.optString("kind",KIND_ACCOUNT),o.optString("label","חשבון"),o.optString("ownerKey",""),o.optString("state","NEVER_SYNCED"),o.optBoolean("includeInCombined",true),o.optLong("lastSyncAt"),o.optInt("generation",1)));}active=index.getString(ACTIVE,"");if(find(active)==null)active=sources.isEmpty()?"":sources.get(0).id;}catch(Exception e){sources.clear();active="";}}
    private synchronized void save(){JSONArray a=new JSONArray();try{for(Source s:sources)a.put(s.json());}catch(Exception ignored){}index.edit().putString(LIST,a.toString()).putString(ACTIVE,active).commit();}
    private static String clean(String s){return s==null||s.trim().isEmpty()?"חשבון":s.trim();}
    private static String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder o=new StringBuilder();for(byte x:b)o.append(String.format(Locale.US,"%02x",x&255));return o.toString();}catch(Exception e){return Integer.toHexString(s.hashCode());}}
    private static boolean copy(File a,File b){if(a.isDirectory()){if(!b.exists()&&!b.mkdirs())return false;File[] fs=a.listFiles();if(fs==null)return true;for(File f:fs)if(!copy(f,new File(b,f.getName())))return false;return true;}try{InputStream i=new FileInputStream(a);OutputStream o=new FileOutputStream(b);byte[] z=new byte[8192];int n;while((n=i.read(z))!=-1)o.write(z,0,n);o.close();i.close();return true;}catch(Exception e){return false;}}
    private static void deleteTree(File f){if(!f.exists())return;File[] c=f.listFiles();if(c!=null)for(File x:c)deleteTree(x);f.delete();}
}
