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

import android.util.Log;

import net.pictulog.otgdb.utils.PrettyPrint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.waldheinz.fs.BlockDevice;
import de.waldheinz.fs.ReadOnlyException;
import de.waldheinz.fs.fat.FatType;

/**
 * This is an implementation of a {@link BlockDevice}
 *
 * @author rostskadat
 */
public class OtgBlockDevice implements BlockDevice {

    private static final int DEFAULT_RW_SIZE = 0x4000;
    // http://www.easeus.com/resource/fat32-disk-structure.htm
    private static final int MBR_OFFSET_WATERMARK = 0x03;
    private static final int MBR_OFFSET_PARTITION_1 = 0x1be;

    private static final int PE_RECORD_SIZE = 0x10;
    private static final int PE_OFFSET_TYPE = 0x04;
    private static final int PE_OFFSET_SECTOR_OFFSET = 0x08;
    private static final int PE_OFFSET_NUMBER_OF_SECTORS = 0x0c;

    private boolean closed;
    private boolean readOnly;
    private int sectorSize;
    private int numberOfSectors;
    private int sectorOffset;
    private FatType fatType;
    private OtgDeviceFacade usbRamDiskFacade;

    public OtgBlockDevice(OtgDeviceFacade usbRamDiskFacade) {
        this.closed = true;
        this.readOnly = true;
        this.sectorSize = 0;
        this.numberOfSectors = 0;
        this.sectorOffset = 0;
        this.fatType = null;
        this.usbRamDiskFacade = usbRamDiskFacade;
    }

    /**
     * This method should be called in order to initialize the device. It will
     * basically read the boot sector from the previously claimed interface and
     * then try to find out the file system on the Device.
     *
     * @throws IOException
     */
    public void init() throws IOException {
        usbRamDiskFacade.readCapacity();
        sectorSize = usbRamDiskFacade.getSectorSize();
        initOtgDisk();
        closed = false;
    }

