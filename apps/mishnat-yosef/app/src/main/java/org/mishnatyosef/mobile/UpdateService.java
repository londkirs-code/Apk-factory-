package org.mishnatyosef.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;

public class UpdateService extends Service {
    private static final String PREFS="mishnat_auth";
    private static final String CHANNEL="app_update_monitor";
    private static final String META="https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/apps/mishnat-yosef/app/build.gradle";
    private static final String APK="https://raw.githubusercontent.com/londkirs-code/Apk-factory-/main/downloads/mishnat-yosef/app.apk";
    private static final int NOTIFICATION_ID=7332;
    private Handler handler;
    private volatile boolean checking=false;
    private final Runnable loop=new Runnable(){@Override public void run(){
        if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("app_update_auto",false)){stopSelf();return;}
        checkUpdate(false);
        long sec=Math.max(5,getSharedPreferences(PREFS,MODE_PRIVATE).getInt("app_update_interval_seconds",300));
        handler.postDelayed(this,sec*1000L);
    }};
    public static void start(Context c){Intent i=new Intent(c,UpdateService.class);try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}catch(Exception ignored){}}
    public static void stop(Context c){c.stopService(new Intent(c,UpdateService.class));}
    public static void checkNow(Context c){Intent i=new Intent(c,UpdateService.class).setAction("org.mishnatyosef.mobile.UPDATE_CHECK_NOW");try{if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}catch(Exception ignored){}}
    public static void refreshNotification(Context c){start(c);}
    @Override public void onCreate(){super.onCreate();handler=new Handler(Looper.getMainLooper());createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        boolean manual=intent!=null&&"org.mishnatyosef.mobile.UPDATE_CHECK_NOW".equals(intent.getAction());
        if(!manual&&!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("app_update_auto",false)){stopSelf();return START_NOT_STICKY;}
        startForeground(NOTIFICATION_ID,buildNotification("בדיקת עדכונים פעילה",true));
        handler.removeCallbacks(loop);
        if(manual)checkUpdate(true);
        else{checkUpdate(false);handler.postDelayed(loop,Math.max(5,getSharedPreferences(PREFS,MODE_PRIVATE).getInt("app_update_interval_seconds",300))*1000L);}
        return START_STICKY;
    }
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null&&nm.getNotificationChannel(CHANNEL)==null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"בדיקת עדכוני אפליקציה",NotificationManager.IMPORTANCE_LOW));}}
    private Notification buildNotification(String text,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,7332,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("משנת יוסף · עדכונים").setContentText(text).setContentIntent(pi).setOngoing(ongoing).setOnlyAlertOnce(true).build();
    }
    private void updateNotification(String text,boolean ongoing){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(text,ongoing));}
    private void checkUpdate(boolean tellNoUpdate){
        if(checking)return;checking=true;
        new Thread(()->{try{
            String gradle=readText(META);Matcher m=Pattern.compile("versionName\\s+'([^']+)'").matcher(gradle);
            if(!m.find())throw new IOException("לא נמצא מספר גרסה בקובץ המקור");
            String latest=m.group(1);PackageInfo pi=getPackageManager().getPackageInfo(getPackageName(),0);String installed=pi.versionName==null?"0":pi.versionName;
            if(compareVersions(latest,installed)<=0){if(tellNoUpdate)postResult("האפליקציה מעודכנת (גרסה "+installed+")",false);return;}
            File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(dir==null)dir=getFilesDir();if(!dir.exists()&&!dir.mkdirs())throw new IOException("לא ניתן ליצור תיקיית הורדה");
            File apk=new File(dir,"mishnat-yosef-update.apk");download(APK,apk);if(apk.length()<100000)throw new IOException("קובץ APK קטן מדי או פגום");
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("app_update_downloaded_path",apk.getAbsolutePath()).putString("app_update_downloaded_version",latest).apply();
            postResult("נמצא עדכון "+latest+" — ההורדה הושלמה. לחצו להתקנה",true);
        }catch(Exception e){if(tellNoUpdate)postResult("בדיקת העדכון נכשלה: "+e.getMessage(),false);}finally{checking=false;}},"mishnat-update-check").start();
    }
    private String readText(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","MishnatYosef-Updater");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}finally{c.disconnect();}}
    private void download(String u,File f)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("User-Agent","MishnatYosef-Updater");try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{c.disconnect();}}
    private int compareVersions(String a,String b){String[] x=a.split("[^0-9]+"),y=b.split("[^0-9]+");int n=Math.max(x.length,y.length);for(int i=0;i<n;i++){long p=i<x.length&&!x[i].isEmpty()?Long.parseLong(x[i]):0,q=i<y.length&&!y[i].isEmpty()?Long.parseLong(y[i]):0;if(p!=q)return p>q?1:-1;}return 0;}
    private void postResult(String message,boolean hasUpdate){handler.post(()->{
        updateNotification(message,getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("app_update_auto",false));
        if(hasUpdate){Intent install=new Intent(this,MainActivity.class).setAction("org.mishnatyosef.mobile.INSTALL_DOWNLOADED_UPDATE").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,7333,install,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Notification n=b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("משנת יוסף — עדכון מוכן").setContentText(message).setContentIntent(pi).setAutoCancel(true).build();NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID+1,n);if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("app_update_auto",false))stopSelf();}
        else if(!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("app_update_auto",false)){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(message,false));stopSelf();}
    });}
    @Override public void onDestroy(){if(handler!=null)handler.removeCallbacks(loop);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}