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
import android.telecom.DisconnectCause;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;

import io.wazo.callkeep.Constants;
import io.wazo.callkeep.R;
import io.wazo.callkeep.VoiceConnection;
import io.wazo.callkeep.VoiceConnectionService;
import io.wazo.callkeep.activity.listener.DebouncedOnClickListener;

import static io.wazo.callkeep.Constants.EXTRA_CALL_ID;


public class IncomingCallActivity extends Activity {
    public static final String TAG = "IncomingCallActivity";

    public static final String EXTRA_KEY_FRIEND_NAME = "IncomingCallActivity.extra_key_friend_name";
    public static final String EXTRA_KEY_FRIEND_ID = "IncomingCallActivity.extra_key_friend_id";
    public static final String EXTRA_KEY_RECEIVED_MSG = "extra_key_received_msg";
    public static final int CALL_NOTI_ID = 909;
    public static final int CALL_NOTI_ONGOING_ID = 90;

    private VoiceConnection mConnection;
    private AudioManager mAudioManager;

    private TextView mTextTimer;

    // incoming
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

        setContentView(R.layout.activity_incoming_call);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
//            window.addFlags(
//                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
//                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
//            );
        }

        Intent intent = getIntent();
        int callId = intent.getIntExtra(Constants.EXTRA_CALL_ID, 0);
        Log.i(TAG, "showing fullscreen answer ux for call id " + callId);

        mConnection = (VoiceConnection) VoiceConnectionService.getConnectionById(callId);

        initWakeLock();

        if (mProximityWakeLock != null) {
            mProximityWakeLock.acquire();
        }
        mTextTimer = (TextView) findViewById(R.id.text_timer);


        TextView textName = (TextView) findViewById(R.id.text_name);
//        textName.setText(name);
        TextView textNumber = (TextView) findViewById(R.id.text_phone_number);
//        textNumber.setText(handle);

        TextView textReceiverName = findViewById(R.id.text_receiver_from_server);
//        textReceiverName.setText(mReceiverName);

        mBtnAnswer = (PanelLeft) findViewById(R.id.panel_left);
        mBtnIgnore = (PanelRight) findViewById(R.id.panel_right);
        mBtnEnd = findViewById(R.id.btn_cancel_calling);
        mBtnSpeak = (Button) findViewById(R.id.btn_speak);
        mBtnBluetooth = (Button) findViewById(R.id.btn_blue_tooth);

        mContainerCallingBtn = findViewById(R.id.container_calling);
        mContainerWaitingBtn = findViewById(R.id.container_waiting);

        switchCallingView(false);

        initListener();

        initAudioManager();
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
//                switchCallingView(true);

            }
        });

        mBtnIgnore.setOnPanelListener(new PanelRight.OnPanelListener() {
            @Override
            public void onPanelEnd() {
                if(mConnection != null) {
                    mConnection.setConnectionDisconnected(DisconnectCause.REJECTED);
                    mConnection.destroy();
                }
                finish();
            }
        });

        mBtnEnd.setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
            }
        });

        mBtnBluetooth.setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (isBluetoothAvailable()) {
                    changeToBlueTooth();
                } else {
                }
            }
        });
    }

    private void switchCallingView(boolean hasToSwitch) {
        if (hasToSwitch) {
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
//                Toast.makeText(getApplicationContext(), R.string.msg_there_is_no_paired_bluetooth, Toast.LENGTH_SHORT).show();
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
}
