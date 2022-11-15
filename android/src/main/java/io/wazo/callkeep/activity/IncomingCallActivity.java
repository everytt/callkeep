package io.wazo.callkeep.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

import io.wazo.callkeep.Constants;
import io.wazo.callkeep.MyService;
import io.wazo.callkeep.R;
import io.wazo.callkeep.VoiceConnection;
import io.wazo.callkeep.VoiceConnectionService;
import io.wazo.callkeep.activity.listener.DebouncedOnClickListener;

public class IncomingCallActivity extends CallActivity implements VoiceConnection.ConnectionListener {
    public static final String TAG = CallActivity.TAG + "- INCOMING";

    private PanelLeft mBtnAnswer;
    private PanelRight mBtnIgnore;
    private View mBtnEnd;

    private View mContainerCallingBtn;
    private View mContainerWaitingBtn;


    public static Intent getIncomingCallIntent(Context context, int callId, boolean accepted, HashMap map) {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(context, IncomingCallActivity.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        intent.putExtra(Constants.EXTRA_CALL_ACCEPTED, accepted);
        intent.putExtra(Constants.EXTRA_CALL_HANDLE, map);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate ++");

        Intent intent = getIntent();
        int callId = intent.getIntExtra(Constants.EXTRA_CALL_ID, 0);
        Log.i(TAG, "showing fullscreen answer ux for call id " + callId);

        boolean accepted = intent.getBooleanExtra(Constants.EXTRA_CALL_ACCEPTED, false);
        String displayName;
        String handle;

        try {
            HashMap<String, String> map = (HashMap) intent.getSerializableExtra(Constants.EXTRA_CALL_HANDLE);

            toast_no_pair_bluetooth = map.get(Constants.EXTRA_TOAST_NO_PAIR_BLUETOOTH);
            displayName = map.get(Constants.EXTRA_CALLER_NAME);

            handle = map.get(Constants.EXTRA_CALL_NUMBER);
            Uri uri = Uri.parse(handle);
            handle  = uri.getSchemeSpecificPart();

            if(displayName == null || displayName.isEmpty()) {
                displayName = handle;
                handle = "";
            }
        } catch( Exception e) {
            Log.i(TAG, "exception :::: " + e);
            displayName = "0000";
            handle = "";
        }

        setContentView(R.layout.activity_incoming_call);

        mConnection = (VoiceConnection) VoiceConnectionService.getConnectionById(callId);
        if(mConnection != null) mConnection.setListener(this);

        mTextTimer = (TextView) findViewById(R.id.text_timer);

        mTextName = (TextView) findViewById(R.id.text_name);
        mProductName = displayName;
        mTextName.setText(displayName);
        mTextPhoneNumber = (TextView) findViewById(R.id.text_phone_number);
        mTextPhoneNumber.setText(handle);

        mBtnAnswer = (PanelLeft) findViewById(R.id.panel_left);
        mBtnIgnore = (PanelRight) findViewById(R.id.panel_right);
        mBtnEnd = findViewById(R.id.btn_cancel_calling);
        mBtnSpeak = (Button) findViewById(R.id.btn_speak);
        mBtnBluetooth = (Button) findViewById(R.id.btn_blue_tooth);
        mBtnKeyPad = findViewById(R.id.btn_keypad);

        mContainerCallInfo = findViewById(R.id.icon_area);
        mKeyPadView = findViewById(R.id.keypad_view);
        initKeyPadView(mKeyPadView);

        mContainerCallingBtn = findViewById(R.id.container_calling);
        mContainerWaitingBtn = findViewById(R.id.container_waiting);

        initListener();
        applyBluetoothStatusUI();

        switchCallingView(accepted);
        Log.i(TAG, "onCreate --");
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
                    changeToBluetooth();
                } else {
                    Toast.makeText(getApplicationContext(), toast_no_pair_bluetooth, Toast.LENGTH_SHORT).show();
                }
            }
        });

        mBtnKeyPad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeKeyPadButton();
            }
        });
    }

    private void switchCallingView(boolean hasToSwitch) {
        if (hasToSwitch) {
            acquireProximity();

            mContainerCallingBtn.setVisibility(View.VISIBLE);
            mContainerWaitingBtn.setVisibility(View.GONE);
            mContainerCallInfo.setVisibility(View.VISIBLE);

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

    @Override
    public void onActive() {
        // ignore
    }

    @Override
    public void onDisconnected() {
        finish();
    }

    @Override
    public void onAudioStateChanged(VoiceConnection.AudioState state) {
        super.onAudioStateChanged(state);
    }
}
