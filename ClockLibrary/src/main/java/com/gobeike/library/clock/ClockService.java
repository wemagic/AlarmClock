package com.gobeike.library.clock;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by xff on 2015/10/11.
 */
public class ClockService extends Service {

    public static final int UPDATE_CLOCK_OPTION = 1;//更新闹铃时间
    public static final int START_CLOCK_OPTION = 2;//启动闹铃
    public static final int Cancel_CLOCK_OPTION = 3;//取消闹铃

    public static final String CLOCK_Action = "clock_action";//判别服务操作


    private static final String PRE_FILENAME = "clock";


    /**
     * Play alarm up to 10 minutes before silencing
     */
    private static final int ALARM_TIMEOUT_SECONDS = 10 * 60;

    private static final long[] sVibratePattern = new long[]{500, 500};

    private boolean mPlaying = false;
    private Vibrator mVibrator;
    private MediaPlayer mMediaPlayer;
    private long mStartTime;
    private TelephonyManager mTelephonyManager;
    private int mInitialCallState;
    private AudioManager mAudioManager = null;
    private boolean mCurrentStates = true;
    private AlarmBean mCurrentAlarm;


//    public ClockService(MediaPlayer mMediaPlayer) {
//        this.mMediaPlayer = mMediaPlayer;
//    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private SharedPreferences preferences;

