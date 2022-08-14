package io.wazo.callkeep.activity;

import android.annotation.SuppressLint;
import android.app.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.telecom.DisconnectCause;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;

import io.wazo.callkeep.Constants;
import io.wazo.callkeep.R;
import io.wazo.callkeep.VoiceConnection;
import io.wazo.callkeep.VoiceConnectionService;
import io.wazo.callkeep.activity.listener.DebouncedOnClickListener;

public class IncomingCallActivity extends Activity implements VoiceConnection.ConnectionListener {
    public static final String TAG = "IncomingCallActivity";

    private VoiceConnection mConnection;
    private AudioManager mAudioManager;

    private TextView mTextTimer;
    private PanelLeft mBtnAnswer;
    private PanelRight mBtnIgnore;

    private View mBtnEnd;
    private Button mBtnSpeak;
    private Button mBtnBluetooth;

    private View mContainerCallingBtn;
    private View mContainerWaitingBtn;

    private PowerManager.WakeLock mProximityWakeLock;


    private long mStartTime;
    private Handler mHandler = new Handler();
    private BluetoothAdapter mBluetoothAdapter;
    private String toast_no_pair_bluetooth;


    public static Intent getIncomingCallIntent(Context context, int callId, boolean accepted, HashMap map) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(context, IncomingCallActivity.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        intent.putExtra(Constants.EXTRA_CALL_ACCEPTED, accepted);
        intent.putExtra(Constants.EXTRA_CALL_HANDLE, map);
        return intent;
    }


    @SuppressLint("DefaultLocale")
    private Runnable mTimerRunnable = new Runnable() {
        @Override
        public void run() { // todo junseo2 여기가 시간 보여주는곳 버그 있는지 체크 해볼것
            long time = (Calendar.getInstance().getTimeInMillis() - mStartTime) / 1000;

            long min = time / 60;
            long sec = time % 60;
            long hour = min / 60;

            String strTime = String.format("%02d : %02d : %02d", hour, min, sec);
            mTextTimer.setText(strTime);

            mHandler.postDelayed(mTimerRunnable, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initWindowFlag();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        Intent intent = getIntent();
        int callId = intent.getIntExtra(Constants.EXTRA_CALL_ID, 0);
        Log.i(TAG, "showing fullscreen answer ux for call id " + callId);

        boolean accepted = intent.getBooleanExtra(Constants.EXTRA_CALL_ACCEPTED, false);
        HashMap<String, String> map = (HashMap) intent.getSerializableExtra(Constants.EXTRA_CALL_HANDLE);

        toast_no_pair_bluetooth = map.get(Constants.EXTRA_TOAST_NO_PAIR_BLUETOOTH);
        String name = map.get(Constants.EXTRA_CALLER_NAME);

        String handle = map.get(Constants.EXTRA_CALL_NUMBER);
        Uri uri = Uri.parse(handle);
        handle  = uri.getSchemeSpecificPart();

        if(name == null || name.isEmpty()) {
            name = handle;
            handle = "";
        }

        setContentView(R.layout.activity_incoming_call);

        mConnection = (VoiceConnection) VoiceConnectionService.getConnectionById(callId);
        if(mConnection != null) mConnection.setListener(this);

        initWakeLock();

        mTextTimer = (TextView) findViewById(R.id.text_timer);

        TextView textName = (TextView) findViewById(R.id.text_name);
        textName.setText(name);
        TextView textNumber = (TextView) findViewById(R.id.text_phone_number);
        textNumber.setText(handle);

        TextView textReceiverName = findViewById(R.id.text_receiver_from_server);
//        textReceiverName.setText(mReceiverName);

        mBtnAnswer = (PanelLeft) findViewById(R.id.panel_left);
        mBtnIgnore = (PanelRight) findViewById(R.id.panel_right);
        mBtnEnd = findViewById(R.id.btn_cancel_calling);
        mBtnSpeak = (Button) findViewById(R.id.btn_speak);
        mBtnBluetooth = (Button) findViewById(R.id.btn_blue_tooth);

        mContainerCallingBtn = findViewById(R.id.container_calling);
        mContainerWaitingBtn = findViewById(R.id.container_waiting);

        initListener();

        initAudioManager();
        switchCallingView(accepted);
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

    private void initAudioManager() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        if (isBluetoothAvailable() && !mAudioManager.isBluetoothScoOn()) {
            mAudioManager.startBluetoothSco();
            mAudioManager.setBluetoothScoOn(true);
        }

        mBtnBluetooth.setBackgroundResource(mAudioManager.isBluetoothScoOn() ?
                R.drawable.call_btn_bluetooth_on :
                R.drawable.call_btn_bluetooth_off);
    }

    private void initListener() {
        mBtnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeToSpeakMode();
            }
        });

