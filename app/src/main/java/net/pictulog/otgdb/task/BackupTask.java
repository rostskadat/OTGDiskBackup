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
package net.pictulog.otgdb.task;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import de.waldheinz.fs.FsDirectory;
import de.waldheinz.fs.FsDirectoryEntry;
import de.waldheinz.fs.FsFile;

/**
 * This class will simply walk the whole tree and copy the file from the source
 * to the destination folder.
 *
 * @author rostskadat
 */
public class BackupTask extends AbstractTask<Void, Integer, List<String>> {

    private final FsDirectory srcDir;
    private final File destDir;
    private final boolean overwrite;
    private final boolean delete;
    private final BackupTaskListener listener;

    private Stack<List<String>> fileToDelete = new Stack<List<String>>();
    private List<String> failedToBackup = new ArrayList<String>();
    private int currentFile = 0;

    public BackupTask(BackupTaskListener listener, FsDirectory srcDir, File destDir, boolean delete, boolean overwrite) {
        this.listener = listener;
        this.srcDir = srcDir;
        this.destDir = destDir;
        this.delete = delete;
        this.overwrite = overwrite;
    }

    @Override
    protected List<String> doInBackground(Void... params) {
        List<String> failedToBackup = null;
        try {
            if (!destDir.exists()) {
                Log.e("BackupTask", "Dest dir '" + destDir.getPath() + "' does not exists or is not writtable");
            } else {
                Log.i("BackupTask", "Backup from " + srcDir.toString() + " -> " + destDir.toString());
                failedToBackup = backupFiles();
                Log.i("BackupTask", "Backup complete");
            }
        } catch (Exception e) {
            // Souldn't be raised...
            Log.e("BackupTask", e.getMessage(), e);
        }
        return failedToBackup;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        listener.onBackupStart();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        listener.onBackupProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(List<String> failedToBackup) {
        super.onPostExecute(failedToBackup);
        if (failedToBackup == null || failedToBackup.isEmpty()) {
            listener.onBackupReady();
        } else {
            listener.onBackupFailed(failedToBackup);
        }
    }

    private List<String> backupFiles() {
        try {
            walkFileTree(srcDir, destDir);
            return failedToBackup;
        } catch (Exception e) {
            // Souldn't be raised...
            Log.e("BackupTask", e.getMessage(), e);
        }
        return null;
    }

    @Override
    protected void visitFile(FsDirectoryEntry entry, File targetDirectory) throws IOException {
        if (entry == null || !entry.isFile()) {
            throw new IllegalArgumentException("entry must be an existing file");
        }
        if (targetDirectory == null || !targetDirectory.exists() || !targetDirectory.isDirectory()
                || !targetDirectory.canWrite()) {
            throw new IllegalArgumentException(
                    "targetDirectory '" + targetDirectory + "' must be an existing writable directory");
        }
        if (copyFile(entry.getFile(), new File(targetDirectory, entry.getName()))) {
            if (delete) {
                fileToDelete.peek().add(entry.getName());
            }
        } else {
            failedToBackup.add(entry.getName());
        }
    }

    @Override
    protected void preVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }
        if (delete) {
            fileToDelete.push(new ArrayList<String>());
        }
    }

    @Override
    protected void postVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
        if (delete) {
            List<String> toDeletes = fileToDelete.pop();
            for (String toDelete : toDeletes) {
                directory.getDirectory().remove(toDelete);
            }
        }
    }

    private boolean copyFile(FsFile srcFile, File destFile) {
        publishProgress(currentFile++);
        if (srcFile.isValid()) {
            ByteBuffer buffer = ByteBuffer.allocate((int) srcFile.getLength());
            if (destFile.exists() && !overwrite) {
                return true;
            }
            OutputStream fos = null;
            try {
                fos = new FileOutputStream(destFile);
                srcFile.read(0, buffer);
                fos.write(buffer.array());
                fos.flush();
                return true;
            } catch (IOException e) {
                Log.e("BackupTask", e.getMessage(), e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e("BackupTask", e.getMessage());
                    }
                }
            }
        }
        return false;
    }

}
