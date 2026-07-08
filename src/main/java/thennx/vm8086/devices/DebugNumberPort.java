package thennx.vm8086.devices;

import thennx.vm8086.IStateStorage;

public class DebugNumberPort implements IPortSpaceDevice, IStateful {

	private final short portBase;
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
			System.out.printf("%X\n", number);
			staged = 0;
			waitingForSecond = false;
		}
	}

	@Override
	public byte readByte(short port) {
		return staged;
	}

	@Override
	public void load(IStateStorage stateStorage) {
	}

	@Override
	public void save(IStateStorage stateStorage) {
	}

	@Override
	public void deleteSaved(IStateStorage stateStorage) {

	}
}
