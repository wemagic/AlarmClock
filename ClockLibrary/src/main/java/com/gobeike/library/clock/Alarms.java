package com.gobeike.library.clock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.List;

/**
 * Created by xff on 2015/10/11.
 */
public class Alarms {
    public static final String ALARM_ALERT_ACTION = "com.gobeike.library.clock.ALARM_ALERT";

    // A public action sent by AlarmKlaxon when the alarm has stopped sounding
    // for any reason (e.g. because it has been dismissed from AlarmAlertFullScreen,
    // or killed due to an incoming phone call, etc).
    public static final String ALARM_DONE_ACTION = "com.gobeike.library.clock.ALARM_DONE";

    // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
    // can snooze the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
    public static final String ALARM_SNOOZE_ACTION = "com.gobeike.library.clock.ALARM_SNOOZE";

    // AlarmAlertFullScreen listens for this broadcast intent, so that other applications
    // can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
    public static final String ALARM_DISMISS_ACTION = "com.gobeike.library.clock.ALARM_DISMISS";

    // This is a private action used by the AlarmKlaxon to update the UI to
    // show the alarm has been killed.
    public static final String ALARM_KILLED = "alarm_killed";

    // Extra in the ALARM_KILLED intent to indicate to the user how long the
    // alarm played before being killed.
    public static final String ALARM_KILLED_TIMEOUT = "alarm_killed_timeout";

    // This string is used to indicate a silent alarm in the db.
    public static final String ALARM_ALERT_SILENT = "silent";

    // This intent is sent from the notification when the user cancels the
    // snooze alert.
    public static final String CANCEL_SNOOZE = "cancel_snooze";//休眠，等会再叫

    // This string is used when passing an Alarm object through an intent.
    public static final String ALARM_INTENT_EXTRA = "intent.extra.alarm";
    public static final String ALARM_RAW_DATA = "intent.extra.alarm_raw";
    // This string is used to identify the alarm id passed to SetAlarm from the
    // list of alarms.
    public static final String ALARM_ID = "alarm_id";
    public static final String START_CLOCK_ACTIVItY = "com.gobeike.startClock";
    public static final String DETAIL_CLOCK_ACTIVItY = "com.gobeike.clock.detail";
    public static final String NEXT_CLOCK_BROADCAST = "com.gobeike.nextclock.broadcast";




    public static final String ALARM_CLOCKTIME_key = "alarm_time";
    public static final String ALARM_CLOCKTIME_desc_key = "alarm_time_title";
    public static final String ALARM_CLOCKTIME_Id_key = "alarm_time_id";


    public static void putClockTime(SharedPreferences preferences,long time) {
        preferences.edit().putLong(ALARM_CLOCKTIME_key, time).apply();
    }

    public static Long getClockTime(SharedPreferences preferences) {
        return preferences.getLong(ALARM_CLOCKTIME_key, 0);
    }

    public static void putClockTimeTitle(SharedPreferences preferences,String title,String key) {
        preferences.edit().putString(key, title).apply();
    }

    public static String getClockTimeTitle(SharedPreferences preferences,String key) {
        return preferences.getString(key, "");
    }
    public static AlarmBean getAlarm(Context context) {
        SharedPreferences preferences=context.getSharedPreferences("clock",Context.MODE_PRIVATE);
        AlarmBean alarmBean=new AlarmBean();
        alarmBean.clockTime=getClockTime(preferences);
        alarmBean.title=getClockTimeTitle(preferences,ALARM_CLOCKTIME_desc_key);
        alarmBean.id=getClockTimeTitle(preferences,ALARM_CLOCKTIME_Id_key);
        if (alarmBean.clockTime==0)return null;
        return alarmBean;
    }

    public static Intent getExplicitIapIntent(String actionString,Context context) {
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
