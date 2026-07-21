package thennx.vm8086.devices;

import thennx.vm8086.IStateStorage;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.VM8086;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DummyIdeDrive implements IBlockDevice {
	private IVirtualMachine vm;
	private boolean readonly = true;
	private final Path imagePath;

	public DummyIdeDrive(IVirtualMachine vm, Path imagePath, boolean readonly) {
		this.vm = vm;
		this.imagePath = imagePath;
		this.readonly = readonly;
	}

	public DummyIdeDrive(IVirtualMachine vm) {
		this.vm = vm;

		String[] tests = {
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos_2.vhd",
				"C:\\Users\\Marcin\\Desktop\\bootloader\\bootloader",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\8086-accuracy-master\\8086ac.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos2.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\first.vhd",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos_1.vhd",
		};

		this.imagePath = Path.of(tests[3]);
	}

	@Override
	public boolean write(long lba, byte[] sectorData) {
		if (readonly) {
			return false;
		}

        try (RandomAccessFile writer = new RandomAccessFile(imagePath.toFile(), "rw")){
			writer.seek(lba * 512);
			writer.write(sectorData);
			return true;
        }
		catch (IOException e) {
            return false;
        }
    }

	@Override
	public byte[] read(long lba) {
		byte[] data = new byte[512];

		try (RandomAccessFile reader = new RandomAccessFile(imagePath.toFile(), "r")){
			reader.seek(lba * 512);
			reader.read(data);
		} catch (IOException e) {
            return data;
        }

        return data;
	}

	@Override
	public long getLbaFromChs(short cylinders, byte heads, byte sectors) {
		return (cylinders * getHeadsPerCylinder() + heads) * getSectorsPerTrack() + sectors - 1;
	}

	@Override
	public void saveCache() {

	}

	@Override
	public void deleteImage() {
		if (readonly) {
			return;
		}

        try {
            Files.deleteIfExists(imagePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	@Override
	public boolean isRemovable() {
		return false;
	}

	@Override
	public short getCylinders() {
		return 80;
	}

	@Override
	public byte getHeadsPerCylinder() {
		return 2;
	}

	@Override
	public byte getSectorsPerTrack() {
		return 18;
	}

	@Override
	public long getTotalSectorCount() {
		return getCylinders() * getHeadsPerCylinder() * getSectorsPerTrack();
	}
}
