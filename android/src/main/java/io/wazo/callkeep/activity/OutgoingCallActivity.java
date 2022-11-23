package io.wazo.callkeep.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.HashMap;

import io.wazo.callkeep.Constants;
import io.wazo.callkeep.R;
import io.wazo.callkeep.VoiceConnection;
import io.wazo.callkeep.VoiceConnectionService;
import io.wazo.callkeep.activity.listener.DebouncedOnClickListener;

public class OutgoingCallActivity extends CallActivity implements VoiceConnection.ConnectionListener{
    public static final String TAG = CallActivity.TAG + "- OUTGOING";

    private static final int REQUEST_PERMISSION = 19;

    private View mTextWaitingBigMsg;
    private View mTextWaitingMsg;

    private View mContainerCallingBtn;
    private View mContainerWaitingBtn;

    public static Intent getOutgoingCallIntent(Context context, int callId, HashMap handle) {
        Log.d("OUTGOINGCALL", "getOutgoingCallIntent : $handle");
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.setClass(context, OutgoingCallActivity.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        intent.putExtra(Constants.EXTRA_CALL_HANDLE, handle);
        return intent;
    }

    boolean noInsert = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_outgoing_call);

        Intent intent = getIntent();
        int callId = intent.getIntExtra(Constants.EXTRA_CALL_ID, 0);
        HashMap<String, String> map = (HashMap) intent.getSerializableExtra(Constants.EXTRA_CALL_HANDLE);

        toast_no_pair_bluetooth = map.get(Constants.EXTRA_TOAST_NO_PAIR_BLUETOOTH);
        if(toast_no_pair_bluetooth == null) {
            toast_no_pair_bluetooth = getString(R.string.toast_no_paired_bluetooth);
        }
        String name = map.get(Constants.EXTRA_CALLER_NAME);
        String handle = map.get(Constants.EXTRA_CALL_NUMBER);

        Uri uri = Uri.parse(handle);
        handle  = uri.getSchemeSpecificPart();
        mConnection = (VoiceConnection) VoiceConnectionService.getConnectionById(callId);
        Log.i(TAG, "showing fullscreen answer ux for call id " + callId);

        mConnection.setListener(this);
        init(name, handle);
        startDestroyCaptureService(callId);
    }

    private void init(String name, String handle) {
        if(name == null || name.isEmpty()) {
            name = handle;
            handle = "";
        }
        mTextName = findViewById(R.id.text_name);
        mProductName = name;
        mTextName.setText(name);
        mTextPhoneNumber = findViewById(R.id.text_phone_number);
        mTextPhoneNumber.setText(handle);

        mTextTimer = findViewById(R.id.text_timer);

        mTextWaitingBigMsg = findViewById(R.id.text_waiting_big_msg);
        mTextWaitingMsg = findViewById(R.id.text_waiting_message);

        mContainerCallingBtn = findViewById(R.id.container_calling);
        mContainerWaitingBtn = findViewById(R.id.container_waiting);

        mBtnSpeak = findViewById(R.id.btn_speak);
        mBtnBluetooth = findViewById(R.id.btn_blue_tooth);
        mBtnKeyPad = findViewById(R.id.btn_keypad);

        mContainerCallInfo = findViewById(R.id.icon_area);

        mKeyPadView = findViewById(R.id.keypad_view);
        initKeyPadView(mKeyPadView);

        switchCallingView(false);

        initListener();
        applyBluetoothStatusUI();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Please allow permissions.", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }

//            init();
        }
    }

    private void initListener() {
        mBtnSpeak.setOnClickListener(view -> changeToSpeakMode());

        findViewById(R.id.btn_cancel_calling).setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
                if(mConnection != null) {
                    mConnection.setConnectionDisconnected(DisconnectCause.LOCAL);
                }
//                finish();
            }
        });

        findViewById(R.id.btn_cancel_waiting).setOnClickListener(new DebouncedOnClickListener() {
            @Override
            public void onDebouncedClick(View view) {
                if(mConnection != null) {
                    mConnection.setConnectionDisconnected(DisconnectCause.LOCAL);
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
            mTextName.setVisibility(View.VISIBLE);
            mTextPhoneNumber.setVisibility(View.VISIBLE);
            mTextTimer.setVisibility(View.VISIBLE);

            mContainerWaitingBtn.setVisibility(View.GONE);
            mTextWaitingBigMsg.setVisibility(View.GONE);
            mTextWaitingMsg.setVisibility(View.GONE);

            startTimer();
        } else {
            mContainerCallingBtn.setVisibility(View.VISIBLE);
//            mTextName.setVisibility(View.GONE);
            mTextPhoneNumber.setVisibility(View.GONE);
            mTextTimer.setVisibility(View.GONE);

            mContainerWaitingBtn.setVisibility(View.GONE);
            mTextWaitingBigMsg.setVisibility(View.VISIBLE);
            mTextWaitingMsg.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAudioStateChanged(VoiceConnection.AudioState state) {
        super.onAudioStateChanged(state);
    }

    @Override
    public void onActive() {
        Log.i(TAG, "onActive");
        switchCallingView(true);
    }

    @Override
    public void onDisconnected() {
        finish();
    }
}