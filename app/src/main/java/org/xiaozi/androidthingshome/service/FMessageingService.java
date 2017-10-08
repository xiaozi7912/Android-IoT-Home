package org.xiaozi.androidthingshome.service;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by xiaoz on 2017-10-07.
 */

public class FMessageingService extends FirebaseMessagingService {
    private final String LOG_TAG = getClass().getSimpleName();

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Log.i(LOG_TAG, "onMessageReceived");
        Log.d(LOG_TAG, "onMessageReceived message : " + message);
    }

    @Override
    public void onMessageSent(String s) {
        super.onMessageSent(s);
        Log.i(LOG_TAG, "onMessageSent");
        Log.d(LOG_TAG, "onMessageReceived s : " + s);
    }
}
