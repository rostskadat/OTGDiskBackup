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
package net.pictulog.otgdb.device;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import net.pictulog.otgdb.utils.PrettyPrint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * This is a simple USB facade. It implements only a small subset of the USB
 * Bulk Device command set. Namely :
 * <ul>
 * <li>{@code readCapacity()}: this method is used to find the USB device number of sectors and its sector size</li>
 * <li>{@code requestSense()}: this method is used to request information about the device error condition</li>
 * <li>{@code read(10)}: this method is used to read a specific set of sectors from the device</li>
 * <li>{@code write(10)}: this method is used to write a specific set of sectors to the device</li>
 * </ul>
 * <p/>
 * The {@code readCapacity()} should be called first.
 *
 * @author rostskadat
 */
public class OtgDeviceFacade {

    private static final byte USB_DIRECTION_TO_HOST = Byte.MIN_VALUE; // 0x80;
    private static final byte USB_DIRECTION_TO_DEVICE = 0x00;

    // constants from http://wiki.osdev.org/USB_Mass_Storage_Class_Devices
    // http://www.usb.org/developers/docs/devclass_docs/usbmassbulk_10.pdf (p13)
    // Command Block Wrapper offsets
    private static final int USB_CBW_SIGNATURE = 0x43425355;
    private static final int USB_CBW_LENGTH = 0x1f;
    private static final int USB_CBW_OFF_SIGNATURE = 0x00;
    private static final int USB_CBW_OFF_TAG = 0x04;
    private static final int USB_CBW_OFF_LENGTH = 0x08;
    private static final int USB_CBW_OFF_DIRECTION = 0x0c;
    private static final int USB_CBW_OFF_LUN = 0x0d;
    private static final int USB_CBW_OFF_CMD_LENGTH = 0x0e;
    private static final int USB_CBW_OFF_CMD_DATA = 0x0f;

    // Command Status Wrapper offsets
    private static final int USB_CSW_SIGNATURE = 0x53425355;
    private static final int USB_CSW_LENGTH = 0x0d;
    private static final int USB_CSW_OFF_SIGNATURE = 0x00;
    private static final int USB_CSW_OFF_TAG = 0x04;
    // private static final int USB_CSW_OFF_RESIDUE = 0x08;
    private static final int USB_CSW_OFF_STATUS = 0x0c;

    private static final int USB_CSW_STATUS_SUCCESS = 0x00;
    // private static final int USB_CSW_STATUS_FAILED = 0x01;
    // private static final int USB_CSW_STATUS_PHASE_ERROR = 0x02;

    // http://www.usb.org/developers/docs/devclass_docs/usbmass-ufi10.pdf
    private static final byte UFI_CMD_REQUEST_SENSE_OC = 0x03; // Page 37
    private static final int UFI_CMD_REQUEST_SENSE_LENGTH = 0x0c;
    private static final int UFI_CMD_REQUEST_SENSE_OFF_LENGTH = 0x04;
    private static final byte UFI_CMD_REQUEST_SENSE_RES_LENGTH = 0x12;

    private static final byte UFI_CMD_READ_CAPACITY_OC = 0x25; // Page 32
    private static final int UFI_CMD_READ_CAPACITY_LENGTH = 0x0c;
    private static final int UFI_CMD_READ_CAPACITY_RES_LENGTH = 0x08;

    private static final byte UFI_CMD_READ_OC = 0x28; // Page 30
    private static final int UFI_CMD_READ_LENGTH = 0x0c;

    private static final byte UFI_CMD_WRITE_OC = 0x2a; // Page 46
    private static final int UFI_CMD_WRITE_LENGTH = 0x0c;

