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

import android.preference.PreferenceActivity;

import java.util.List;

/**
 * @author rostskadat
 */
public class PreferencesActivity extends PreferenceActivity {

    public static final String PREFS_FIRST_LAUNCH = "net.pictulog.otgdb.first_launch";
    public static final String PREFS_FROM_FILE = "net.pictulog.otgdb.from_file";
    public static final String PREFS_TO_FILE = "net.pictulog.otgdb.to_file";
    public static final String PREFS_OVERWRITE = "net.pictulog.otgdb.overwrite";
    public static final String PREFS_DELETE = "net.pictulog.otgdb.delete";
    public static final String PREFS_DEBUG = "net.pictulog.otgdb.debug";

    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preferences_headers, target);
    }

    protected boolean isValidFragment(String fragmentName) {
        if (PreferencesActivityFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }
}