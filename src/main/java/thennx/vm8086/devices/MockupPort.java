package thennx.vm8086.devices;

import net.minecraft.nbt.CompoundTag;

public class MockupPort implements IPortSpaceDevice {

	private short port;
	private byte data;
	
	public MockupPort(short port) {
		this.port = port;
	}
	
	@Override
	public boolean matchPort(short port) {
		return port == this.port;
	}

	@Override
	public void writeByte(short port, byte data) {
		this.data = data;
	}

	@Override
	public byte readByte(short port) {
		return data;
	}

	@Override
	public void load(CompoundTag tag) {

	}

	@Override
	public void save(CompoundTag tag) {

	}

}
