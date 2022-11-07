package io.wazo.callkeep.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Calendar;

import io.wazo.callkeep.R;
import io.wazo.callkeep.MyService;
import io.wazo.callkeep.VoiceConnection;

public class CallActivity extends Activity {
    public static final String TAG = "[Flutter] CallActivity";

    private long mStartTime;
    private PowerManager.WakeLock mProximityWakeLock;

    private AudioManager mAudioManager;
    private BluetoothAdapter mBluetoothAdapter;

    public VoiceConnection mConnection;

    public TextView mTextTimer;
    public Button mBtnSpeak;
    public Button mBtnBluetooth;
    public String toast_no_pair_bluetooth;

    private VoiceConnection.AudioState audioState = VoiceConnection.AudioState.EARPEICE;

    private Handler mHandler = new Handler();
    @SuppressLint("DefaultLocale")
    private Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() {
            long now = Calendar.getInstance().getTimeInMillis();

            long time = (now - mStartTime) / 1000;

            long min = time / 60;
            long sec = time % 60;
            long hour = min / 60;

            String strTime = String.format("%02d : %02d : %02d", hour, min, sec);
            if(mTextTimer != null) mTextTimer.setText(strTime);

            mHandler.postDelayed(mTimerRunnable, 1000L);
        }
    };


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initWindowFlag();
        initWakeLock();

        initAudioManager();
    }

    public void startDestroyCaptureService(int callId){
        Intent serviceIntent = new Intent(getBaseContext(), MyService.class);
        serviceIntent.putExtra("callId", callId);
        startService(serviceIntent);
    }

    private void initAudioManager() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        if (isBluetoothAvailable() && !mAudioManager.isBluetoothScoOn()) {
            mAudioManager.startBluetoothSco();
            mAudioManager.setBluetoothScoOn(true);
            audioState = VoiceConnection.AudioState.BLUETOOTH;
        }
    }

    private void initWindowFlag() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
//            window.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private void initWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mProximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, getLocalClassName());
        } else {
            try {
                int proximityScreenOffWakeLock = PowerManager.class.getClass().getField("PROXIMITY_SCREEN_OFF_WAKE_LOCK").getInt(null);
                if (proximityScreenOffWakeLock != 0x0) {
                    mProximityWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, getLocalClassName());
                    mProximityWakeLock.setReferenceCounted(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
                mProximityWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getLocalClassName());
            }
        }
    }

    @SuppressLint("DefaultLocale")
    public void startTimer() {
        String strTime = String.format("%02d : %02d : %02d", 0, 0, 0);
        mTextTimer.setText(strTime);

        mStartTime = Calendar.getInstance().getTimeInMillis();
        mHandler.postDelayed(mTimerRunnable, 1000L);
    }

    private void stopTimer() {
        try {
            mHandler.removeCallbacks(mTimerRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void changeToSpeakMode() {
        Log.d(TAG, "changeToSpeakMode ++ ");

        if (mAudioManager != null) {
            if (mAudioManager.isSpeakerphoneOn()) {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    mConnection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
                else {
                    mAudioManager.setSpeakerphoneOn(false);
                    mAudioManager.stopBluetoothSco();
                    mAudioManager.setBluetoothScoOn(false);
                }
//                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_off );
            } else {
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    mConnection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
                else
                    mAudioManager.setSpeakerphoneOn(true);
//                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_on);
            }

            applyBluetoothStatusUI();
        }
    }

    public void applyBluetoothStatusUI() {
        if (isBluetoothAvailable()) {
            mBtnBluetooth.setBackgroundResource(audioState == VoiceConnection.AudioState.BLUETOOTH ?
                    R.drawable.btn_bluetooth_on :
                    R.drawable.call_btn_bluetooth_off);
        } else {
            mBtnBluetooth.setBackgroundResource(R.drawable.call_btn_bluetooth_off);
        }
    }
    public boolean isBluetoothScoOn() {
        boolean isOn = mAudioManager.isBluetoothScoOn();
        Log.i(TAG, "isBluetoothScoOn : " + (isOn ? "ON" : "OFF"));
        return isOn;
    }

    public boolean isSpeakerphoneOn() {
        boolean isOn = mAudioManager.isSpeakerphoneOn();
        Log.i(TAG, "isSpeakerphoneOn : " + (isOn ? "ON" : "OFF"));
        return isOn;
    }


    public boolean isBluetoothAvailable() {
        if (mAudioManager != null) {
            if (mBluetoothAdapter != null &&
                    mBluetoothAdapter.isEnabled() &&
                    mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED &&
                    mBluetoothAdapter.getBondedDevices() != null &&
                    mBluetoothAdapter.getBondedDevices().size() > 0) {
                return true;
            }
        }
        return false;
    }

    public void changeToBluetooth() {
        Log.i(TAG, "changeToBluetooth ++");
        if (mAudioManager != null) {
            if (isBluetoothAvailable()) {
                if (audioState == VoiceConnection.AudioState.BLUETOOTH) {
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        mConnection.setAudioRoute(CallAudioState.ROUTE_EARPIECE);
                    } else {
                        mAudioManager.setSpeakerphoneOn(false);
                        mAudioManager.stopBluetoothSco();
                        mAudioManager.setBluetoothScoOn(false);
                    }
//                    mBtnBluetooth.setBackgroundResource(R.drawable.btn_bluetooth_off);
                } else {
                    if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        mConnection.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH);
                    } else {
                        mAudioManager.setSpeakerphoneOn(false);
                        mAudioManager.startBluetoothSco();
                        mAudioManager.setBluetoothScoOn(true);
                    }
//                    mBtnBluetooth.setBackgroundResource(R.drawable.btn_bluetooth_on);
                }
            } else {
                Toast.makeText(getApplicationContext(), toast_no_pair_bluetooth, Toast.LENGTH_SHORT).show();
            }

//            mBtnSpeak.setBackgroundResource(isSpeakerphoneOn() ?
//                    R.drawable.call_btn_call_speaker_on :
//                    R.drawable.call_btn_call_speaker_off);
        }
        Log.i(TAG, "changeToBluetooth --");

    }

    public void acquireProximity() {
        if (mProximityWakeLock != null) {
            mProximityWakeLock.acquire();
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();

        if (mProximityWakeLock != null) {
            if (mProximityWakeLock.isHeld()) {
                mProximityWakeLock.release();
            }
            mProximityWakeLock = null;
        }

        stopService(new Intent(getBaseContext(), MyService.class));

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.i(getClass().getSimpleName(), "moveTaskToBack!");

        moveTaskToBack(true);
    }

    public void onAudioStateChanged(VoiceConnection.AudioState state) {
        Log.i(TAG, "onAudioStateChanged : " + state.name());
        audioState = state;
        if(state == VoiceConnection.AudioState.EARPEICE){
            mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_off);
            mBtnBluetooth.setBackgroundResource(isBluetoothAvailable() ? R.drawable.btn_bluetooth_off : R.drawable.call_btn_bluetooth_off);
        }else if( state == VoiceConnection.AudioState.SPEAKER) {
            mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_on);
            mBtnBluetooth.setBackgroundResource(isBluetoothAvailable() ? R.drawable.btn_bluetooth_off : R.drawable.call_btn_bluetooth_off);
        } else if( state == VoiceConnection.AudioState.BLUETOOTH) {
            mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_off);
            mBtnBluetooth.setBackgroundResource(R.drawable.btn_bluetooth_on);
        }
    }

}
