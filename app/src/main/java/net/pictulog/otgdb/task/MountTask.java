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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import net.pictulog.otgdb.MainActivity;
import net.pictulog.otgdb.PreferencesActivity;
import net.pictulog.otgdb.R;
import net.pictulog.otgdb.device.OtgBlockDevice;
import net.pictulog.otgdb.device.OtgDeviceFacade;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import de.waldheinz.fs.BlockDevice;
import de.waldheinz.fs.FileSystem;
import de.waldheinz.fs.FsDirectory;
import de.waldheinz.fs.FsDirectoryEntry;
import de.waldheinz.fs.FsFile;
import de.waldheinz.fs.fat.FatFileSystem;
import de.waldheinz.fs.fat.FatType;
import de.waldheinz.fs.fat.SuperFloppyFormatter;
import de.waldheinz.fs.util.RamDisk;

/**
 * This class is specifically in charge of mounting the FileSystem on the OTG
 * disk.
 *
 * @author rostskadat
 */
public class MountTask extends AsyncTask<Void, Void, FileSystem> {

    private static final byte DEFAULT_LUN = 0;

    private final Context context;
    private final MountTaskListener listener;
    private UsbInterface usbInterface;
    private UsbManager manager;
    private UsbDevice device;
    private UsbDeviceConnection usbDeviceConnection;
    private int errorMessageId;

    public MountTask(MountTaskListener listener, Context context) {
        this.context = context;
        this.listener = listener;
    }

    @Override
    protected FileSystem doInBackground(Void... params) {
        try {
            if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PreferencesActivity.PREFS_DEBUG, false)) {
                return mountMockDevice();
            }
            return mountDevice();
        } catch (Exception e) {
            // Souldn't be raised...
            Log.e("MountTask", e.getMessage(), e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(FileSystem fs) {
        super.onPostExecute(fs);
        if (fs != null) {
            listener.onMountReady(fs);
        } else {
            listener.onMountFailed(errorMessageId);
        }
    }

    private FileSystem mountMockDevice() throws IOException {
        Log.d("MountTask", "creating fs hierarchy...");
        BlockDevice dev = new RamDisk(100 * 1024 * 1024);
        Log.d("MountTask", "BlockDevice created...");
        FileSystem fs = SuperFloppyFormatter.get(dev).format();
        Log.d("MountTask", "File system formatted...");
        FsDirectory rootDir = fs.getRoot();
        // Add some files to root...
        for (int i = 0; i < 10; i++) {
            addMockFile(rootDir, i, 1024 * 5);
        }
        // Add some files to DCIM/100_PANO...
        FsDirectoryEntry dcim = rootDir.addDirectory("DCIM");
        FsDirectoryEntry subDir = dcim.getDirectory().addDirectory("100_PANO");
        for (int i = 0; i < 155; i++) {
            addMockFile(subDir.getDirectory(), i, 1024 * 100);
        }
        return fs;
    }

    private void addMockFile(FsDirectory directory, int id, int preferredSize) throws IOException {
        FsFile fsFile = directory.addFile(String.format("image_%02d.jpg", id)).getFile();
        String content = String.format(Locale.getDefault(), "%1d", id % 10);
        byte[] buffer = new byte[preferredSize];
        Arrays.fill(buffer, (byte) content.charAt(0));
        ByteBuffer buff = ByteBuffer.wrap(buffer);
        buff.rewind();
        fsFile.write(0, buff);
        fsFile.flush();
    }

    private FileSystem mountDevice() {
        Log.d("MountTask", "opening OTG disk...");
        manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> devices = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = devices.values().iterator();
        if (devices.size() < 1) {
            Log.e("MountTask", "No device found...");
            errorMessageId = R.string.pluginDisk;
        } else if (deviceIterator.hasNext()) {
            device = deviceIterator.next();
            Log.d("MountTask", String.format("Found device: %04X:%04X, Class: %02X:%02X, at %s",
                    device.getVendorId(), device.getProductId(),
                    device.getDeviceClass(), device.getDeviceSubclass(),
                    device.getDeviceName()));
            if (manager.hasPermission(device)) {
                return claimInterface(device);
            } else {
                Log.e("MountTask", "No permission granted to access this device, requesting...");
                manager.requestPermission(device,
                        PendingIntent.getBroadcast(context, 0, new Intent(MainActivity.ACTION_USB_PERMISSION), 0));
            }
            Log.d("MountTask", "No more devices found");
        }
        return null;
    }

    private FileSystem claimInterface(UsbDevice usbDevice) {
        if (usbDevice != null) {
            usbDeviceConnection = manager.openDevice(usbDevice);
            if (usbDeviceConnection == null) {
                Log.e("MountTask", "Can't open device!");
                errorMessageId = R.string.mountingFailed;
                return null;
            }
            // Let's give some time to the device to get online...
            usbInterface = usbDevice.getInterface(0);
            Log.d("MountTask", "Got interface #" + usbInterface.getId());
            if (usbDeviceConnection.claimInterface(usbInterface, true)) {
                int interfaceClass = usbInterface.getInterfaceClass();
                Log.d("MountTask", String.format(
                        "Claimed interface #" + usbInterface.getId()
                                + ": Class=0x%02X, Sub Class=0x%02X, Protocol=0x%02X",
                        interfaceClass, usbInterface.getInterfaceSubclass(),
                        usbInterface.getInterfaceProtocol()));
                for (interfaceClass = 0; interfaceClass < usbInterface.getEndpointCount(); interfaceClass++) {
                    String endpointType = "BULK";
                    String endpointDirection = "";
                    if (usbInterface.getEndpoint(interfaceClass).getType() == 2) {
                        if (usbInterface.getEndpoint(interfaceClass).getDirection() == UsbConstants.USB_DIR_IN) {
                            endpointDirection = "/IN";
                        } else {
                            endpointDirection = "/OUT";
                        }
                    } else {
                        endpointType = "NOT_BULK";
                    }
                    Log.d("MountTask", String.format("EP@ 0x%02X, %s%s",
                            usbInterface.getEndpoint(interfaceClass).getAddress(), endpointType,
                            endpointDirection));
                }
                return readFileSystem();
            } else {
                errorMessageId = R.string.mountingFailed;
                Log.e("MountTask", "Couldn't claim interface");
            }
        }
        return null;
    }

    private FileSystem readFileSystem() {
        try {
            Log.i("MountTask", "Mounting OTG disk on interface #" + usbInterface.getId() + ", device #" + device.getDeviceId());
            OtgDeviceFacade facade = new OtgDeviceFacade(usbInterface, DEFAULT_LUN, usbDeviceConnection, device);
            OtgBlockDevice blockDevice = new OtgBlockDevice(facade);
            blockDevice.init();
            if (blockDevice.getFatType() == FatType.FAT32) {
                boolean readOnly = !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PreferencesActivity.PREFS_DELETE, false);
                Log.d("MountTask", "Reading FAT filesystem " + (readOnly ? "ro" : "rw"));
                blockDevice.setReadOnly(readOnly);
                return FatFileSystem.read(blockDevice, readOnly);
            } else {
                errorMessageId = R.string.mountingFailed;
                Log.e("MountTask", "File system not supported");
            }
        } catch (IOException e) {
            errorMessageId = R.string.mountingFailed;
            Log.e("MountTask", e.getMessage(), e);
        }
        return null;
    }
}
