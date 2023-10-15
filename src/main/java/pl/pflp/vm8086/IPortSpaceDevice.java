package pl.pflp.vm8086;

import net.minecraft.nbt.CompoundTag;

public interface IPortSpaceDevice {
	boolean matchPort(short port);

	void writeByte(short port, byte data);

	byte readByte(short port);

	void load(CompoundTag tag);

	void save(CompoundTag tag);
}