    // Logical Block Address
    private static final int UFI_CMD_READ_CAPACITY_LAST_LBA = 0x00;
    // Transfer Length
    private static final int UFI_CMD_READ_CAPACITY_BOCK_LENGTH = 0x04;
    private static final int UFI_CMD_READ_LBA = 0x02; // Logical Block Address
    private static final int UFI_CMD_READ_TL = 0x07; // Transfer Length
    private static final int UFI_CMD_WRITE_LBA = 0x02; // Logical Block Address
    private static final int UFI_CMD_WRITE_TL = 0x02; // Transfer Length

    private final UsbDeviceConnection usbDeviceConnection;
    private final byte lun;
    private final ByteBuffer cbwBuffer;
    private final ByteBuffer cswBuffer;
    private final ByteBuffer ufiCmdRequestSenseBuffer;
    private final ByteBuffer ufiCmdReadCapacityBuffer;
    private final ByteBuffer ufiCmdReadBuffer;
    private final ByteBuffer ufiCmdWriteBuffer;
    private UsbEndpoint inputEndpoint;
    private UsbEndpoint outputEndpoint;
    private int cbwTag;
    private int sectors;
    private int sectorSize;
    private byte[] receiveBuffer;

    public OtgDeviceFacade(UsbInterface usbInterface, byte lun, UsbDeviceConnection usbDeviceConnection,
                           UsbDevice usbDevice) {

        this.cbwBuffer = ByteBuffer.wrap(new byte[USB_CBW_LENGTH]);
        this.cswBuffer = ByteBuffer.wrap(new byte[USB_CSW_LENGTH]);

        this.ufiCmdRequestSenseBuffer = ByteBuffer.wrap(new byte[UFI_CMD_REQUEST_SENSE_LENGTH]);
        this.ufiCmdReadCapacityBuffer = ByteBuffer.wrap(new byte[UFI_CMD_READ_CAPACITY_LENGTH]);
        this.ufiCmdReadBuffer = ByteBuffer.wrap(new byte[UFI_CMD_READ_LENGTH]);
        this.ufiCmdWriteBuffer = ByteBuffer.wrap(new byte[UFI_CMD_WRITE_LENGTH]);
        this.cbwTag = 0;
        this.sectorSize = 512;
        this.receiveBuffer = new byte[8192];
        this.usbDeviceConnection = usbDeviceConnection;
        this.lun = lun;
        initEnpoints(usbInterface);

        initCommandBuffers();
    }

