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

import java.util.HashSet;
import java.util.Set;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.alexandreroman.nrelay.NmeaRelayContext.State;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Main application activity.
 * 
 * @author Alexandre Roman <alexandre.roman@gmail.com>
 */
public class MainActivity extends Activity implements NmeaRelayListener {
    private final ServiceConnection nmeaRelayServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            nmeaRelayService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final NmeaRelayService.Binder binder = (NmeaRelayService.Binder) service;
            nmeaRelayService = binder.getService();
            nmeaRelayService.addListener(MainActivity.this);
        }
    };
    private static final Set<NmeaRelayContext.State> STATE_LOCATION_DISPLAYED = new HashSet<NmeaRelayContext.State>(
            3);
    static {
        STATE_LOCATION_DISPLAYED.add(State.NETWORK_UNAVAILABLE);
        STATE_LOCATION_DISPLAYED.add(State.SERVER_UNREACHABLE);
        STATE_LOCATION_DISPLAYED.add(State.RELAYING_NMEA);
    }
    private NmeaRelayService nmeaRelayService;
    private GoogleMap googleMap;
    private Marker myPositionMarker;
    private Circle myPositionCircle;
    private boolean firstCamUpdate = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set default preference values.
        PreferenceManager.setDefaultValues(this, PREF_FILE, MODE_PRIVATE, R.xml.preferences, false);

        // Bind this activity to the background service.
        bindService(new Intent(this, NmeaRelayService.class), nmeaRelayServiceConn,
                Context.BIND_IMPORTANT | Context.BIND_AUTO_CREATE);

        // Initialize UI.
        setContentView(R.layout.activity_main);

        final MapFragment mf = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        googleMap = mf.getMap();
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.getUiSettings().setRotateGesturesEnabled(false);
        googleMap.getUiSettings().setTiltGesturesEnabled(false);

        if (savedInstanceState == null) {
            // When the application starts for the first time, device
            // connectivity may not be known.
            // Let's update connectivity status right now.
            ConnectivityListener.checkIfNetworkIsReady(this);

            // Center map view on current device location.
            final LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (loc == null) {
                // Current device location is unknown: let's use a default one.
                loc = new Location(LocationManager.PASSIVE_PROVIDER);
                loc.setLatitude(48.82333);
                loc.setLongitude(2.33667);
            }
            centerOnLocation(loc, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(nmeaRelayServiceConn);
        googleMap = null;
        myPositionMarker = null;
        myPositionCircle = null;
    }

    @Override
    public void onNmeaRelayContextChanged(NmeaRelayContext context) {
        centerOnLocation(context.location, STATE_LOCATION_DISPLAYED.contains(context.state));
    }

    private void centerOnLocation(Location loc, boolean locationDisplayed) {
        if (loc != null) {
            // Center map view on this location.
            final LatLng center = new LatLng(loc.getLatitude(), loc.getLongitude());
            final CameraUpdate camUpdate;
            if (firstCamUpdate) {
                camUpdate = CameraUpdateFactory.newLatLngZoom(center, 16);
                firstCamUpdate = false;
            } else {
                camUpdate = CameraUpdateFactory.newLatLng(center);
            }
            googleMap.animateCamera(camUpdate);

            if (myPositionMarker == null) {
                // Lazily create markers.
                final int blueCircleFillColor = getResources().getColor(
                        R.color.blue_circle_fill_color);
                final int blueCircleStrokeColor = getResources().getColor(
                        R.color.blue_circle_stroke_color);
                myPositionMarker = googleMap.addMarker(new MarkerOptions().position(center));
                myPositionCircle = googleMap.addCircle(new CircleOptions().center(center)
                        .fillColor(blueCircleFillColor).strokeColor(blueCircleStrokeColor)
                        .strokeWidth(2).radius(loc.getAccuracy()));
            } else {
                myPositionMarker.setPosition(center);
                myPositionCircle.setCenter(center);
                myPositionCircle.setRadius(loc.getAccuracy());
            }
            myPositionCircle.setVisible(locationDisplayed);
            myPositionMarker.setVisible(locationDisplayed);
        }
    }
}
