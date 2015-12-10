/**
 * This file is part of OTGDiskBackup.
 * <p/>
 * Copyright 2005-2009 Red Hat, Inc.  All rights reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.pictulog.otgdb;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.nononsenseapps.filepicker.FilePickerActivity;

/**
 * This class uses the code at https://android-arsenal.com/details/1/160
 */
public class PreferencesActivityFragment extends PreferenceFragment {

    private static final int REQUEST_CODE_FROM = 0;
    private static final int REQUEST_CODE_TO = 1;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // Setup the file chooser for the To field.
        Preference filePicker = findPreference(PreferencesActivity.PREFS_TO_FILE);
        filePicker.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
                intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);
                intent.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath());
                startActivityForResult(intent, REQUEST_CODE_TO);
                return true;
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i("PreferencesActivity", "retrieve file chooser result");
        if (requestCode == REQUEST_CODE_TO && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            Log.d("PreferencesActivity", uri.toString());
        }
    }


}
