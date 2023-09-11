package pl.pflp.vm8086;

import net.minecraft.nbt.CompoundTag;

public class DebugDataPort implements IPortSpaceDevice {

	private short strPort;

	public DebugDataPort(short strPort) {
		this.strPort = strPort;
	}

	@Override
	public boolean matchPort(short port) {
		return ((port & 0xFFFF) == this.strPort);
	}

	@Override
	public void writeByte(short port, byte data) {
		System.out.print((char) data);
	}

	@Override
	public byte readByte(short port) {
		return (byte) 0xFF;
	}

	@Override
	public void load(CompoundTag tag) {
	}

	@Override
	public void save(CompoundTag tag) {
	}

	@Override
	public boolean processIrqs() {
		return false;
	}

}
