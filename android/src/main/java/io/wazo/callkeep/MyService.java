package io.wazo.callkeep;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class MyService extends Service {
    private static String TAG = "[Flutter] RNCK:MyService";
    private int callId;
    private boolean isDestroyed = false;

    public MyService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand ++");
        Bundle extra = intent.getExtras();
        callId = (int) extra.get("callId");
        Log.i(TAG, "onStartCommand + " + callId);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind ++");
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved ++ "+ callId);
        VoiceConnectionService.deinitConnectionByCallId(callId);
        isDestroyed = true;

        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy ++");
        if( !isDestroyed) VoiceConnectionService.deinitConnectionByCallId(callId);
        super.onDestroy();
    }
}