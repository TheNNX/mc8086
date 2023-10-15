package pl.pflp.vm8086.devices;

import net.minecraft.nbt.CompoundTag;

public class DebugNumberPort implements IPortSpaceDevice {

	private short portBase;
	byte staged = 0;

	boolean waitingForSecond = false;

	public DebugNumberPort(short portBase) {
		this.portBase = portBase;
	}

	@Override
	public boolean matchPort(short port) {
		return (this.portBase == port);
	}

	@Override
	public void writeByte(short port, byte data) {
		if (!waitingForSecond) {
			staged = data;
			waitingForSecond = true;
		} else {
			int number = (data << 8) | staged;
			System.out.println(number);
			staged = 0;
			waitingForSecond = false;
		}
	}

	@Override
	public byte readByte(short port) {
		return staged;
	}

	@Override
	public void load(CompoundTag tag) {
	}

	@Override
	public void save(CompoundTag tag) {
	}
}
