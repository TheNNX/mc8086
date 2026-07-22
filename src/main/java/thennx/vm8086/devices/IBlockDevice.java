package thennx.vm8086.devices;

public interface IBlockDevice extends IDevice {
	boolean write(long lba, byte[] sectorData);

	byte[] read(long lba);

	long getLbaFromChs(short cylinders, byte heads, byte sectors);

	void saveCache();

	void deleteImage();

	boolean isRemovable();

	short getCylinders();

	byte getHeadsPerCylinder();

	byte getSectorsPerTrack();

	long getTotalSectorCount();
}
