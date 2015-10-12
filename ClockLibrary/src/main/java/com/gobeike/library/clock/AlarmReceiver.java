/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gobeike.library.clock;

import android.app.*;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.os.Parcel;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;
import java.util.Random;

import static android.app.Notification.*;

/**
 * Glue class: connects AlarmAlert IntentReceiver to AlarmAlert
 * activity.  Passes through Alarm ID.
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * If the alarm is older than STALE_WINDOW, ignore.  It
     * is probably the result of a time or timezone change
     */
    private final static int STALE_WINDOW = 30 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmBean alarm = Alarms.getAlarm(context);

        if (Alarms.ALARM_KILLED.equals(intent.getAction())) {
            // The alarm has been killed, update the notification
            updateNotification(context, (AlarmBean)
                            intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA),
                    intent.getIntExtra(Alarms.ALARM_KILLED_TIMEOUT, -1));
            return;
        } else if (Alarms.CANCEL_SNOOZE.equals(intent.getAction())) {
            if (alarm != null)
                enableAlert(context, alarm, alarm.clockTime);
            return;
        } else if (!Alarms.ALARM_ALERT_ACTION.equals(intent.getAction())) {
            // Unknown intent, bail.
            return;
        }


        /**
         * 如果本地没有，请求刷新重新获取闹钟时间或者对象
         */
        if (alarm == null) {
            // Make sure we set the next alert if needed.
            Intent nextClockBroad = new Intent(Alarms.NEXT_CLOCK_BROADCAST);
            context.sendBroadcast(nextClockBroad);
            // Alarms.setNextAlert(context);
            return;
        }
//
//        // Disable the snooze alert if this alarm is the snooze.
//        Alarms.disableSnoozeAlert(context, alarm.id);
//        // Disable this alarm if it does not repeat.
//        if (!alarm.daysOfWeek.isRepeatSet()) {
//            Alarms.enableAlarm(context, alarm.id, false);
//        } else {
//            // Enable the next alert if there is one. The above call to
//            // enableAlarm will call setNextAlert so avoid calling it twice.
//            Alarms.setNextAlert(context);
//        }

        // Intentionally verbose: always log the alarm time to provide useful
        // information in bug reports.
        long now = System.currentTimeMillis();

        // Always verbose to track down time change problems.
        if (now > alarm.clockTime + STALE_WINDOW) {
            Log.v("wangxianming", "Ignoring stale alarm");
            return;
        }

        // Maintain a cpu wake lock until the AlarmAlert and AlarmKlaxon can
        // pick it up.
        AlarmAlertWakeLock.acquireCpuWakeLock(context);

        /* Close dialogs and window shade */
        Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeDialogs);

        // Decide which activity to start based on the state of the keyguard.


        Intent stopIntent = Alarms.getExplicitIapIntent("com.gobeike.library.clock.ALARM_ALERT", context);
        if (stopIntent == null) return;
        stopIntent.putExtra("clock_action", 3);
        Notification n = new Builder(context).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("日程提醒").setContentText(alarm.title)
                .setWhen(alarm.clockTime)
                .setContentIntent(PendingIntent.getService(context, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT)).build();
        n.flags |= FLAG_SHOW_LIGHTS
                | FLAG_AUTO_CANCEL;
        n.defaults |= DEFAULT_LIGHTS | DEFAULT_SOUND | DEFAULT_VIBRATE;


        Intent alarmAlert = new Intent(Alarms.START_CLOCK_ACTIVItY);
        alarmAlert.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
        alarmAlert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        n.fullScreenIntent = PendingIntent.getActivity(context, alarm.serid, alarmAlert, 0);

        // Send the notification using the alarm id to easily identify the
        // correct notification.
        NotificationManager nm = getNotificationManager(context);
        nm.notify(0, n);


        Intent startIntent = Alarms.getExplicitIapIntent("com.gobeike.library.clock.ALARM_ALERT", context);
        if (startIntent == null) return;
        startIntent.putExtra(ClockService.CLOCK_Action, 2);
        context.startService(startIntent);
    }

    private NotificationManager getNotificationManager(Context context) {
        return (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void updateNotification(Context context, AlarmBean alarm, int timeout) {
        NotificationManager nm = getNotificationManager(context);

        // If the alarm is null, just cancel the notification.
        if (alarm == null) {
            if (true) {
                Log.v("wangxianming", "Cannot update notification for killer callback");
            }
            return;
        }

        // Launch SetAlarm when clicked.
        Intent viewAlarm = new Intent(Alarms.DETAIL_CLOCK_ACTIVItY);
        viewAlarm.putExtra(Alarms.ALARM_ID, alarm.id);
        PendingIntent intent =
                PendingIntent.getActivity(context, alarm.serid, viewAlarm, 0);

        // Update the notification to indicate that the alert has been
        // silenced.


        Notification n = new Builder(context).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("日程提醒update").setContentText("")
                .setWhen(timeout)
                .setContentIntent(intent).build();
        n.defaults = DEFAULT_ALL;

        n.flags |= FLAG_AUTO_CANCEL;
        // We have to cancel the original notification since it is in the
        // ongoing section and we want the "killed" notification to be a plain
        // notification.
        nm.cancel(alarm.serid);
        nm.notify(alarm.serid, n);
    }

    public static void enableAlert(Context context, final AlarmBean alarm,
                                   final long atTimeInMillis) {
        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(Alarms.ALARM_ALERT_ACTION);

        Log.d(Alarms.ALARM_CLOCKTIME_desc_key, "enableAlert()");
        Parcel out = Parcel.obtain();
        alarm.writeToParcel(out, 0);
        out.setDataPosition(0);
        intent.putExtra(Alarms.ALARM_RAW_DATA, out.marshall());

        PendingIntent sender = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        am.set(AlarmManager.RTC_WAKEUP, atTimeInMillis, sender);
        Log.d(Alarms.ALARM_CLOCKTIME_desc_key, "atTimeInMillis()" + atTimeInMillis);

        setStatusBarIcon(context, true);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(atTimeInMillis);
        String timeString = c.getTime().toString();
//        saveNextAlarm(context, timeString);
    }

    /**
     * Disables alert in AlarmManger and StatusBar.
     *
     * @param id Alarm ID.
     */
    static void disableAlert(Context context) {
        AlarmManager am = (AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent sender = PendingIntent.getBroadcast(
                context, 0, new Intent(Alarms.ALARM_ALERT_ACTION),
                PendingIntent.FLAG_CANCEL_CURRENT);
        am.cancel(sender);
        setStatusBarIcon(context, false);
//        saveNextAlarm(context, "");
    }

    /**
     * Tells the StatusBar whether the alarm is enabled or disabled
     */
    private static void setStatusBarIcon(Context context, boolean enabled) {
        Intent alarmChanged = new Intent("android.intent.action.ALARM_CHANGED");
        alarmChanged.putExtra("alarmSet", enabled);
        context.sendBroadcast(alarmChanged);
    }

    static void saveNextAlarm(final Context context, String timeString) {
        Settings.System.putString(context.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED,
                timeString);
    }


}