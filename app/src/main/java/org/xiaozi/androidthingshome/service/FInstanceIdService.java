package org.xiaozi.androidthingshome.service;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Created by xiaoz on 2017-10-07.
 */

public class FInstanceIdService extends FirebaseInstanceIdService {
    private final String LOG_TAG = getClass().getSimpleName();

    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        Log.i(LOG_TAG, "onTokenRefresh");
    }
}
