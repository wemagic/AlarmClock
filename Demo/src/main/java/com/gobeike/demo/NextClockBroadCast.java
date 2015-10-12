package com.gobeike.demo;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

public class NextClockBroadCast extends BroadcastReceiver {
    public NextClockBroadCast() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        // Remove the snooze alarm after a boot.

        Intent startIntent=getExplicitIapIntent("com.gobeike.library.clock.ALARM_ALERT",context);
        if (startIntent==null)return;
        startIntent.putExtra("clock_action",1);
        /**
         *  public static final String ALARM_CLOCKTIME_key = "alarm_time";
         public static final String ALARM_CLOCKTIME_desc_key = "alarm_time_title";
         public static final String ALARM_CLOCKTIME_Id_key = "alarm_time_id";
         */
        long time=System.currentTimeMillis()+60*1000;
        startIntent.putExtra("alarm_time", time);
        startIntent.putExtra("alarm_time_title","闹钟提醒");
        startIntent.putExtra("alarm_time_id","123");

        context.startService(startIntent);
    }

    private Intent getExplicitIapIntent(String actionString,Context context) {
        PackageManager pm = context.getPackageManager();
        Intent implicitIntent = new Intent(actionString);
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(implicitIntent, 0);

        // Is somebody else trying to intercept our IAP call?
        if (resolveInfos == null || resolveInfos.size()== 0) {
            return null;
        }
        for (ResolveInfo resolveInfo:resolveInfos){

            String packageName = resolveInfo.serviceInfo.packageName;
            if (packageName.equals(context.getApplicationInfo().packageName)){
                String className = resolveInfo.serviceInfo.name;
                ComponentName component = new ComponentName(packageName, className);
                Intent iapIntent = new Intent();
                iapIntent.setComponent(component);
                return iapIntent;
            }

        }

        return null;
    }
}
