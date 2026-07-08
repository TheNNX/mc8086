package thennx.vm8086.devices;

public class DebugDataPort implements IPortSpaceDevice {

	private final short strPort;

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
}
