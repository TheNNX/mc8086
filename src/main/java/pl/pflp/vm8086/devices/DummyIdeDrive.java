package pl.pflp.vm8086.devices;

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
		
		try (FileInputStream fs = new FileInputStream("C:\\Users\\Marcin\\Desktop\\bootloader\\bootloader")){
			fs.skipNBytes(lba * 512);
			fs.read(data);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
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
	public boolean isRemovable() {
		return false;
	}

	@Override
	public short getCylinders() {
		return 2;
	}

	@Override
	public byte getHeadsPerCylinder() {
		return 10;
	}

	@Override
	public byte getSectorsPerTrack() {
		return 10;
	}

	@Override
	public long getTotalSectorCount() {
		return getCylinders() * getHeadsPerCylinder() * getSectorsPerTrack();
	}

}
