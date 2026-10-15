package org.londkirs.notifications;

import android.app.*;
import android.content.*;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_SHOW="org.londkirs.notifications.SHOW";
    public static final String ACTION_ALARM="org.londkirs.notifications.ALARM";
    public static final String ACTION_BUTTON="org.londkirs.notifications.BUTTON";
    public static final String CHANNEL="general";
    static final String PREFS="notification_center";
    static final String SCHEDULES="schedules";

    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null)return;
        if(ACTION_BUTTON.equals(intent.getAction())) {
            String label=intent.getStringExtra("label");
            Toast.makeText(context,label==null?"בוצע":label,Toast.LENGTH_SHORT).show();
            return;
        }
        String raw=intent.getStringExtra("payload");
        if(raw==null)return;
        try {
            JSONObject p=new JSONObject(raw);
            show(context,p);
            if(ACTION_ALARM.equals(intent.getAction())) handleFiredSchedule(context,p);
        } catch(Exception ignored) { }
    }

    static JSONArray readSchedules(Context c) {
        try{return new JSONArray(c.getSharedPreferences(PREFS,0).getString(SCHEDULES,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static void writeSchedules(Context c,JSONArray a) {
        c.getSharedPreferences(PREFS,0).edit().putString(SCHEDULES,a.toString()).apply();
    }
    static void schedule(Context c,JSONObject p) {
        try {
            long at=p.optLong("at",System.currentTimeMillis()+5000);
            Intent i=new Intent(c,NotificationReceiver.class); i.setAction(ACTION_ALARM); i.putExtra("payload",p.toString());
            int request=p.optString("uid",String.valueOf(p.optInt("id"))).hashCode();
            PendingIntent pi=PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            else if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
            else am.setExact(AlarmManager.RTC_WAKEUP,at,pi);
        } catch(Exception ignored) { }
    }
    static void cancel(Context c,JSONObject p) {
        try {
            Intent i=new Intent(c,NotificationReceiver.class);i.setAction(ACTION_ALARM);i.putExtra("payload",p.toString());
            int request=p.optString("uid",String.valueOf(p.optInt("id"))).hashCode();
            PendingIntent pi=PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);pi.cancel();
        }catch(Exception ignored){}
    }
    static void cancelDisplayed(Context c,int id) {
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
    }
    void handleFiredSchedule(Context c,JSONObject fired) {
        JSONArray all=readSchedules(c), keep=new JSONArray(); boolean found=false;
        for(int n=0;n<all.length();n++){
            JSONObject j=all.optJSONObject(n); if(j==null)continue;
            if(j.optString("uid").equals(fired.optString("uid"))) {
                found=true;
                long interval=j.optLong("repeat",0);
                if(interval>0){try{j.put("at",System.currentTimeMillis()+interval);keep.put(j);schedule(c,j);}catch(Exception ignored){}}
            } else keep.put(j);
        }
        if(found)writeSchedules(c,keep);
    }
    static void show(Context c,JSONObject p) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26 && nm.getNotificationChannel(CHANNEL)==null){
            NotificationChannel ch=new NotificationChannel(CHANNEL,"התראות",NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("התראות שנוצרות במרכז ההתראות");
            ch.enableVibration(true);ch.enableLights(true);ch.setLightColor(Color.CYAN);
            ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            nm.createNotificationChannel(ch);
        }
        int id=p.optInt("id",100);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle(p.optString("title","התראה"))
         .setContentText(p.optString("text",""))
         .setStyle(new Notification.BigTextStyle().bigText(p.optString("text","")))
         .setAutoCancel(!p.optBoolean("ongoing",false))
         .setOngoing(p.optBoolean("ongoing",false))
         .setOnlyAlertOnce(p.optBoolean("alertOnce",false))
         .setPriority(p.optInt("priority",0))
         .setWhen(System.currentTimeMillis()).setShowWhen(true);
        if(!p.optBoolean("sound",true))b.setSound(null);
        if(!p.optBoolean("vibrate",true))b.setVibrate(new long[]{0});
        if(p.optBoolean("progress",false))b.setProgress(100,p.optInt("progressValue",0),false);
        Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.setContentIntent(content);
        addButton(c,b,p.optString("action1",""),id*10+1);
        addButton(c,b,p.optString("action2",""),id*10+2);
        if(Build.VERSION.SDK_INT>=21) b.setCategory(Notification.CATEGORY_REMINDER);
        try{nm.notify(id,b.build());}catch(SecurityException ignored){}
    }
    static void addButton(Context c,Notification.Builder b,String label,int req) {
        if(label==null||label.trim().isEmpty())return;
        Intent i=new Intent(c,NotificationReceiver.class);i.setAction(ACTION_BUTTON);i.putExtra("label",label);
        PendingIntent pi=PendingIntent.getBroadcast(c,req,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_view,label,pi).build());
    }
}
