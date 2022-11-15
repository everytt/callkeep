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
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.wazo.callkeep.activity.IncomingCallActivity;
import io.wazo.callkeep.activity.OutgoingCallActivity;
import io.wazo.callkeep.utils.ConstraintsMap;
import static io.wazo.callkeep.Constants.*;
import static io.wazo.callkeep.Constants.FOREGROUND_SERVICE_TYPE_MICROPHONE;

// @see https://github.com/kbagchiGWC/voice-quickstart-android/blob/9a2aff7fbe0d0a5ae9457b48e9ad408740dfb968/exampleConnectionService/src/main/java/com/twilio/voice/examples/connectionservice/VoiceConnectionService.java
@TargetApi(Build.VERSION_CODES.M)
public class VoiceConnectionService extends ConnectionService {
    private static Boolean isAvailable;
    private static Boolean isInitialized;
    private static Boolean isReachable;
    private static String notReachableCallUuid;
    private static ConnectionRequest currentConnectionRequest;
    private static PhoneAccountHandle phoneAccountHandle = null;
    private static String TAG = "[Flutter] RNCK:VoiceConnectionService";
    public static Map<String, VoiceConnection> currentConnections = new HashMap<>();
    public static Boolean hasOutgoingCall = false;
    public static Boolean isCalling = false;
    public static VoiceConnectionService currentConnectionService = null;
    public static ConstraintsMap _settings = null;

    public static final String ONGOING_CHANNEL_ID = "io.winehouse.ttgo.call";

    public static Connection getConnection(String connectionId) {
        if (currentConnections.containsKey(connectionId)) {
            return currentConnections.get(connectionId);
        }
        return null;
    }

    public static Connection getConnectionById(int callId) {
        for(VoiceConnection connection : currentConnections.values()) {
            if (connection.getCallId() == callId) {
                return connection;
            }
        }
        return null;
    }

    public VoiceConnectionService() {
        super();
        isReachable = false;
        isInitialized = false;
        isAvailable = false;
        currentConnectionRequest = null;
        currentConnectionService = this;
    }

    public static void setPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle) {
        VoiceConnectionService.phoneAccountHandle = phoneAccountHandle;
    }

    public static void setAvailable(Boolean value) {
        Log.d(TAG, "setAvailable: " + (value ? "true" : "false"));
        if (value) {
            isInitialized = true;
        }

        isAvailable = value;
    }

    public static void setSettings(ConstraintsMap settings) {
        _settings = settings;
    }

    public static void setReachable() {
        Log.d(TAG, "setReachable");
        isReachable = true;
        VoiceConnectionService.currentConnectionRequest = null;
    }

    public static void deinitConnection(String connectionId) {
        Log.d(TAG, "deinitConnection:" + connectionId);
        VoiceConnectionService.hasOutgoingCall = false;
        VoiceConnectionService.isCalling = false;

        currentConnectionService.stopForegroundService(false);

        if (currentConnections.containsKey(connectionId)) {
            currentConnections.remove(connectionId);
        }
    }

    public static void deinitConnectionByCallId(int callId) {
        Log.d(TAG, "deinitConnectionByCallId:" + callId);

        VoiceConnectionService.hasOutgoingCall = false;
        VoiceConnectionService.isCalling = false;

        currentConnectionService.stopForegroundService(true);

        for(VoiceConnection connection : currentConnections.values()) {
            if (connection.getCallId() == callId) {
                connection.onDisconnect();
                currentConnections.remove(connection);
            }
        }
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.d(TAG, "onCreateIncomingConnection ::: ");
        VoiceConnectionService.isCalling = true;
        Bundle extra = request.getExtras();
        Uri number = request.getAddress();
        String name = extra.getString(EXTRA_CALLER_NAME);
        Connection incomingCallConnection = createConnection(request, true);
        incomingCallConnection.setRinging();
        incomingCallConnection.setInitialized();


//        startForegroundService();

        return incomingCallConnection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Log.d(TAG, "onCreateOutgoingConnection ::: ");
        VoiceConnectionService.hasOutgoingCall = true;
//        String uuid = UUID.randomUUID().toString();

//        if (!isInitialized && !isReachable) {
//            this.notReachableCallUuid = uuid;
//            this.currentConnectionRequest = request;
//            this.checkReachability();
//        }

        Bundle extras = request.getExtras();

        String number = request.getAddress().getSchemeSpecificPart();
        String extrasNumber = extras.getString(EXTRA_CALL_NUMBER);
        String displayName = extras.getString(EXTRA_CALLER_NAME);
        Connection outgoingCallConnection = createConnection(request, false);
        outgoingCallConnection.setDialing();
        outgoingCallConnection.setAudioModeIsVoip(true);
        outgoingCallConnection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED);


        String displayServiceTitle = displayName;
        if(displayName == null || displayName.isEmpty()) {
            Uri uri = Uri.parse(extrasNumber);
            displayServiceTitle  = uri.getSchemeSpecificPart();
        }

        startForegroundService(displayServiceTitle, true);

        // ‍️Weirdly on some Samsung phones (A50, S9...) using `setInitialized` will not display the native UI ...
        // when making a call from the native Phone application. The call will still be displayed correctly without it.
        if (!Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            outgoingCallConnection.setInitialized();
        }

        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        sendCallRequestToActivity(ACTION_ONGOING_CALL, extrasMap);
        sendCallRequestToActivity(ACTION_AUDIO_SESSION, extrasMap);

        return outgoingCallConnection;

