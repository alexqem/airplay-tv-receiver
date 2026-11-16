package com.airplay.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Starts the receiver after a reboot so AirPlay and DLNA advertising survive power cycles -
 * the whole point of the persistent service is that nobody has to open the app first.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (AirPlayService.userStopped) {
            Log.i(TAG, "Receiver was stopped by hand before shutdown, staying down");
            return;
        }
        Log.i(TAG, "Boot completed, starting the receiver");
        AirPlayService.start(context);
    }
}
