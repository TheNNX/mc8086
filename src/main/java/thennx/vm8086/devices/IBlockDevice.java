package thennx.vm8086.devices;

public interface IBlockDevice extends IDevice {
	boolean write(long lba, byte[] sectorData);

	byte[] read(long lba);

	long getLbaFromChs(int cylinders, int heads, int sectors);

	void saveCache();

	void deleteImage();

	boolean isRemovable();

	int getCylinders();

	int getHeadsPerCylinder();

	int getSectorsPerTrack();

    default int getBytesPerSector() { return 512; }

    long getTotalSectorCount();
}
