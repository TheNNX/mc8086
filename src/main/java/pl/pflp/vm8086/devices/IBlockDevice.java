package pl.pflp.vm8086.devices;

public interface IBlockDevice {
	boolean write(long lba, byte[] sectorData);

	byte[] read(long lba);

	long getLbaFromChs(short cylinders, byte heads, byte sectors);

	void saveCache();

	boolean isRemovable();

	short getCylinders();

	byte getHeadsPerCylinder();

	byte getSectorsPerTrack();

	long getTotalSectorCount();
}
