package net.pictulog.otgdb.task;

import android.os.AsyncTask;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import de.waldheinz.fs.FsDirectory;
import de.waldheinz.fs.FsDirectoryEntry;

public abstract class AbstractTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {


    protected void walkFileTree(FsDirectory srcDir, File destDir) throws IOException {
        if (srcDir == null) {
            throw new IllegalArgumentException("srcDir can't be null");
        }
        if (destDir == null) {
            throw new IllegalArgumentException("destDir can't be null");
        }
        Iterator<FsDirectoryEntry> i = srcDir.iterator();
        while (i.hasNext()) {
            FsDirectoryEntry entry = i.next();
            if (entry.isFile()) {
                visitFile(entry, destDir);
            } else if (entry.isDirectory() && !".".equals(entry.getName()) && !"..".equals(entry.getName())) {
                // I create the destination directory...
                File newSubDir = new File(destDir, entry.getName());
                preVisitDirectory(entry, newSubDir);
                walkFileTree(entry.getDirectory(), newSubDir);
                postVisitDirectory(entry, newSubDir);
            }
        }
    }

    protected abstract void visitFile(FsDirectoryEntry file, File targetDirectory) throws IOException;

    protected abstract void preVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException;

    protected abstract void postVisitDirectory(FsDirectoryEntry directory, File targetDirectory) throws IOException;
}
