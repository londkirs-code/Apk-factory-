package com.londkirs.apkbridge.wifihelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

/** Switches Wi-Fi on for APK Bridge. Only apps signed with the same key may send this broadcast. */
public class EnableWifiReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean ok = false;
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                ok = wifi.isWifiEnabled() || wifi.setWifiEnabled(true);
            }
        } catch (RuntimeException ignored) {
            ok = false;
        }
        if (isOrderedBroadcast()) {
            setResultCode(ok ? 1 : 0);
        }
    }
}
