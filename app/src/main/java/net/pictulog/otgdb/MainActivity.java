/**
 * This file is part of OTGDiskBackup.
 * <p/>
 * Copyright 2015 Julien Masnada. All rights reserved.
 * <p/>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.nononsenseapps.filepicker.FilePickerActivity;

public class MainActivity extends AppCompatActivity {

    public static final boolean USE_USB_MOCK = false;
    public static final String ACTION_USB_PERMISSION = "net.pictulog.otgdb.USB_PERMISSION";
    public static final int FILE_CODE = 12345;
    private static final String PREFERENCES_ACTIVITY = PreferencesActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Handle gracefully the fact that I do not have permission to write to the internal storage
        if (!checkWriteExternalPermission()) {
            setContentView(R.layout.activity_nopermission);
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
        } else {
            setContentView(R.layout.activity_main);
            onFirstLaunch();

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onSettings(MenuItem menuItem) {
        startActivity(new Intent(PREFERENCES_ACTIVITY));
    }

    public void onAbout(MenuItem menuItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.action_about);
        builder
                .setMessage(R.string.action_about)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public void onStartFilePicker(MenuItem menuItem) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);

        // Set these depending on your use case. These are the defaults.
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
        i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
        i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);

        // Configure initial directory by specifying a String.
        // You could specify a String like "/storage/emulated/0/", but that can
        // dangerous. Always use Android's API calls to get paths to the SD-card or
        // internal memory.
        i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

        startActivityForResult(i, FILE_CODE);
    }

    private boolean checkWriteExternalPermission() {
        String permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
        int res = checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }

    /*
         * This method is called once during the first launch. It will try to set
         * the source and destination folders to reasonable defaults
         */
    private void onFirstLaunch() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (!settings.contains(PreferencesActivity.PREFS_FIRST_LAUNCH)) {
            Log.i("INIT", "First launch detected. Initializing destination folder...");
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PreferencesActivity.PREFS_FROM_FILE, getText(R.string.from_file).toString());
            editor.putString(PreferencesActivity.PREFS_TO_FILE,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
            editor.putBoolean(PreferencesActivity.PREFS_FIRST_LAUNCH, false);
            editor.apply();
        } else {
            Log.d("INIT", "This is not the first launch. Keeping preferences...");
        }
    }


}
