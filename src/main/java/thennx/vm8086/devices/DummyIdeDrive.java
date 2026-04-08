package thennx.vm8086.devices;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DummyIdeDrive implements IBlockDevice {

	@Override
	public boolean write(long lba, byte[] sectorData) {
		return false;
	}

	@Override
	public byte[] read(long lba) {
		byte[] data = new byte[512];
		System.out.println("Reading LBA " + lba);

		String[] tests = {
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos_2.vhd",
				"C:\\Users\\Marcin\\Desktop\\bootloader\\bootloader",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\8086-accuracy-master\\8086ac.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos2.img",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\first.vhd",
				"C:\\Users\\Marcin\\Desktop\\oc86boot\\dos\\dos_1.vhd",
		};

		try (FileInputStream fs = new FileInputStream(tests[4])){
			fs.skipNBytes(lba * 512);
			fs.read(data);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.printf("Magic: %X%X\n", data[510], data[511]);
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
	public boolean isRemovable() {
		return false;
	}

	@Override
	public short getCylinders() {
		return 100;
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