        mBtnAnswer.setOnPanelListener(new PanelLeft.OnPanelListener() {
            @Override
            public void onPanelEnd() {
                if(mConnection != null) mConnection.onAnswer();
                switchCallingView(true);

            }
        });

        mBtnIgnore.setOnPanelListener(new PanelRight.OnPanelListener() {
            @Override
            public void onPanelEnd() {
                if(mConnection != null) {
                    mConnection.setConnectionDisconnected(DisconnectCause.REJECTED);
                    mConnection.destroy();
                }
//                finish();
            }
        });

        mBtnEnd.setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
                if(mConnection != null) {
                    mConnection.setConnectionDisconnected(DisconnectCause.LOCAL);
                    mConnection.destroy();
                }
//                finish();
            }
        });

        mBtnBluetooth.setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (isBluetoothAvailable()) {
                    changeToBlueTooth();
                } else {
                    Toast.makeText(getApplicationContext(), toast_no_pair_bluetooth, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void switchCallingView(boolean hasToSwitch) {
        if (hasToSwitch) {
            if (mProximityWakeLock != null) {
                mProximityWakeLock.acquire();
            }

            mContainerCallingBtn.setVisibility(View.VISIBLE);
            mContainerWaitingBtn.setVisibility(View.GONE);

            mBtnEnd.setVisibility(View.VISIBLE);

            mBtnAnswer.setVisibility(View.GONE);
            mBtnIgnore.setVisibility(View.GONE);
            mTextTimer.setVisibility(View.VISIBLE);
            startTimer();

        } else {
            mContainerCallingBtn.setVisibility(View.GONE);
            mContainerWaitingBtn.setVisibility(View.VISIBLE);

            mBtnEnd.setVisibility(View.GONE);
            mTextTimer.setVisibility(View.GONE);
            mBtnAnswer.setVisibility(View.VISIBLE);
            mBtnIgnore.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("DefaultLocale")
    private void startTimer() {
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

    private void changeToSpeakMode() {
        if (mAudioManager != null) {
            if (mAudioManager.isSpeakerphoneOn()) {
                mAudioManager.setSpeakerphoneOn(false);
                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);

                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_off);
            } else {
                mAudioManager.setSpeakerphoneOn(true);
                mBtnSpeak.setBackgroundResource(R.drawable.call_btn_call_speaker_on);
            }

            mBtnBluetooth.setBackgroundResource(mAudioManager.isBluetoothScoOn() ?
                    R.drawable.call_btn_bluetooth_on :
                    R.drawable.call_btn_bluetooth_off);
        }
    }

    private void changeToBlueTooth() {
        if (mAudioManager != null) {
            if (isBluetoothAvailable()) {
                if (mAudioManager.isBluetoothScoOn()) {
                    mAudioManager.setSpeakerphoneOn(false);
                    mAudioManager.stopBluetoothSco();
                    mAudioManager.setBluetoothScoOn(false);

                    mBtnBluetooth.setBackgroundResource(R.drawable.call_btn_bluetooth_off);
                } else {
                    mAudioManager.setSpeakerphoneOn(false);
                    mAudioManager.startBluetoothSco();
                    mAudioManager.setBluetoothScoOn(true);

                    mBtnBluetooth.setBackgroundResource(R.drawable.call_btn_bluetooth_on);
                }
            } else {
                Toast.makeText(getApplicationContext(), toast_no_pair_bluetooth, Toast.LENGTH_SHORT).show();
            }

            mBtnSpeak.setBackgroundResource(mAudioManager.isSpeakerphoneOn() ?
                    R.drawable.call_btn_call_speaker_on :
                    R.drawable.call_btn_call_speaker_off);
        }
    }

    private boolean isBluetoothAvailable() {
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

    @Override
    public void onBackPressed() {
        Log.i(getClass().getSimpleName(), "moveTaskToBack!");

        moveTaskToBack(true);
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

        super.onDestroy();

    }

    @Override
    public void onActive() {
        // ignore
    }

    @Override
    public void onDisconnected() {
        finish();
    }
}