//        return this.makeOutgoingCall(request, uuid, false);
    }

    private Connection makeOutgoingCall(ConnectionRequest request, String uuid, Boolean forceWakeUp) {
        Bundle extras = request.getExtras();
        Connection outgoingCallConnection = null;
        String number = request.getAddress().getSchemeSpecificPart();
        String extrasNumber = extras.getString(EXTRA_CALL_NUMBER);
        String displayName = extras.getString(EXTRA_CALLER_NAME);
        Boolean isForeground = VoiceConnectionService.isRunning(this.getApplicationContext());

        Log.d(TAG, "makeOutgoingCall:" + uuid + ", number: " + number + ", displayName:" + displayName);

        // Wakeup application if needed
        if (!isForeground || forceWakeUp) {
            Log.d(TAG, "onCreateOutgoingConnection: Waking up application");
            this.wakeUpApplication(uuid, number, displayName);
        } else if (!this.canMakeOutgoingCall() && isReachable) {
            Log.d(TAG, "onCreateOutgoingConnection: not available");
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.LOCAL));
        }

        // TODO: Hold all other calls
        if (extrasNumber == null || !extrasNumber.equals(number)) {
            extras.putString(EXTRA_CALL_UUID, uuid);
            extras.putString(EXTRA_CALLER_NAME, displayName);
            extras.putString(EXTRA_CALL_NUMBER, number);
        }

        outgoingCallConnection = createConnection(request, false);
        outgoingCallConnection.setDialing();
        outgoingCallConnection.setAudioModeIsVoip(true);
        outgoingCallConnection.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED);

