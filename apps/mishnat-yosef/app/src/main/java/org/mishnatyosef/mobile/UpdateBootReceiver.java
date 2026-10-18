package org.mishnatyosef.mobile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class UpdateBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && context.getSharedPreferences("mishnat_auth",Context.MODE_PRIVATE).getBoolean("app_update_auto",false)) UpdateService.start(context);
 }
}