    @Override
    public void close() throws IOException {
        Log.d("USB", "close() is not implemented");
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Device is closed");
        }
        Log.d("USB", "flush() is not implemented");
    }

    @Override
    public int getSectorSize() throws IOException {
        if (closed) {
            throw new IOException("Device is closed");
        }
        return sectorSize;
    }

    @Override
    public long getSize() throws IOException {
        if (closed) {
            throw new IOException("Device is closed");
        }
        return numberOfSectors * sectorSize;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public FatType getFatType() {
        return fatType;
    }

    public void setFatType(FatType fatType) {
        this.fatType = fatType;
    }

    /**
     * Read a block of data from this device.
     *
     * @param devOffset the byte offset where to read the data from
     * @param dest      the destination buffer where to store the data read
     * @throws IOException on read error
     */
    @Override
    public void read(long devOffset, ByteBuffer dest) throws IOException {
        if (closed) {
            throw new IOException("Device is closed");
        }
        Log.d("USB", "reading: " + dest.capacity() + " bytes @" + devOffset);
        int srcOffset = (int) (devOffset % ((long) this.sectorSize));
        dest.put(
                readSectorFrom((int) (devOffset / ((long) this.sectorSize)),
                        (((dest.remaining() + srcOffset) + this.sectorSize) - 1) / this.sectorSize),
                srcOffset, dest.remaining());
    }

    /**
     * Writes a block of data to this device.
     *
     * @param devOffset the byte offset where to store the data
     * @param src       the source {@code ByteBuffer} to write to the device
     * @throws ReadOnlyException        if this {@code BlockDevice} is read-only
     * @throws IOException              on write error
     * @throws IllegalArgumentException if the {@code devOffset} is negative or the write would go
     *                                  beyond the end of the device
     * @see #isReadOnly()
     */
    @Override
    public void write(long devOffset, ByteBuffer src) throws ReadOnlyException, IOException, IllegalArgumentException {
        if (closed) {
            throw new IOException("Device is closed");
        }
        if (readOnly) {
            throw new ReadOnlyException();
        }
        Log.d("USB", "writing: " + src.capacity() + " bytes @" + devOffset);
        int i = (int) (devOffset / ((long) this.sectorSize));
        int remaining = src.remaining();
        int sectorsToWrite = ((this.sectorSize + remaining) - 1) / this.sectorSize;
        byte[] writeBuffer = new byte[remaining];
        src.get(writeBuffer);
        writeSectors(this.sectorOffset + i, sectorsToWrite, writeBuffer);
    }

    private void writeSectors(int sectorOffset, int sectorsToWrite, byte[] src) {
        Log.d("USB", "Writing " + sectorsToWrite + " sector(s) @ position #" + sectorOffset);

        int defaultNumberOfSectors = DEFAULT_RW_SIZE / this.sectorSize;
        byte[] writeBuffer = new byte[DEFAULT_RW_SIZE];
        int currentSector = 0;
        while (currentSector < sectorsToWrite) {
            try {
                int numberOfSector;
                byte[] writeChunk;
                int chunkLength = this.sectorSize * defaultNumberOfSectors;
                if (defaultNumberOfSectors > sectorsToWrite - currentSector) {
                    numberOfSector = sectorsToWrite - currentSector;
                    writeChunk = new byte[(this.sectorSize * numberOfSector)];
                    chunkLength = src.length - (this.sectorSize * currentSector);
                } else {
                    writeChunk = writeBuffer;
                    numberOfSector = defaultNumberOfSectors;
                }
                System.arraycopy(src, this.sectorSize * currentSector, writeChunk, 0, chunkLength);
                usbRamDiskFacade.write(sectorOffset + currentSector, numberOfSector, writeChunk);
                currentSector += defaultNumberOfSectors;
                writeBuffer = writeChunk;
            } catch (Exception e) {
                Log.e("USB", "Writing failed: " + e.getMessage(), e);
                return;
            }
        }
    }

    private byte[] readSectors(int firstSectorOffset, int sectorsToRead) {
        Log.d("USB", "Reading " + sectorsToRead + " sector(s) @ position #" + firstSectorOffset);
        int defaultNumberOfSectors = DEFAULT_RW_SIZE / this.sectorSize;
        byte[] readBuffer = new byte[(sectorsToRead * this.sectorSize)];
        int currentSector = 0;
        while (currentSector < sectorsToRead) {
            int numberOfSector = Math.min(defaultNumberOfSectors, sectorsToRead - currentSector);
            // defaultNumberOfSectors > sectorsToRead - currentSector ?
            // sectorsToRead - currentSector : defaultNumberOfSectors;
            try {
                Log.d("USB", String.format("Reading chunk #%d: sectors(%d)@ 0x%X", currentSector, numberOfSector,
                        (firstSectorOffset + currentSector)));
                System.arraycopy(usbRamDiskFacade.read(firstSectorOffset + currentSector, numberOfSector), 0,
                        readBuffer, sectorSize * currentSector, numberOfSector * sectorSize);
                currentSector += defaultNumberOfSectors;
            } catch (Exception e) {
                Log.e("USB", "Read failed: " + e.getMessage(), e);
            }
        }
        return readBuffer;
    }

    private void initOtgDisk() {
        Log.i("USB", "Initializing OTG disk, reading boot sector...");
        ByteBuffer bootSector = ByteBuffer.wrap(readSectors(0, 1));
        bootSector.order(ByteOrder.LITTLE_ENDIAN);

        byte[] executable = new byte[5];
        bootSector.position(MBR_OFFSET_WATERMARK);
        bootSector.get(executable);
        bootSector.rewind();
        String watermark = new String(executable);
        if (watermark.matches("(IBM|MS|..DOS|..dos|NTFS)")) {
            Log.d("USB", "Found FAT Floppy watermark...");
            sectorOffset = 0;
            numberOfSectors = usbRamDiskFacade.getSectors();
            setFatType(FatType.FAT32);
        } else if (watermark.startsWith("NTFS")) {
            Log.e("USB", "Found NTFS Floppy watermark: NTFS not supported");
        } else {
            Log.d("USB", "Found HardDisk partition table");
            int partitionNumber = 0;
            while (partitionNumber < 4) {
                byte[] pe = new byte[PE_RECORD_SIZE];
                System.arraycopy(bootSector.array(), MBR_OFFSET_PARTITION_1 + (partitionNumber * PE_RECORD_SIZE), pe, 0,
                        PE_RECORD_SIZE);
                Log.d("USB", "Partition Entry #" + partitionNumber + ":\n" + PrettyPrint.prettyPrint(pe));
                byte type = bootSector
                        .get(MBR_OFFSET_PARTITION_1 + (partitionNumber * PE_RECORD_SIZE) + PE_OFFSET_TYPE);
                if (translateFatType(type) != null) {
                    setFatType(translateFatType(type));
                    Log.i("USB", "Found fat " + getFatType().toString() + " (" + type + ") on partition #"
                            + partitionNumber);
                    break;
                } else {
                    Log.w("USB", "Partition " + partitionNumber + " is not supported: type=" + type);
                }
                partitionNumber++;
            }
            if (partitionNumber > 3) {
                Log.w("USB", "Defaulting to partition 0");
                partitionNumber = 0;
            }
            Log.d("USB", "Reading 1st sector offset and number of sectors...");
            sectorOffset = bootSector
                    .getInt((partitionNumber * PE_RECORD_SIZE) + MBR_OFFSET_PARTITION_1 + PE_OFFSET_SECTOR_OFFSET);
            numberOfSectors = bootSector
                    .getInt((partitionNumber * PE_RECORD_SIZE) + MBR_OFFSET_PARTITION_1 + PE_OFFSET_NUMBER_OF_SECTORS);
            if (sectorOffset > usbRamDiskFacade.getSectors() || numberOfSectors > usbRamDiskFacade.getSectors()) {
                sectorOffset = 0;
                numberOfSectors = usbRamDiskFacade.getSectors();
            }
        }
        Log.d("USB", "sectorOffset=" + sectorOffset);
        Log.d("USB", "numberOfSectors=" + numberOfSectors);
    }

    private byte[] readSectorFrom(int currentSectorOffset, int sectorsToRead) {
        return readSectors(sectorOffset + currentSectorOffset, sectorsToRead);
    }

    private FatType translateFatType(byte fatType) {
        if (fatType == 0x01) {
            return FatType.FAT12;
        } else if (fatType == 0x04 || fatType == 0x06 || fatType == 0x0e) {
            return FatType.FAT16;
        } else if (fatType == 0x0b || fatType == 0x0c) {
            return FatType.FAT32;
        }
        return null;
    }

}
