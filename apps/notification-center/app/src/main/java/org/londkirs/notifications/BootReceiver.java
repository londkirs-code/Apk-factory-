package org.londkirs.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
        JSONArray all=NotificationReceiver.readSchedules(context);
        long now=System.currentTimeMillis();
        for(int n=0;n<all.length();n++){
            JSONObject j=all.optJSONObject(n);if(j==null)continue;
            long at=j.optLong("at",now+5000), repeat=j.optLong("repeat",0);
            if(at<=now && repeat>0){
                long steps=((now-at)/repeat)+1;
                at+=steps*repeat;
                try{j.put("at",at);}catch(Exception ignored){}
            }
            NotificationReceiver.schedule(context,j);
        }
        NotificationReceiver.writeSchedules(context,all);
    }
}
