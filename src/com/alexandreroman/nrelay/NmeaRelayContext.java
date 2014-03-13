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

import android.location.Location;

class NmeaRelayContext {
    public static enum State {
        STARTING, WAITING_FOR_GPS_FIX, GPS_DISABLED, NETWORK_UNAVAILABLE, SERVER_UNREACHABLE, RELAYING_NMEA, STOPPED
    }

    public Location location;
    public int satellitesInView;
    public int satellitesInUse;
    public State state;

    public NmeaRelayContext() {
        reset();
    }

    public void reset() {
        location = null;
        satellitesInUse = satellitesInView = 0;
        state = State.STOPPED;
    }
}
