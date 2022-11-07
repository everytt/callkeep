/*
 * Copyright (c) 2016-2019 The CallKeep Authors (see the AUTHORS file)
 * SPDX-License-Identifier: ISC, MIT
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package io.wazo.callkeep;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;

import static io.wazo.callkeep.Constants.*;

import io.wazo.callkeep.activity.IncomingCallActivity;
import io.wazo.callkeep.activity.OutgoingCallActivity;
import io.wazo.callkeep.utils.ConstraintsMap;

@TargetApi(Build.VERSION_CODES.M)
public class VoiceConnection extends Connection {
    public static final String INCOMING_CALL_CHANNEL_ID = "INCOMING_CALL_CHANNEL_ID";
    public static final String EXTRA_PHONE_ACCOUNT_HANDLE = "io.wazo.callkeep.extra.PHONE_ACCOUNT_HANDLE";
    public static final String CALL_NOTIFICATION = "io.wazo.callkeep.INCOMING_CALL";

    private static int sNextCallId = 1;

    public enum AudioState {
        EARPEICE,
        SPEAKER,
        BLUETOOTH
    }

    private boolean isMuted = false;
    private AudioState audioState = AudioState.EARPEICE;
    private HashMap<String, String> handle;
    private Context context;
    private final boolean mIsIncomingCall;
    private final int mCallId;
    private static final String TAG = "[Flutter] RNCK:VoiceConnection";

    private RemoteViews notiView;

    public interface ConnectionListener {
        void onActive();
        void onDisconnected();
        void onAudioStateChanged(AudioState state);
    }

    private ConnectionListener mListener;


    VoiceConnection(Context context, HashMap<String, String> handle, boolean isIncomingCall) {
        super();
        Log.d(TAG, "createVoiceConnection : " +handle);

        this.handle = handle;
        this.context = context;
        mCallId = sNextCallId++;
        mIsIncomingCall = isIncomingCall;

        String number = handle.get(EXTRA_CALL_NUMBER);
        String name = handle.get(EXTRA_CALLER_NAME);

        if (number != null) {
            setAddress(Uri.parse(number), TelecomManager.PRESENTATION_ALLOWED);
        }
        if (name != null && !name.equals("")) {
            setCallerDisplayName(name, TelecomManager.PRESENTATION_ALLOWED);
        }
    }

    public int getCallId() {
        return mCallId;
    }

    public void setListener (ConnectionListener listener){
        mListener = listener;
    }

    private PendingIntent getAcceptPendingIntent(Context context, int callId) {
        Intent acceptIntent = IncomingNotificationReceiver.getAcceptIntent(context, callId);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getBroadcast(context, 0, acceptIntent,
            PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(context, 0, acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getDeclinePendingIntent(Context context, int callId) {
        Intent rejectIntent = IncomingNotificationReceiver.getDeclineIntent(context, callId);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getBroadcast(context, 0, rejectIntent,
            PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else
            return PendingIntent.getBroadcast(context, 0, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onShowIncomingCallUi() {
//        super.onShowIncomingCallUi();
        Log.i(TAG, "onShowIncomingCallUi ++ " + mCallId);
        createNotificationChanel();
        Intent intent = IncomingCallActivity.getIncomingCallIntent(context, mCallId, false, handle);
        PendingIntent pi;
        
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_MUTABLE| PendingIntent.FLAG_UPDATE_CURRENT);
        } else 
            pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(true);
        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
        builder.setSound(null);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(NotificationCompat.CATEGORY_CALL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        builder.setContentIntent(pi);
        builder.setFullScreenIntent(pi, true);

        int resIcon = context.getApplicationInfo().icon;
        builder.setSmallIcon(resIcon);
        builder.setColor(ContextCompat.getColor(context, R.color.action_color));

        String number = handle.get(EXTRA_CALL_NUMBER);
        Uri uri = Uri.parse(number);
        number  = uri.getSchemeSpecificPart();

        String name = handle.get(EXTRA_CALLER_NAME);

        if(name == null || name.isEmpty()) {
            name = number;
            number = "";
        }
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId(INCOMING_CALL_CHANNEL_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notiView = new RemoteViews(context.getPackageName(), R.layout.custom_notification_small);
            notiView.setTextViewText(R.id.tvNameCaller, name);
            notiView.setTextViewText(R.id.tvNumber, number);
            notiView.setOnClickPendingIntent(R.id.llAccept, getAcceptPendingIntent(context, mCallId));
            notiView.setOnClickPendingIntent(R.id.llDecline, getDeclinePendingIntent(context,mCallId));

            builder.setStyle(new Notification.DecoratedCustomViewStyle());
            builder.setCustomContentView(notiView);
            // D - HeadsUpContentView or BigContentView 설정 시 Noti 화면 내 확장버튼 출력되어 주석
            // builder.setCustomHeadsUpContentView(notiView);
            // builder.setCustomBigContentView(notiView);
        } else {
            builder.setContentTitle(name);
            builder.setContentText(number);
            builder.addAction(
                    new Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_accept),
                            "Accept",
                            getAcceptPendingIntent(context, mCallId))
                            .build());
            builder.addAction(
                    new Notification.Action.Builder(
                            Icon.createWithResource(context, R.drawable.ic_decline),
                            "Decline",
                            getDeclinePendingIntent(context, mCallId))
                            .build());
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.notify(CALL_NOTIFICATION, mCallId, notification);
    }

    private void createNotificationChanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelCall = new NotificationChannel(
                    INCOMING_CHANNEL_ID,
                    INCOMING_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channelCall.setDescription("");
            channelCall.setLightColor(Color.RED);
            channelCall.enableLights(true);
            channelCall.enableVibration(true);

            Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE);
            channelCall.setSound(ringtoneUri, new AudioAttributes.Builder()
                    // Setting the AudioAttributes is important as it identifies the purpose of your
                    // notification sound.
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(AudioManager.STREAM_RING)
                    .build());
            NotificationManager mgr = context.getSystemService(NotificationManager.class);
            mgr.createNotificationChannel(channelCall);
        }
    }

    public void cancelNotification() {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.cancel(CALL_NOTIFICATION, mCallId);
    }


    @Override
    public void onExtrasChanged(Bundle extras) {
        super.onExtrasChanged(extras);
        HashMap attributeMap = (HashMap<String, String>)extras.getSerializable("attributeMap");
        if (attributeMap != null) {
            handle = attributeMap;
        }
    }


    @Override
    public void onStateChanged(int state) {
        Log.d(TAG, "onStateChanged : " + state);

        if(state == Connection.STATE_DIALING) {
            Log.d(TAG, "onStateChanged : " + state);
            Intent intent = OutgoingCallActivity.getOutgoingCallIntent(context, mCallId, handle);
            context.startActivity(intent);
        } else if(state == Connection.STATE_ACTIVE) {
            if(mListener != null) mListener.onActive();
        } else if(state == Connection.STATE_DISCONNECTED) {
            if(mListener != null) mListener.onDisconnected();
        }

        super.onStateChanged(state);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        Log.d(TAG, "onCallAudioStateChanged : " + state);

        if (state.isMuted() == this.isMuted && state.getRoute() == this.audioState.ordinal()) {
            return;
        }else if(state.getRoute() == CallAudioState.ROUTE_SPEAKER) {
            if( mListener != null ) mListener.onAudioStateChanged(AudioState.SPEAKER);
        } else if(state.getRoute() == CallAudioState.ROUTE_EARPIECE) {
            if( mListener != null ) mListener.onAudioStateChanged(AudioState.EARPEICE);
        } else if(state.getRoute() == CallAudioState.ROUTE_BLUETOOTH) {
            if( mListener != null ) mListener.onAudioStateChanged(AudioState.BLUETOOTH);
        }


        this.isMuted = state.isMuted();
        sendCallRequestToActivity(isMuted ? ACTION_MUTE_CALL : ACTION_UNMUTE_CALL, handle);
    }


    @Override
    public void onAnswer() {
        super.onAnswer();
        Log.d(TAG, "onAnswer called");
        Log.d(TAG, "onAnswer ignored");
    }
    
    @Override
    public void onAnswer(int videoState) {
        super.onAnswer(videoState);
        Log.d(TAG, "onAnswer videoState called: " + videoState);
//        setConnectionCapabilities(getConnectionCapabilities() | Connection.CAPABILITY_HOLD);

        Intent intent = IncomingCallActivity.getIncomingCallIntent(context, mCallId, true, handle);
        context.startActivity(intent);

        cancelNotification();
        String name = handle.get(Constants.EXTRA_CALLER_NAME);

        if(name == null || name.isEmpty()) {
            String number = handle.get(Constants.EXTRA_CALL_NUMBER);
            Uri uri = Uri.parse(number);
            number  = uri.getSchemeSpecificPart();

            name = number;
        }
        ((VoiceConnectionService) context).startForegroundService(name, false);
        setAudioModeIsVoip(true);

        sendCallRequestToActivity(ACTION_ANSWER_CALL, handle);
        sendCallRequestToActivity(ACTION_AUDIO_SESSION, handle);
        Log.d(TAG, "onAnswer videoState executed");
    }

    @Override
    public void onPlayDtmfTone(char dtmf) {
        try {
            handle.put("DTMF", Character.toString(dtmf));
        } catch (Throwable exception) {
            Log.e(TAG, "Handle map error", exception);
        }
        sendCallRequestToActivity(ACTION_DTMF_TONE, handle);
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        if(mIsIncomingCall) cancelNotification();
        sendCallRequestToActivity(ACTION_END_CALL, handle);
        Log.d(TAG, "onDisconnect executed");
        try {
            ((VoiceConnectionService) context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        } catch(Throwable exception) {
            Log.e(TAG, "Handle map error", exception);
        }
        destroy();
    }



    public void setConnectionDisconnected(int cause) {
//        Log.d(TAG, "setConnectionDisconnected :: $cause");
        DisconnectCause c = new DisconnectCause(cause);

        Log.d(TAG, "setConnectionDisconnected :: "+ c);

        if(mIsIncomingCall && cause == DisconnectCause.REJECTED) cancelNotification();
        setDisconnected(c);

        sendCallRequestToActivity(ACTION_END_CALL, handle);
        ((VoiceConnectionService)context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        destroy();

        // listener
    }

    public void reportDisconnect(int reason) {
        super.onDisconnect();
        switch (reason) {
            case 1:
                setDisconnected(new DisconnectCause(DisconnectCause.ERROR));
                break;
            case 2:
            case 5:
                setDisconnected(new DisconnectCause(DisconnectCause.REMOTE));
                break;
            case 3:
                setDisconnected(new DisconnectCause(DisconnectCause.BUSY));
                break;
            case 4:
                setDisconnected(new DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE));
                break;
            case 6:
                setDisconnected(new DisconnectCause(DisconnectCause.MISSED));
                break;
            default:
                break;
        }
        ((VoiceConnectionService)context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        destroy();
    }

    @Override
    public void onAbort() {
        super.onAbort();
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        sendCallRequestToActivity(ACTION_END_CALL, handle);
        Log.d(TAG, "onAbort executed");
        try {
            ((VoiceConnectionService) context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        } catch(Throwable exception) {
            Log.e(TAG, "Handle map error", exception);
        }
        destroy();
    }

    @Override
    public void onHold() {
        super.onHold();
        this.setOnHold();
        sendCallRequestToActivity(ACTION_HOLD_CALL, handle);
    }

    @Override
    public void onUnhold() {
        super.onUnhold();
        sendCallRequestToActivity(ACTION_UNHOLD_CALL, handle);
        setActive();
    }

    @Override
    public void onReject() {
        super.onReject();
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        sendCallRequestToActivity(ACTION_END_CALL, handle);
        Log.d(TAG, "onReject executed");
        try {
            ((VoiceConnectionService) context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        } catch(Throwable exception) {
            Log.e(TAG, "Handle map error", exception);
        }
        destroy();
    }

    /*
     * Send call request to the RNCallKeepModule
     */
    private void sendCallRequestToActivity(final String action, @Nullable final HashMap attributeMap) {
        final VoiceConnection instance = this;
        final Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(action);
                if (attributeMap != null) {
                    Bundle extras = new Bundle();
                    extras.putSerializable("attributeMap", attributeMap);
                    intent.putExtras(extras);
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }
        });
    }
}
