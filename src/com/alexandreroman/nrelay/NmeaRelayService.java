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
import static com.alexandreroman.nrelay.Constants.SP_NETWORK_READY;
import static com.alexandreroman.nrelay.Constants.SP_PORT;
import static com.alexandreroman.nrelay.Constants.TAG;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseIntArray;

import com.alexandreroman.nrelay.NmeaRelayContext.State;

public class NmeaRelayService extends Service implements GpsStatus.NmeaListener,
        GpsStatus.Listener, LocationListener {
    private static final SparseIntArray STR_STATES = new SparseIntArray(4);
    static {
        STR_STATES.put(State.STARTING.ordinal(), R.string.notif_starting);
        STR_STATES.put(State.GPS_DISABLED.ordinal(), R.string.notif_gps_disabled);
        STR_STATES.put(State.RELAYING_NMEA.ordinal(), R.string.notif_relaying);
        STR_STATES.put(State.WAITING_FOR_GPS_FIX.ordinal(), R.string.notif_waiting_for_gps_fix);
        STR_STATES.put(State.NETWORK_UNAVAILABLE.ordinal(), R.string.notif_network_unavailable);
        STR_STATES.put(State.SERVER_UNREACHABLE.ordinal(), R.string.notif_server_unreachable);
        STR_STATES.put(State.STOPPED.ordinal(), R.string.notif_stopped);
    }

    public static final class Binder extends android.os.Binder {
        private final NmeaRelayService service;

        private Binder(final NmeaRelayService service) {
            this.service = service;
        }

        public NmeaRelayService getService() {
            return service;
        }
    }

    private Handler uiHandler;
    private final Binder binder = new Binder(this);
    private final NumberFormat locationFormat = NumberFormat.getInstance(Locale.ENGLISH);
    private final NmeaRelayContext context = new NmeaRelayContext();
    private BlockingQueue<String> nmeaQueue = new ArrayBlockingQueue<String>(16);
    private Set<WeakReference<NmeaRelayListener>> listenerRefs = new HashSet<WeakReference<NmeaRelayListener>>(
            2);
    private boolean relaying;
    private PowerManager.WakeLock pLock;
    private SocketChannel sock;
    private final ByteBuffer buffer = ByteBuffer.allocate(512);
    private final CharsetEncoder encoder = Charset.forName("US-ASCII").newEncoder();
    private PendingIntent openMainActivityIntent;
    private Thread nmeaWorker;

    private SharedPreferences prefs;
    private LocationManager locationManager;
    private PowerManager powerManager;

    public NmeaRelayService() {
        locationFormat.setMaximumFractionDigits(5);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        uiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                for (final WeakReference<NmeaRelayListener> listenerRef : listenerRefs) {
                    final NmeaRelayListener listener = listenerRef.get();
                    if (listener != null) {
                        try {
                            listener.onNmeaRelayContextChanged(context);
                        } catch (Exception e) {
                            Log.w(TAG, "Error in NMEA relay listener: " + listener, e);
                        }
                    }
                }
            }
        };

        openMainActivityIntent = PendingIntent.getActivity(this, 0, new Intent(this,
                MainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopNmeaRelay();
        uiHandler = null;
        openMainActivityIntent = null;
        locationManager = null;
        powerManager = null;
        prefs = null;
        nmeaQueue.clear();
    }

    @Override
    public Binder onBind(Intent intent) {
        return binder;
    }

    private void updateState(State newState) {
        if (newState == null) {
            throw new IllegalArgumentException("State cannot be null");
        }
        if (newState != context.state) {
            Log.i(TAG, "State updated: " + newState);
            final int res = STR_STATES.get(newState.ordinal());
            startForeground(R.string.stat_notify_nmea_relay, createNotification(res));
            context.state = newState;
            fireNmeaRelayContextChanged();
        }
    }

    public NmeaRelayContext getNmeaRelayContext() {
        return context;
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        nmeaQueue.offer(nmea);
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (GpsStatus.GPS_EVENT_STARTED == event) {
            Log.i(TAG, "GPS started");
        } else if (GpsStatus.GPS_EVENT_STOPPED == event) {
            Log.i(TAG, "GPS stopped");
        } else if (GpsStatus.GPS_EVENT_FIRST_FIX == event) {
            Log.i(TAG, "GPS first fix");
        } else if (GpsStatus.GPS_EVENT_SATELLITE_STATUS == event) {
            if (locationManager != null) {
                final GpsStatus s = locationManager.getGpsStatus(null);
                context.satellitesInUse = 0;
                context.satellitesInView = 0;
                for (final GpsSatellite sat : s.getSatellites()) {
                    if (sat.usedInFix()) {
                        context.satellitesInUse += 1;
                    }
                    context.satellitesInView += 1;
                }
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "GPS satellite status: " + context.satellitesInUse
                            + " satellite(s) used in fix");
                }
                fireNmeaRelayContextChanged();
            }
        }
    }

    @Override
    public void onLocationChanged(Location loc) {
        Log.d(TAG,
                "Got new location: [" + locationFormat.format(loc.getLatitude()) + "°, "
                        + locationFormat.format(loc.getLongitude()) + "°, "
                        + locationFormat.format(loc.getAccuracy()) + " m]");
        context.location = loc;
        fireNmeaRelayContextChanged();
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            Log.i(TAG, "GPS is disabled by user");
            updateState(State.GPS_DISABLED);
        }
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            Log.i(TAG, "GPS is enabled by user");
            updateState(State.WAITING_FOR_GPS_FIX);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) {
            if (LocationProvider.TEMPORARILY_UNAVAILABLE == status) {
                Log.i(TAG, "GPS is temporarily unavailable");
                updateState(State.WAITING_FOR_GPS_FIX);
            }
        }
    }

    private Notification createNotification(int contentText) {
        final NotificationCompat.Builder nb = new NotificationCompat.Builder(this)
                .setTicker(getString(R.string.stat_notify_nmea_relay))
                .setSmallIcon(R.drawable.ic_stat_notify_nmea_relay)
                .setContentTitle(getString(R.string.stat_notify_nmea_relay))
                .setContentText(getString(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openMainActivityIntent);
        if (R.string.notif_relaying == contentText) {
            nb.setWhen(System.currentTimeMillis());
            nb.setUsesChronometer(true);
        }
        return nb.build();
    }

    public void startNmeaRelay() throws IOException {
        if (relaying) {
            Log.d(TAG, "Already relaying: do nothing");
            return;
        }

        context.reset();

        pLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        pLock.acquire();
        updateState(State.STARTING);

        nmeaWorker = new NmeaRelayWorker();
        nmeaWorker.start();

        Log.d(TAG, "Requesting location updates through GPS");
        locationManager.addNmeaListener(this);
        locationManager.addGpsStatusListener(this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 0, this);

        Log.i(TAG, "NMEA relay started");
        relaying = true;
        fireNmeaRelayContextChanged();
    }

    public void stopNmeaRelay() {
        if (!relaying) {
            Log.d(TAG, "Relaying is not active");
        }
        locationManager.removeNmeaListener(this);
        locationManager.removeGpsStatusListener(this);
        locationManager.removeUpdates(this);
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ignore) {
            }
            sock = null;
        }
        if (nmeaWorker != null) {
            nmeaWorker.interrupt();
            nmeaWorker = null;
        }
        relaying = false;
        stopForeground(true);
        context.reset();
        fireNmeaRelayContextChanged();
        if (pLock != null) {
            pLock.release();
            pLock = null;
        }
        Log.i(TAG, "NMEA relay stopped");
        stopSelf();
    }

    public void addListener(NmeaRelayListener listener) {
        clearListeners();
        if (listener != null) {
            listenerRefs.add(new WeakReference<NmeaRelayListener>(listener));
        }
    }

    public void removeListener(NmeaRelayListener listener) {
        clearListeners();
        if (listener != null) {
            for (final Iterator<WeakReference<NmeaRelayListener>> i = listenerRefs.iterator(); i
                    .hasNext();) {
                final WeakReference<NmeaRelayListener> ref = i.next();
                if (listener.equals(ref.get())) {
                    i.remove();
                }
            }
        }
    }

    private void clearListeners() {
        for (final Iterator<WeakReference<NmeaRelayListener>> i = listenerRefs.iterator(); i
                .hasNext();) {
            final WeakReference<NmeaRelayListener> ref = i.next();
            if (ref.get() == null) {
                i.remove();
            }
        }
    }

    private void fireNmeaRelayContextChanged() {
        uiHandler.sendEmptyMessage(0);
    }

    private void sendNmeaOnLocalNetwork(String nmea) throws IOException {
        if (!prefs.getBoolean(SP_NETWORK_READY, false)) {
            Log.d(TAG, "Network is not ready: cannot relay NMEA");
            updateState(State.NETWORK_UNAVAILABLE);
            return;
        }

        if (sock == null) {
            Log.d(TAG, "Initializing client socket");
            final String hostAddress = prefs.getString(SP_HOST_ADDRESS, "192.168.1.93");
            if (hostAddress == null) {
                throw new IOException("No host address set");
            }
            final int port = Integer.parseInt(prefs.getString(SP_PORT, "0"));
            final InetSocketAddress serverAddr = new InetSocketAddress(hostAddress, port);
            sock = SocketChannel.open();
            sock.configureBlocking(true);
            sock.socket().setSoTimeout(4000);

            Log.d(TAG, "Connecting to server: " + hostAddress + ":" + port);
            try {
                sock.connect(serverAddr);
            } catch (ConnectException e) {
                Log.w(TAG, "Failed to connect to server");
                updateState(State.SERVER_UNREACHABLE);
                sock = null;
                return;
            }
        }

        buffer.clear();
        encoder.encode(CharBuffer.wrap(nmea), buffer, true);
        buffer.flip();

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Sending NMEA on local network: " + nmea + " (" + buffer.remaining()
                    + " bytes)");
        }
        while (buffer.hasRemaining()) {
            try {
                sock.write(buffer);
            } catch (IOException e) {
                try {
                    sock.close();
                } catch (IOException ignore) {
                }
                updateState(State.SERVER_UNREACHABLE);
                sock = null;
                throw e;
            }
        }
        updateState(State.RELAYING_NMEA);
    }

    private class NmeaRelayWorker extends Thread {
        public NmeaRelayWorker() {
            super("NRelay/Worker");
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }

        @Override
        public void run() {
            boolean running = true;
            Log.d(TAG, "NMEA worker is started");
            while (running) {
                try {
                    final String nmea = nmeaQueue.take();
                    sendNmeaOnLocalNetwork(nmea);
                } catch (InterruptedException e) {
                    running = false;
                } catch (InterruptedIOException e) {
                    running = false;
                } catch (IOException e) {
                    Log.w(TAG, "Failed to send NMEA on local network", e);
                }
            }
            Log.d(TAG, "NMEA worker is stopped");
        }
    }
}
