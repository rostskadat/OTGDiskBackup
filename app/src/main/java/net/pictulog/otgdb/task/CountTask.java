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

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import de.waldheinz.fs.FsDirectory;
import de.waldheinz.fs.FsDirectoryEntry;

/**
 * This class is simply in charge of counting the number of files on the OTG
 * disk from the baseRoot. It is used to create the progress bar with the
 * correct information.
 *
 * @author rostskadat
 */
public class CountTask extends AbstractTask<Void, Void, Void> {

    private final CountTaskListener listener;
    private final FsDirectory srcDir;
    private final List<String> extensions;
    private List<String> files = new ArrayList<String>();
    private Stack<String> pathFragments = new Stack<String>();

    public CountTask(CountTaskListener listener, FsDirectory srcDir, List<String> extensions) {
        this.listener = listener;
        this.srcDir = srcDir;
        this.extensions = extensions;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            Log.d("CountTask", "Counting files...");
            countFiles();
        } catch (Exception e) {
            // Shouldn't be raised...
            Log.e("CountTask", e.getMessage(), e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        super.onPostExecute(result);
        listener.onCountReady(files);
    }

    private void countFiles() {
        try {
            walkFileTree(srcDir, new File(""));
        } catch (Exception e) {
            // Shouldn't be raised...
            Log.e("CountTask", e.getMessage(), e);
        }
    }

    @Override
    protected void visitFile(FsDirectoryEntry file, File targetDirectory) throws IOException {
        String entryName = file.getName();
        String extensionUpper = FilenameUtils.getExtension(entryName).toUpperCase();
        String extensionLower = FilenameUtils.getExtension(entryName).toLowerCase();
        if (!extensions.isEmpty() && !extensions.contains(extensionUpper) && !extensions.contains(extensionLower)) {
            return;
        }
        StringBuilder path = new StringBuilder("/");
        for (String pathFragment : pathFragments) {
            path.append(pathFragment).append("/");
        }
        path.append(file.getName());
        files.add(path.toString());
    }

    @Override
    protected void preVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
        // NA
        pathFragments.push(directory.getName());
    }

    @Override
    protected void postVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException {
        // NA
        pathFragments.pop();
    }

}
