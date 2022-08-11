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
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;

import static io.wazo.callkeep.Constants.*;

import io.wazo.callkeep.activity.IncomingCallActivity;
import io.wazo.callkeep.activity.OutgoingCallActivity;

@TargetApi(Build.VERSION_CODES.M)
public class VoiceConnection extends Connection {
    private boolean isMuted = false;
    private HashMap<String, String> handle;
    private Context context;
    private final boolean mIsIncomingCall;
    private static final String TAG = "[Flutter] RNCK:VoiceConnection";

    VoiceConnection(Context context, HashMap<String, String> handle, boolean isIncomingCall) {
        super();
        this.handle = handle;
        this.context = context;
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

    @Override
    public void onShowIncomingCallUi() {
//        super.onShowIncomingCallUi();
        Log.i(TAG, "onShowIncomingCallUi ++");
        createNotificationChanel();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(context, IncomingCallActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context, 1, intent, 0);

        Notification.Builder builder = new Notification.Builder(context);
        builder.setOngoing(true);
        builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
        builder.setSound(null);
        builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setChannelId("callkit_incoming_channel_id");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(NotificationCompat.CATEGORY_CALL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        builder.setContentIntent(pi);
        builder.setFullScreenIntent(pi, true);
        builder.setSmallIcon(R.drawable.ic_ttgo_big);

        builder.setContentTitle("Your notification title");
        builder.setContentText("Your notification content.");

        String number = handle.get(EXTRA_CALL_NUMBER);
        String name = handle.get(EXTRA_CALLER_NAME);
        builder.setContentTitle(name);
        builder.setContentText(number);

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.notify("Call Notification", 37, notification);
    }

    private void createNotificationChanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelCall = new NotificationChannel(
                    "callkit_incoming_channel_id",
                    "Incoming Call",
                    NotificationManager.IMPORTANCE_MAX
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


    @Override
    public void onExtrasChanged(Bundle extras) {
        super.onExtrasChanged(extras);
        HashMap attributeMap = (HashMap<String, String>)extras.getSerializable("attributeMap");
        if (attributeMap != null) {
            handle = attributeMap;
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState state) {
        if (state.isMuted() == this.isMuted) {
            return;
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
        sendCallRequestToActivity(ACTION_END_CALL, handle);
        Log.d(TAG, "onDisconnect executed");
        try {
            ((VoiceConnectionService) context).deinitConnection(handle.get(EXTRA_CALL_UUID));
        } catch(Throwable exception) {
            Log.e(TAG, "Handle map error", exception);
        }
        destroy();
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