    public static void startNextClock() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PRE_FILENAME, Context.MODE_PRIVATE);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Listen for incoming calls to kill the alarm.
        mTelephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mTelephonyManager.listen(
                mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * 闹铃响时来电话，要停止闹铃
     */
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String ignored) {
            // The user might already be in a call when the alarm fires. When
            // we register onCallStateChanged, we get the initial in-call state
            // which kills the alarm. Check against the initial call state so
            // we don't kill the alarm during a call.
            if (state != TelephonyManager.CALL_STATE_IDLE
                    && state != mInitialCallState) {
//                sendKillBroadcast(mCurrentAlarm);
                stopSelf();
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            Log.v("wangxianming", "intent=null");

            stopSelf();
            return START_NOT_STICKY;
        }
        int flagAction = intent.getIntExtra(CLOCK_Action, -1);
        switch (flagAction) {
            case UPDATE_CLOCK_OPTION:
                long time = intent.getLongExtra(Alarms.ALARM_CLOCKTIME_key, 0);
                Log.v("wangxianming", "UPDATE_CLOCK_OPTION");

                String title = intent.getStringExtra(Alarms.ALARM_CLOCKTIME_desc_key);
                Alarms.putClockTime(preferences, time);
                Alarms.putClockTimeTitle(preferences, title, Alarms.ALARM_CLOCKTIME_desc_key);
                Alarms.putClockTimeTitle(preferences, intent.getStringExtra(Alarms.ALARM_CLOCKTIME_Id_key), Alarms.ALARM_CLOCKTIME_Id_key);
                AlarmReceiver.enableAlert(getApplicationContext(), Alarms.getAlarm(getApplicationContext()), time);
                break;
            case START_CLOCK_OPTION:
                wakeAndUnlock(true);

                final AlarmBean alarm = Alarms.getAlarm(getApplicationContext());
                Log.v("Play", "AlarmKlaxon play music");

                if (alarm == null) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
                if (mCurrentAlarm != null) {
                    sendKillBroadcast(mCurrentAlarm);
                }
                //startClock(alarm);
                play(alarm);
                mCurrentAlarm = alarm;
                // Record the initial call state here so that the new alarm has the
                // newest state.
                mInitialCallState = mTelephonyManager.getCallState();
                AlarmAlertWakeLock.acquireCpuWakeLock(this);
                Intent nextClockBroad = new Intent(Alarms.NEXT_CLOCK_BROADCAST);
                sendBroadcast(nextClockBroad);
                break;
            case Cancel_CLOCK_OPTION:
                stopSelf();
                wakeAndUnlock(false);
                break;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 启动闹铃
     */
    private void startClock(AlarmBean alarm) {
        //   if (alarm.clockTime == 0) return;
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(alarm.clockTime));
        play(alarm);
        mCurrentAlarm = alarm;
        // Record the initial call state here so that the new alarm has the
        // newest state.
        mInitialCallState = mTelephonyManager.getCallState();
    }


    private void sendKillBroadcast(AlarmBean alarm) {
        long millis = System.currentTimeMillis() - mStartTime;
        int minutes = (int) Math.round(millis / 60000.0);
        Intent alarmKilled = new Intent(Alarms.ALARM_KILLED);
        alarmKilled.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
        alarmKilled.putExtra(Alarms.ALARM_KILLED_TIMEOUT, minutes);
        sendBroadcast(alarmKilled);
    }

    // Volume suggested by media team for in-call alarms.
    private static final float IN_CALL_VOLUME = 0.125f;

    private AudioManager.OnAudioFocusChangeListener mAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    private void play(AlarmBean alarm) {
        // stop() checks to see if we are already playing.
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        stop();

        Uri alert = null;
        // Fall back on the default alarm if the database does not have an
        // alarm stored.
        if (alert == null) {
            alert = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM);
            Log.v("wangxianming", "Using default alarm: " + alert.toString());
        }

        // TODO: Reuse mMediaPlayer instead of creating a new one and/or use
        // RingtoneManager.
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.v("wangxianming", "Error occurred while playing audio.");
                mp.stop();
                mp.release();
                mMediaPlayer = null;
                return true;
            }
        });

        try {
            // Check if we are in a call. If we are, use the in-call alarm
            // resource at a low volume to not disrupt the call.
            if (mTelephonyManager.getCallState()
                    != TelephonyManager.CALL_STATE_IDLE) {
                Log.v("wangxianming", "Using the in-call alarm");
                mMediaPlayer.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME);
                setDataSourceFromResource(getResources(), mMediaPlayer,
                        R.raw.in_call_alarm);
            } else {
                mMediaPlayer.setDataSource(this, alert);
            }
            startAlarm(mMediaPlayer);
        } catch (Exception ex) {
            Log.v("wangxianming", "Using the fallback ringtone");
            // The alert may be on the sd card which could be busy right
            // now. Use the fallback ringtone.
            try {
                // Must reset the media player to clear the error state.
                mMediaPlayer.reset();
                setDataSourceFromResource(getResources(), mMediaPlayer,
                        R.raw.fallbackring);
                startAlarm(mMediaPlayer);
            } catch (Exception ex2) {
                // At this point we just don't play anything.
                Log.v("wangxianming", "Failed to play fallback ringtone" + ex2);
            }
        }


        mVibrator.vibrate(sVibratePattern, 0);


        enableKiller(alarm);
        mPlaying = true;
        mStartTime = System.currentTimeMillis();
    }

    // Do the common stuff when starting the alarm.
    private void startAlarm(MediaPlayer player)
            throws java.io.IOException, IllegalArgumentException,
            IllegalStateException {
        final AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        // do not play alarms if stream volume is 0
        // (typically because ringer mode is silent).
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) != 0) {
            player.setAudioStreamType(AudioManager.STREAM_ALARM);
            player.setLooping(true);
            player.prepare();
            player.start();
        }
    }

    private void setDataSourceFromResource(Resources resources,
                                           MediaPlayer player, int res) throws java.io.IOException {
        AssetFileDescriptor afd = resources.openRawResourceFd(res);
        if (afd != null) {
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
        }
    }

    /**
     * Stops alarm audio and disables alarm if it not snoozed and not
     * repeating
     */
    public void stop() {
        Log.v("wangxianming", "AlarmKlaxon.stop()");
        if (mPlaying) {
            mPlaying = false;

            Intent alarmDone = new Intent(Alarms.ALARM_DONE_ACTION);
            sendBroadcast(alarmDone);

            // Stop audio playing
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }

            // Stop vibrator
            mVibrator.cancel();
        }
        disableKiller();
    }

    /**
     * Kills alarm audio after ALARM_TIMEOUT_SECONDS, so the alarm
     * won't run all day.
     * <p/>
     * This just cancels the audio, but leaves the notification
     * popped, so the user will know that the alarm tripped.
     */
    private void enableKiller(AlarmBean alarm) {
        mHandler.sendMessageDelayed(mHandler.obtainMessage(KILLER, alarm),
                1000 * ALARM_TIMEOUT_SECONDS);
    }

    private void disableKiller() {
        mHandler.removeMessages(KILLER);
    }

    @Override
    public void onDestroy() {
        stop();
        // Stop listening for incoming calls.
        mTelephonyManager.listen(mPhoneStateListener, 0);
        AlarmAlertWakeLock.releaseCpuLock();
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
    }

    // Internal messages
    private static final int KILLER = 1;
    private static final int FOCUSCHANGE = 2;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KILLER:
                    Log.v("wangxianming", "*********** Alarm killer triggered ***********");
                    sendKillBroadcast((AlarmBean) msg.obj);
                    stopSelf();
                    break;
                case FOCUSCHANGE:
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:

                            if (!mPlaying && mMediaPlayer != null) {
                                stop();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

                            if (!mPlaying && mMediaPlayer != null) {
                                mMediaPlayer.pause();
                                mCurrentStates = false;
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:

                            if (mPlaying && !mCurrentStates) {
                                play(mCurrentAlarm);
                            }
                            break;
                        default:

                            break;
                    }
                default:
                    break;

            }
        }
    };


    KeyguardManager km = null;
    //    KeyguardManager.KeyguardLock kl = null;
    PowerManager pm = null;
    PowerManager.WakeLock wl = null;

    public void wakeAndUnlock(boolean b) {

        if (b) {
            pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");

            //点亮屏幕
            wl.acquire();

            km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            // kl = km.newKeyguardLock("unLock");

            //解锁
            //  kl.disableKeyguard();
        } else {
            //锁屏
            //if (kl!=null)  kl.reenableKeyguard();

            //释放wakeLock，关灯
            if (wl != null) wl.release();
        }

    }
}