    private void initEnpoints(UsbInterface usbInterface) {
        usbInterface.getInterfaceSubclass();
        UsbEndpoint endpoint = usbInterface.getEndpoint(0);
        UsbEndpoint endpoint2 = usbInterface.getEndpoint(1);
        if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
            UsbEndpoint usbEndpoint = endpoint2;
            endpoint2 = endpoint;
            endpoint = usbEndpoint;
        }
        inputEndpoint = endpoint2;
        outputEndpoint = endpoint;
    }

    private void initCommandBuffers() {
        // These fields never change...
        cbwBuffer.order(ByteOrder.LITTLE_ENDIAN);
        cbwBuffer.putInt(USB_CBW_OFF_SIGNATURE, USB_CBW_SIGNATURE);
        cbwBuffer.put(USB_CBW_OFF_DIRECTION, USB_DIRECTION_TO_HOST);
        cbwBuffer.put(USB_CBW_OFF_LUN, lun);

        cswBuffer.order(ByteOrder.LITTLE_ENDIAN);

        ufiCmdRequestSenseBuffer.put(UFI_CMD_REQUEST_SENSE_OC);
        ufiCmdRequestSenseBuffer.put(UFI_CMD_REQUEST_SENSE_OFF_LENGTH, UFI_CMD_REQUEST_SENSE_RES_LENGTH);

        ufiCmdReadCapacityBuffer.put(UFI_CMD_READ_CAPACITY_OC);

        ufiCmdReadBuffer.put(UFI_CMD_READ_OC);
        ufiCmdReadBuffer.order(ByteOrder.BIG_ENDIAN);

        ufiCmdWriteBuffer.put(UFI_CMD_WRITE_OC);
        ufiCmdWriteBuffer.order(ByteOrder.BIG_ENDIAN);

    }

    private void prepareCBW(int cbwSubsequentLength, byte[] command, byte direction) {
        cbwBuffer.putInt(USB_CBW_OFF_LENGTH, cbwSubsequentLength);
        cbwBuffer.put(USB_CBW_OFF_DIRECTION, direction);
        cbwBuffer.put(USB_CBW_OFF_CMD_LENGTH, (byte) command.length);
        cbwBuffer.position(USB_CBW_OFF_CMD_DATA);
        cbwBuffer.put(command, 0, command.length);
    }

    private synchronized void sendCBW() throws UsbCommanException {
        ByteBuffer byteBuffer = cbwBuffer;
        int tag = cbwTag;
        cbwTag = tag + 1;
        byteBuffer.putInt(USB_CBW_OFF_TAG, tag);
        int length = cbwBuffer.array().length;
        int transferredLength = usbDeviceConnection.bulkTransfer(outputEndpoint, cbwBuffer.array(), length, 800);
        if (transferredLength != length) {
            Log.e("USB", "Failed to send CBW#" + tag + ": " + PrettyPrint.prettyPrint(cbwBuffer.array()) + ", but got "
                    + transferredLength + " bytes in return");
            throw new UsbCommanException("Failed to send CBW#" + tag, -1);
        }
        Log.d("USB", "CBW#" + tag + ":\n" + PrettyPrint.prettyPrint(cbwBuffer.array()));
    }

    private void receiveCSW() throws UsbCommanException {
        cswBuffer.rewind();
        int cswLength = usbDeviceConnection.bulkTransfer(inputEndpoint, cswBuffer.array(), cswBuffer.capacity(), 800);
        if (cswLength < 0) {
            Log.d("USB", "CSW Error: wrong cswLength= " + cswLength + "\n" + PrettyPrint.prettyPrint(cswBuffer.array()));
            throw new UsbCommanException("CSW Error: couldn't read sense data", -3);
        } else if (cswBuffer.getInt(USB_CSW_OFF_SIGNATURE) != USB_CSW_SIGNATURE) {
            Log.d("USB", "CSW Error: wrong signature");
            throw new UsbCommanException("CSW Error: wrong signature", -4);
        } else if (cswBuffer.get(USB_CSW_OFF_STATUS) != USB_CSW_STATUS_SUCCESS) {
            Log.d("USB", "CSW Error: failed status");
            throw new UsbCommanException("CSW Error: failed status", -4);
        }
        int tag = cswBuffer.getInt(USB_CSW_OFF_TAG);
        Log.d("USB", "CSW#" + tag + ":\n" + PrettyPrint.prettyPrint(cswBuffer.array()));
        // TODO: Should I check that the tag corresponds to the command tag???
        // Resetting the CSW buffer
        Arrays.fill(cswBuffer.array(), (byte) 0);
    }

    void readCapacity() throws UsbCommanException {
        Log.d("USB", "readCapacity...");
        ByteBuffer readCapacityResponse = ByteBuffer.wrap(new byte[UFI_CMD_READ_CAPACITY_RES_LENGTH]);
        readCapacityResponse.order(ByteOrder.BIG_ENDIAN);
        prepareCBW(UFI_CMD_READ_CAPACITY_RES_LENGTH, ufiCmdReadCapacityBuffer.array(), USB_DIRECTION_TO_HOST);
        // Sending the command
        sendCBW();
        // reading the result
        usbDeviceConnection.bulkTransfer(inputEndpoint, readCapacityResponse.array(), readCapacityResponse.capacity(),
                750);
        // checking the command status
        receiveCSW();
        sectors = readCapacityResponse.getInt(UFI_CMD_READ_CAPACITY_LAST_LBA);
        sectorSize = readCapacityResponse.getInt(UFI_CMD_READ_CAPACITY_BOCK_LENGTH);
        Log.d("USB", "readCapacity: Sectors=" + sectors + ", Sector Size=" + sectorSize);
    }

    void requestSense() throws UsbCommanException {
        Log.d("USB", "requestSense...");
        ByteBuffer requestSenseResponse = ByteBuffer.wrap(new byte[UFI_CMD_REQUEST_SENSE_RES_LENGTH]);
        requestSenseResponse.order(ByteOrder.BIG_ENDIAN);
        prepareCBW(UFI_CMD_REQUEST_SENSE_RES_LENGTH, ufiCmdRequestSenseBuffer.array(), USB_DIRECTION_TO_HOST);
        // Sending the command
        sendCBW();
        // reading the result
        usbDeviceConnection.bulkTransfer(inputEndpoint, requestSenseResponse.array(), requestSenseResponse.capacity(),
                750);
        Log.d("USB", "requestSense: \n" + PrettyPrint.prettyPrint(requestSenseResponse.array()));
        // checking the command status
        receiveCSW();
        byte errorCode = requestSenseResponse.get();
        boolean valid = (errorCode & 0x80) == 1;
        errorCode &= ~0b100000000;
        if (valid) {
            int information = requestSenseResponse.getInt(0x03);
            Log.d("USB", "Request Sense: Error=" + errorCode + ", valid=" + valid + ", info=" + information);
        } else {
            Log.d("USB", String.format("Request Sense: Error=0x%x", errorCode));
        }
    }

    synchronized void write(int sectorOffset, int numberOfSector, byte[] buffer) {
        Log.d("USB", "write...");
        int writeResponseLength = sectorSize * numberOfSector;
        if (writeResponseLength < buffer.length) {
            Log.d("USB", "write data length to small");
        } else {
            try {
                ufiCmdWriteBuffer.putInt(UFI_CMD_WRITE_LBA, sectorOffset);
                ufiCmdWriteBuffer.putShort(UFI_CMD_WRITE_TL, (short) numberOfSector);
                prepareCBW(writeResponseLength, ufiCmdWriteBuffer.array(), USB_DIRECTION_TO_DEVICE);
                try {
                    sendCBW();
                    this.usbDeviceConnection.bulkTransfer(this.outputEndpoint, buffer, writeResponseLength, 5000);
                } catch (Exception e) {
                    Log.e("USB", e.getMessage(), e);
                }
                receiveCSW();
            } catch (UsbCommanException e2) {
                Log.e("USB", e2.getMessage(), e2);
            }
        }
    }

    synchronized byte[] read(int sectorOffset, int numberOfSector) {
        int readResponseLength = sectorSize * numberOfSector;
        if (readResponseLength > receiveBuffer.length) {
            this.receiveBuffer = new byte[readResponseLength];
        }
        try {
            int bulkTransfer;
            Log.d("USB", "read...");
            ufiCmdReadBuffer.putInt(UFI_CMD_READ_LBA, sectorOffset);
            ufiCmdReadBuffer.putShort(UFI_CMD_READ_TL, (short) numberOfSector);
            prepareCBW(readResponseLength, ufiCmdReadBuffer.array(), USB_DIRECTION_TO_HOST);
            try {
                sendCBW();
                bulkTransfer = usbDeviceConnection.bulkTransfer(inputEndpoint, receiveBuffer, readResponseLength, 3000);
            } catch (Exception e) {
                Log.e("USB", e.getMessage(), e);
                bulkTransfer = -1;
            }
            receiveCSW();
            if (bulkTransfer != readResponseLength) {
                throw new UsbCommanException("USB Read Error", 100);
            }
        } catch (UsbCommanException e2) {
            Log.e("USB", e2.getMessage(), e2);
        }
        return receiveBuffer;
    }

    int getSectorSize() {
        return sectorSize;
    }

    int getSectors() {
        return sectors;
    }
}
