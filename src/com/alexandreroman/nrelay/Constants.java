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

/**
 * Application constants.
 * 
 * @author Alexandre Roman <alexandre.roman@gmail.com>
 */
final class Constants {
    /**
     * Log tag name.
     */
    public static final String TAG = "NRelay";
    /**
     * Shared preferences file name.
     */
    public static final String PREF_FILE = "nrelay";
    /**
     * Preference key: where to send NMEA sentences?
     */
    public static final String SP_HOST_ADDRESS = "hostAddress";
    /**
     * Preference key: UDP port.
     */
    public static final String SP_PORT = "port";
    /**
     * Preference key: is network ready to broadcast NMEA?
     */
    public static final String SP_NETWORK_READY = "networkReady";

    private Constants() {
    }
}
