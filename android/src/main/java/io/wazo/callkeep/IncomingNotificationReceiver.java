package io.wazo.callkeep;

import static io.wazo.callkeep.VoiceConnection.CALL_NOTIFICATION;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.DisconnectCause;
import android.util.Log;

public class IncomingNotificationReceiver extends BroadcastReceiver  {
    private static final String TAG = "IncomingNotificationReceiver";
    public static final String ACTION_ANSWER_CALL =
            "io.wazo.callkeep.action.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL =
            "io.wazo.callkeep.action.REJECT_CALL";

    public static Intent getAcceptIntent(Context context, int callId) {
        Intent intent = new Intent(
                IncomingNotificationReceiver.ACTION_ANSWER_CALL, null, context,
                IncomingNotificationReceiver.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        return intent;
    }

    public static Intent getDeclineIntent(Context context, int callId) {
        Intent intent = new Intent(
                IncomingNotificationReceiver.ACTION_REJECT_CALL, null, context,
                IncomingNotificationReceiver.class);
        intent.putExtra(Constants.EXTRA_CALL_ID, callId);
        return intent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Log.i(TAG, "onReceive >>> " + action);

        int callId = intent.getIntExtra(Constants.EXTRA_CALL_ID, 0);

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        VoiceConnection connection = (VoiceConnection) VoiceConnectionService.getConnectionById(callId);
        switch (action) {
            case ACTION_ANSWER_CALL:
                if(connection != null) {
                    connection.onAnswer();
                }
//                notificationManager.cancel(CALL_NOTIFICATION, callId);
                break;
            case ACTION_REJECT_CALL:
                if(connection != null) {
                    connection.setConnectionDisconnected(DisconnectCause.REJECTED);
                    connection.destroy();
                }
                notificationManager.cancel(CALL_NOTIFICATION, callId);
                break;

        }

    }
}