//        startForegroundService();

        // ‍️Weirdly on some Samsung phones (A50, S9...) using `setInitialized` will not display the native UI ...
        // when making a call from the native Phone application. The call will still be displayed correctly without it.
        if (!Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            outgoingCallConnection.setInitialized();
        }

        HashMap<String, String> extrasMap = this.bundleToMap(extras);

        sendCallRequestToActivity(ACTION_ONGOING_CALL, extrasMap);
        sendCallRequestToActivity(ACTION_AUDIO_SESSION, extrasMap);

        Log.d(TAG, "onCreateOutgoingConnection: calling");

        return outgoingCallConnection;
    }

    public void startForegroundService(String endPoint, boolean outgoing) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Foreground services not required before SDK 28
            return;
        }
        Log.d(TAG, "[VoiceConnectionService] startForegroundService");
        if (_settings == null || !_settings.hasKey("foregroundService")) {
            Log.w(TAG, "[VoiceConnectionService] Not creating foregroundService because not configured");
            return;
        }
        ConstraintsMap foregroundSettings = _settings.getMap("foregroundService");
        String notificationContent = foregroundSettings.getString("notificationContent");

        NotificationChannel chan = new NotificationChannel(ONGOING_CHANNEL_ID, getApplicationInfo().name, NotificationManager.IMPORTANCE_NONE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);
        Intent intent = new Intent(getApplicationContext(), (outgoing ? OutgoingCallActivity.class : IncomingCallActivity.class));
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, ONGOING_CHANNEL_ID);
        notificationBuilder.setOngoing(true)
                .setContentTitle(endPoint)
                .setContentText(notificationContent)
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE).setContentIntent(pi);

        int resIcon = getApplicationInfo().icon;
        notificationBuilder.setSmallIcon(resIcon);

        Log.d(TAG, "[VoiceConnectionService] Starting foreground service");

        Notification notification = notificationBuilder.build();
        startForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void stopForegroundService(boolean force) {
        Log.d(TAG, "[VoiceConnectionService] stopForegroundService");
        if (_settings == null || !_settings.hasKey("foregroundService")) {
            Log.d(TAG, "[VoiceConnectionService] Discarding stop foreground service, no service configured");
            return;
        }
        if( force ) {
            stopForeground(true);
        } else {
            stopForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE);
        }
    }

    private void wakeUpApplication(String uuid, String number, String displayName) {
        Intent headlessIntent = new Intent(
            this.getApplicationContext(),
            CallKeepBackgroundMessagingService.class
        );
        headlessIntent.putExtra("callUUID", uuid);
        headlessIntent.putExtra("name", displayName);
        headlessIntent.putExtra("handle", number);
        Log.d(TAG, "wakeUpApplication: " + uuid + ", number : " + number + ", displayName:" + displayName);

        ComponentName name = this.getApplicationContext().startService(headlessIntent);
        if (name != null) {
            CallKeepBackgroundMessagingService.acquireWakeLockNow(this.getApplicationContext());
        }
    }

    private void wakeUpAfterReachabilityTimeout(ConnectionRequest request) {
        if (this.currentConnectionRequest == null) {
            return;
        }
        Log.d(TAG, "checkReachability timeout, force wakeup");
        Bundle extras = request.getExtras();
        String number = request.getAddress().getSchemeSpecificPart();
        String displayName = extras.getString(EXTRA_CALLER_NAME);
        wakeUpApplication(this.notReachableCallUuid, number, displayName);

        VoiceConnectionService.currentConnectionRequest = null;
    }

    private void checkReachability() {
        Log.d(TAG, "checkReachability");

        final VoiceConnectionService instance = this;
        sendCallRequestToActivity(ACTION_CHECK_REACHABILITY, null);

        new android.os.Handler().postDelayed(
            new Runnable() {
                public void run() {
                    instance.wakeUpAfterReachabilityTimeout(instance.currentConnectionRequest);
                }
            }, 2000);
    }

    private Boolean canMakeOutgoingCall() {
        return isAvailable;
    }

    private Connection createConnection(ConnectionRequest request, boolean isIncomingCall) {
        Bundle extras = request.getExtras();
        HashMap<String, String> extrasMap = this.bundleToMap(extras);
        extrasMap.put(EXTRA_CALL_NUMBER, request.getAddress().toString());
        VoiceConnection connection = new VoiceConnection(this, extrasMap, isIncomingCall);
//        connection.setConnectionCapabilities(Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = getApplicationContext();
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(context.TELECOM_SERVICE);
            PhoneAccount phoneAccount = telecomManager.getPhoneAccount(request.getAccountHandle());

            //If the phone account is self managed, then this connection must also be self managed.
            if((phoneAccount.getCapabilities() & PhoneAccount.CAPABILITY_SELF_MANAGED) == PhoneAccount.CAPABILITY_SELF_MANAGED) {
                Log.d(TAG, "[VoiceConnectionService] PhoneAccount is SELF_MANAGED, so connection will be too");
                connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            }
            else {
                Log.d(TAG, "[VoiceConnectionService] PhoneAccount is not SELF_MANAGED, so connection won't be either");
            }
        }

        connection.setInitializing();
        connection.setExtras(extras);
        currentConnections.put(extras.getString(EXTRA_CALL_UUID), connection);

        // Get other connections for conferencing
        Map<String, VoiceConnection> otherConnections = new HashMap<>();
        for (Map.Entry<String, VoiceConnection> entry : currentConnections.entrySet()) {
            if(!(extras.getString(EXTRA_CALL_UUID).equals(entry.getKey()))) {
                otherConnections.put(entry.getKey(), entry.getValue());
            }
        }
        List<Connection> conferenceConnections = new ArrayList<Connection>(otherConnections.values());
        connection.setConferenceableConnections(conferenceConnections);

        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        super.onConference(connection1, connection2);
        VoiceConnection voiceConnection1 = (VoiceConnection) connection1;
        VoiceConnection voiceConnection2 = (VoiceConnection) connection2;

        VoiceConference voiceConference = new VoiceConference(phoneAccountHandle);
        voiceConference.addConnection(voiceConnection1);
        voiceConference.addConnection(voiceConnection2);

        connection1.onUnhold();
        connection2.onUnhold();

        this.addConference(voiceConference);
    }

    /*
     * Send call request to the RNCallKeepModule
     */
    private void sendCallRequestToActivity(final String action, @Nullable final HashMap attributeMap) {
        final VoiceConnectionService instance = this;
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
                LocalBroadcastManager.getInstance(instance).sendBroadcast(intent);
            }
        });
    }

    private HashMap<String, String> bundleToMap(Bundle extras) {
        HashMap<String, String> extrasMap = new HashMap<>();
        Set<String> keySet = extras.keySet();
        Iterator<String> iterator = keySet.iterator();

        while(iterator.hasNext()) {
            String key = iterator.next();
            if (extras.get(key) != null) {
                extrasMap.put(key, extras.get(key).toString());
            }
        }
        return extrasMap;
    }

    /**
     * https://stackoverflow.com/questions/5446565/android-how-do-i-check-if-activity-is-running
     *
     * @param context Context
     * @return boolean
     */
    public static boolean isRunning(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> tasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (RunningTaskInfo task : tasks) {
            if (context.getPackageName().equalsIgnoreCase(task.baseActivity.getPackageName()))
                return true;
        }

        return false;
    }
}
