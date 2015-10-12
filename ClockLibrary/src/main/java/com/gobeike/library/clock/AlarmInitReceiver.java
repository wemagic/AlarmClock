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

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.util.Log;

public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * Sets alarm on ACTION_BOOT_COMPLETED.  Resets alarm on
     * TIME_SET, TIMEZONE_CHANGED
     * 接受开机启动完成的广播，
     * 设置闹钟，当时区改变也设置
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.v("xuff", "AlarmInitReceiver" + action);


        // Make sure we set the next alert if needed.
        Intent nextClockBroad = new Intent(Alarms.NEXT_CLOCK_BROADCAST);
        context.sendBroadcast(nextClockBroad);
        AlarmBean bean = Alarms.getAlarm(context);
        AlarmReceiver.enableAlert(context, bean, bean.clockTime);
        ;
        Log.d(Alarms.ALARM_CLOCKTIME_desc_key, "AlarmInitReceiver()"+bean.id);

    }
}
