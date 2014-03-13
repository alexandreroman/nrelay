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
import static com.alexandreroman.nrelay.Constants.SP_HOST_ADDRESS;
import static com.alexandreroman.nrelay.Constants.TAG;

import java.io.IOException;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Fragment displaying information about NMEA relay.
 * 
 * @author Alexandre Roman <alexandre.roman@gmail.com>
 */
public class InfoFragment extends Fragment implements NmeaRelayListener {
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            nmeaRelayService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final NmeaRelayService.Binder binder = (NmeaRelayService.Binder) service;
            nmeaRelayService = binder.getService();
            nmeaRelayService.addListener(InfoFragment.this);
            update(nmeaRelayService.getNmeaRelayContext());
        }
    };
    private static final SparseIntArray STATE_STRINGS = new SparseIntArray(7);
    private static final SparseIntArray STATE_ICONS = new SparseIntArray(7);
    static {
        STATE_STRINGS.put(NmeaRelayContext.State.GPS_DISABLED.ordinal(),
                R.string.state_gps_disabled);
        STATE_STRINGS.put(NmeaRelayContext.State.NETWORK_UNAVAILABLE.ordinal(),
                R.string.state_network_unavailable);
        STATE_STRINGS.put(NmeaRelayContext.State.RELAYING_NMEA.ordinal(), R.string.state_relaying);
        STATE_STRINGS.put(NmeaRelayContext.State.SERVER_UNREACHABLE.ordinal(),
                R.string.state_server_unreachable);
        STATE_STRINGS.put(NmeaRelayContext.State.STARTING.ordinal(), R.string.state_starting);
        STATE_STRINGS.put(NmeaRelayContext.State.STOPPED.ordinal(), R.string.state_stopped);
        STATE_STRINGS.put(NmeaRelayContext.State.WAITING_FOR_GPS_FIX.ordinal(),
                R.string.state_waiting_for_gps_fix);

        STATE_ICONS.put(NmeaRelayContext.State.GPS_DISABLED.ordinal(), R.drawable.state_red);
        STATE_ICONS.put(NmeaRelayContext.State.NETWORK_UNAVAILABLE.ordinal(), R.drawable.state_red);
        STATE_ICONS.put(NmeaRelayContext.State.RELAYING_NMEA.ordinal(), R.drawable.state_green);
        STATE_ICONS.put(NmeaRelayContext.State.SERVER_UNREACHABLE.ordinal(), R.drawable.state_red);
        STATE_ICONS.put(NmeaRelayContext.State.STARTING.ordinal(), R.drawable.state_orange);
        STATE_ICONS.put(NmeaRelayContext.State.STOPPED.ordinal(), R.drawable.state_red);
        STATE_ICONS.put(NmeaRelayContext.State.WAITING_FOR_GPS_FIX.ordinal(),
                R.drawable.state_orange);
    }
    private NmeaRelayService nmeaRelayService;
    private TextView stateTV;
    private TextView satellitesTV;
    private TextView accuracyTV;
    private MenuItem startStopAction;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_info, container);
        stateTV = (TextView) v.findViewById(R.id.info_state);
        satellitesTV = (TextView) v.findViewById(R.id.info_satellites);
        accuracyTV = (TextView) v.findViewById(R.id.info_accuracy);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stateTV = satellitesTV = accuracyTV = null;
        startStopAction = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().bindService(new Intent(getActivity(), NmeaRelayService.class), serviceConn,
                Context.BIND_IMPORTANT | Context.BIND_AUTO_CREATE);
        setHasOptionsMenu(true);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        startStopAction = menu.findItem(R.id.start_stop_action);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_info, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.start_stop_action:
            onMenuStartStopAction();
            break;
        case R.id.settings_action:
            startActivity(new Intent(getActivity(), PreferencesActivity.class));
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void onMenuStartStopAction() {
        if (nmeaRelayService != null) {
            if (nmeaRelayService.getNmeaRelayContext().state == NmeaRelayContext.State.STOPPED) {
                final SharedPreferences prefs = getActivity().getSharedPreferences(PREF_FILE,
                        Context.MODE_PRIVATE);
                if (prefs.getString(SP_HOST_ADDRESS, null) == null) {
                    final Fragment f = ErrorDialog.newInstance(R.string.dialog_error,
                            R.string.error_no_host_address_set);
                    getFragmentManager().beginTransaction().add(f, "error").commit();
                    return;
                }

                try {
                    getActivity().startService(new Intent(getActivity(), NmeaRelayService.class));
                    nmeaRelayService.startNmeaRelay();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to start NMEA relay", e);
                }
            } else {
                nmeaRelayService.stopNmeaRelay();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (nmeaRelayService != null) {
            nmeaRelayService.addListener(this);
            update(nmeaRelayService.getNmeaRelayContext());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (nmeaRelayService != null) {
            nmeaRelayService.removeListener(this);
        }
        getActivity().unbindService(serviceConn);
    }

    @Override
    public void onNmeaRelayContextChanged(NmeaRelayContext context) {
        update(context);
    }

    private void update(NmeaRelayContext context) {
        final int accuracy = context.location == null ? 0 : (int) context.location.getAccuracy();
        final String accuracyStr = String.format(getString(R.string.accuracy), accuracy);
        accuracyTV.setText(accuracyStr);

        final String satellitesStr = String.format(getString(R.string.seen_satellites),
                context.satellitesInUse, context.satellitesInView);
        satellitesTV.setText(satellitesStr);

        stateTV.setText(STATE_STRINGS.get(context.state.ordinal()));

        final int stateDrawable;
        if (context.state == NmeaRelayContext.State.RELAYING_NMEA && context.location == null) {
            stateDrawable = R.drawable.state_orange;
        } else {
            stateDrawable = STATE_ICONS.get(context.state.ordinal());
        }
        stateTV.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(stateDrawable),
                null, null, null);

        if (startStopAction != null) {
            if (context.state == NmeaRelayContext.State.STOPPED) {
                startStopAction.setTitle(R.string.menu_start);
                startStopAction.setIcon(R.drawable.ic_action_play);
            } else {
                startStopAction.setTitle(R.string.menu_stop);
                startStopAction.setIcon(R.drawable.ic_action_stop);
            }
        }
    }
}
