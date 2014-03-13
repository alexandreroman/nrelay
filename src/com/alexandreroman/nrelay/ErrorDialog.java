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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * Error dialog fragment.
 * 
 * @author Alexandre Roman <alexandre.roman@gmail.com>
 */
public class ErrorDialog extends DialogFragment {
    public static ErrorDialog newInstance(int title, int message) {
        final Bundle args = new Bundle(2);
        args.putInt("title", title);
        args.putInt("message", message);
        final ErrorDialog e = new ErrorDialog();
        e.setArguments(args);
        return e;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        final int title = args.getInt("title");
        final int message = args.getInt("message");
        return new AlertDialog.Builder(getActivity()).setTitle(title).setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, null).create();
    }
}
