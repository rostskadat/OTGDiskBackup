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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import net.pictulog.otgdb.task.BackupTask;
import net.pictulog.otgdb.task.BackupTaskListener;
import net.pictulog.otgdb.task.CountTask;
import net.pictulog.otgdb.task.CountTaskListener;
import net.pictulog.otgdb.task.MountTask;
import net.pictulog.otgdb.task.MountTaskListener;
import net.pictulog.otgdb.task.NavigateTask;
import net.pictulog.otgdb.task.NavigateTaskListener;

import java.io.File;
import java.util.List;

import de.waldheinz.fs.FileSystem;
import de.waldheinz.fs.FsDirectory;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment implements MountTaskListener, NavigateTaskListener, CountTaskListener, BackupTaskListener {

    private Button btnBackup;
    private ProgressDialog progressDialog;
    private FsDirectory fromDir;
    private int fileCount = -1;


    public MainActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        btnBackup = (Button) view.findViewById(R.id.action_backup);
        btnBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backupFiles();
            }
        });
        btnBackup.setEnabled(false);
        new MountTask(this, getContext()).execute();
        return view;
    }

    private void backupFiles() {

        Context context = getContext();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        if (!settings.contains(PreferencesActivity.PREFS_FROM_FILE)
                || !settings.contains(PreferencesActivity.PREFS_TO_FILE)) {
            Log.e("MainActivityFragment", "Missing from/to preferences...");
            Toast.makeText(context, "Missing from/to preferences...", Toast.LENGTH_LONG).show();
            btnBackup.setEnabled(false);
            return;
        }

        File to = new File(settings.getString(PreferencesActivity.PREFS_TO_FILE, ""));
        if (!to.exists()) {
            Log.e("MainActivityFragment", "Invalid from/to preferences...");
            Toast.makeText(context, "Invalid from/to preferences...", Toast.LENGTH_LONG).show();
            btnBackup.setEnabled(false);
            return;
        }
        Log.i("MainActivityFragment", "Copying all files from OTG Disk to " + to);
        try {
            FsDirectory srcDir = fromDir;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            File destDir = new File(sharedPreferences.getString(PreferencesActivity.PREFS_TO_FILE,
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()));
            boolean delete = sharedPreferences.getBoolean(PreferencesActivity.PREFS_DELETE, false);
            boolean overwrite = sharedPreferences.getBoolean(PreferencesActivity.PREFS_DELETE, false);
            new BackupTask(this, srcDir, destDir, delete, overwrite).execute();
        } catch (Exception e) {
            progressDialog.dismiss();
            Log.e("MainActivityFragment", e.getMessage(), e);
            Toast.makeText(context, R.string.backingUpFailed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMountReady(FileSystem fs) {
        Log.i("MainActivityFragment", "Disk ready!");
        Context context = getContext();
        Toast.makeText(context, R.string.diskReady, Toast.LENGTH_SHORT).show();
        try {
            new NavigateTask(this, fs.getRoot(),
                    PreferenceManager.getDefaultSharedPreferences(context).getString(PreferencesActivity.PREFS_FROM_FILE, getText(R.string.from_file).toString())).execute();
        } catch (Exception e) {
            Log.e("MainActivityFragment", e.getMessage(), e);
            Toast.makeText(context, R.string.mountingFailed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onMountFailed(int messageId) {
        Toast.makeText(getContext(), messageId, Toast.LENGTH_LONG).show();
    }

    public void onNavigateReady(FsDirectory fromDir) {
        this.fromDir = fromDir;
        btnBackup.setEnabled(true);
        try {
            new CountTask(this, fromDir).execute();
        } catch (Exception e) {
            Log.e("MainActivityFragment", e.getMessage(), e);
            Toast.makeText(getContext(), R.string.mountingFailed, Toast.LENGTH_LONG).show();
        }
    }

    public void onCountReady(int count) {
        this.fileCount = count;
        if (count <= 0) {
            btnBackup.setEnabled(false);
        }
    }

    /*
         * BackupTaskListener methods
         *
         *
         */
    @Override
    public void onBackupStart() {
        btnBackup.setEnabled(false);
        Log.d("MainActivityFragment", "Backing up " + fileCount + " files...");
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setCancelable(false);
        progressDialog.setMessage(getText(R.string.backingUp));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgress(0);
        progressDialog.setMax(fileCount);
        progressDialog.show();
    }

    @Override
    public void onBackupReady() {
        try {
            Log.i("MainActivityFragment", "Backup complete");
            btnBackup.setEnabled(true);
            new CountTask(this, fromDir).execute();
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            Log.e("MainActivityFragment", e.getMessage(), e);
        }
    }

    @Override
    public void onBackupProgressUpdate(Integer... values) {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.setProgress(values != null ? values[values.length - 1] : 0);
            }
        } catch (Exception e) {
            Log.e("MainActivityFragment", e.getMessage(), e);
        }
    }

    @Override
    public void onBackupFailed(List<String> filenames) {
        try {
            Log.e("MainActivityFragment", "Failed to download file(s): " + filenames);
            btnBackup.setEnabled(true);
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            Toast.makeText(getContext(), R.string.backingUpFailed, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e("MainActivityFragment", e.getMessage(), e);
        }
    }

    private Context getContext() {
        return getActivity();
    }
}
