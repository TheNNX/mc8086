package thennx.vm8086.devices;

import thennx.vm8086.IVirtualMachine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DummyIdeDrive implements IBlockDevice {
	private IVirtualMachine vm;
	private boolean readonly = true;
	private IPathProvider imagePathProvider;
	private ATAChannel channel = null;
	private int channelIndex = 0;

	private final int cylinders;
	private final int heads;
	private final int sectors;
	private final int bytesPerSector;

	public interface IPathProvider {
		Path getPath();
	}

	public static class AbsolutePathProvider implements IPathProvider {
		private final Path path;

		public AbsolutePathProvider(Path path) {
			this.path = path;
		}

		@Override
		public Path getPath() {
			return path;
		}
	}

	public DummyIdeDrive(IVirtualMachine vm, Path imagePath, boolean readonly, int c, int h, int s, int bps) {
		this(vm, new AbsolutePathProvider(imagePath), readonly, c, h, s, bps);
	}

	public DummyIdeDrive(IVirtualMachine vm, IPathProvider provider, boolean readonly, int cylinders, int heads, int sectors, int bytesPerSector) {
		this.vm = vm;
		this.imagePathProvider = provider;
		this.readonly = readonly;
		this.cylinders = cylinders;
		this.heads = heads;
		this.bytesPerSector = bytesPerSector;
		this.sectors = sectors;
	}

	@Override
	public boolean write(long lba, byte[] sectorData) {
		if (readonly) {
			return false;
		}

		if (lba >= getTotalSectorCount()) {
			return false;
		}

        try (RandomAccessFile writer = new RandomAccessFile(imagePathProvider.getPath().toFile(), "rw")){
			writer.seek(lba * this.getBytesPerSector());
			writer.write(sectorData);
			return true;
        }
		catch (IOException e) {
            return false;
        }
    }

	@Override
	public byte[] read(long lba) {
		byte[] data = new byte[this.getBytesPerSector()];

		if (lba >= getTotalSectorCount()) {
			return data;
		}

		try (RandomAccessFile reader = new RandomAccessFile(imagePathProvider.getPath().toFile(), "r")){
			reader.seek(lba * this.getBytesPerSector());
			reader.read(data);
		} catch (IOException e) {
            return data;
        }

        return data;
	}

	@Override
	public long getLbaFromChs(int cylinders, int heads, int sectors) {
		return ((long) cylinders * getHeadsPerCylinder() + heads) * getSectorsPerTrack() + sectors - 1;
	}

	@Override
	public boolean onAdded(IVirtualMachine machine) {
		List<ATAChannel> channels = vm.getDevices(ATAChannel.class);

		for (ATAChannel channel : channels) {
			for (int i = 0; i < 2; i++) {
				if (channel.addBlockDevice(this, i)) {
					this.channel = channel;
					this.channelIndex = i;
					return IBlockDevice.super.onAdded(machine);
				}
			}
		}

		return false;
	}

	@Override
	public void onRemoved(IVirtualMachine machine) {
		if (this.channel != null) {
			assert this.channel.getBlockDevice(channelIndex) == this;

			this.channel.setBlockDevice(null, channelIndex);
		}
		IBlockDevice.super.onRemoved(machine);
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
            Files.deleteIfExists(imagePathProvider.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	@Override
	public boolean isRemovable() {
		return false;
	}

	@Override
	public int getCylinders() {
		return this.cylinders;
	}

	@Override
	public int getHeadsPerCylinder() {
		return this.heads;
	}

	@Override
	public int getSectorsPerTrack() {
		return this.sectors;
	}

	@Override
	public int getBytesPerSector() {
		return this.bytesPerSector;
	}

	@Override
	public long getTotalSectorCount() {
		return (long) getCylinders() * getHeadsPerCylinder() * getSectorsPerTrack();
	}
}
