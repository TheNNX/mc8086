package thennx.vm8086.devices;

import java.nio.file.Path;

public interface IPortSpaceDevice extends IDevice {
	boolean matchPort(short port);

	void writeByte(short port, byte data);

	byte readByte(short port);
}
