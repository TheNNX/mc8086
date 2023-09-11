package pl.pflp.vm8086;

public class DummyIdeDrive implements IBlockDevice {

	byte[] realData = new byte[512];

	public DummyIdeDrive() {
		String initialData = "a big pile of bullshit shared disk cock balls shit";
		char[] data = initialData.toCharArray();
		for (int i = 0; i < ((data.length > realData.length) ? realData.length : data.length); i++) {
			realData[i * 2] = (byte) data[i];
			realData[i * 2 + 1] = (byte) (0x0A);
		}
	}

	@Override
	public boolean write(long lba, byte[] sectorData) {
		this.realData = sectorData;
		return true;
	}

	@Override
	public byte[] read(long lba) {
		return realData;
	}

	@Override
	public long getLbaFromChs(short cylinders, byte heads, byte sectors) {
		return 0;
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
