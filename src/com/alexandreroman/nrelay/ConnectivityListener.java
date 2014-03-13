/* 
 * NMEA relay.
 * Copyright (c) 2014- Alexandre Roman, alexandre.roman@gmail.com.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alexandreroman.nrelay;

import static com.alexandreroman.nrelay.Constants.PREF_FILE;
import static com.alexandreroman.nrelay.Constants.SP_NETWORK_READY;
import static com.alexandreroman.nrelay.Constants.TAG;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Listen to connectivity updates.
 * 
 * @author Alexandre Roman <alexandre.roman@gmail.com>
 */
public class ConnectivityListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            return;
        }
        checkIfNetworkIsReady(context);
    }

    public static void checkIfNetworkIsReady(Context context) {
        boolean readyToBroadcast = false;

        final ConnectivityManager connManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo netInfo = connManager.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isAvailable() && netInfo.isConnected()) {
            readyToBroadcast = netInfo.getType() == ConnectivityManager.TYPE_WIFI
                    || netInfo.getType() == ConnectivityManager.TYPE_ETHERNET;
        }

        Log.i(TAG, "Network connectivity changed: " + (readyToBroadcast ? "ready" : "NOT ready")
                + " to broadcast NMEA");
        final SharedPreferences.Editor prefs = context.getSharedPreferences(PREF_FILE,
                Context.MODE_PRIVATE).edit();
        prefs.putBoolean(SP_NETWORK_READY, readyToBroadcast);
        prefs.apply();
    }
}
