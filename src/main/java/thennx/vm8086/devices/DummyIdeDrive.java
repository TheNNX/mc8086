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

	public DummyIdeDrive(IVirtualMachine vm, Path imagePath, boolean readonly) {
		this.vm = vm;
		this.imagePathProvider = new AbsolutePathProvider(imagePath);
		this.readonly = readonly;
	}

	public DummyIdeDrive(IVirtualMachine vm, IPathProvider provider, boolean readonly) {
		this.vm = vm;
		this.imagePathProvider = provider;
		this.readonly = readonly;
	}

	@Override
	public boolean write(long lba, byte[] sectorData) {
		if (readonly) {
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

		try (RandomAccessFile reader = new RandomAccessFile(imagePathProvider.getPath().toFile(), "r")){
			reader.seek(lba * this.getBytesPerSector());
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
