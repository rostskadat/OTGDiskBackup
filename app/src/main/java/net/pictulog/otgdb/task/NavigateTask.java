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
import java.io.IOException;

import de.waldheinz.fs.FsDirectory;
import de.waldheinz.fs.FsDirectoryEntry;

/**
 * This class is in charge of navigating the OTG disk in order to lookup the
 * baseRoot
 *
 * @author rostskadat
 */
public class NavigateTask extends AbstractTask<Void, Void, FsDirectory> {

    private final NavigateTaskListener listener;
    private final FsDirectory rootDir;
    private final String targetPath;

    private FsDirectory navigateTo = null;

    public NavigateTask(NavigateTaskListener listener, FsDirectory rootDir, String targetPath) {
        this.listener = listener;
        this.rootDir = rootDir;
        this.targetPath = targetPath;
    }

    @Override
    protected FsDirectory doInBackground(Void... params) {
        try {
            return navigate();
        } catch (Exception e) {
            // Souldn't be raised...
            Log.e("TASKS", e.getMessage(), e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(FsDirectory result) {
        super.onPostExecute(result);
        listener.onNavigateReady(result);
    }

    private FsDirectory navigate() {
        try {
            walkFileTree(rootDir, new File("/"));
        } catch (Exception e) {
            // Souldn't be raised...
            Log.e("TASKS", e.getMessage(), e);
        }
        return navigateTo;
    }

    protected void visitFile(FsDirectoryEntry file, File targetDirectory) throws IOException {
        // NA
    }

    @Override
    protected void preVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
        if (targetDirectory.getPath().equals(targetPath)) {
            navigateTo = directory.getDirectory();
        }
    }

    @Override
    protected void postVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
    }
}
