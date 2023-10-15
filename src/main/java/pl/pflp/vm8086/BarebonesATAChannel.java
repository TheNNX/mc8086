package pl.pflp.vm8086;

import java.util.LinkedList;

import net.minecraft.nbt.CompoundTag;

public class BarebonesATAChannel implements IPortSpaceDevice {

	private short basePort;
	private short controlPort;
	private boolean slaveSelected = false;
	private byte headsOrLba28High4 = 0;
	private boolean usingLba = false;
	private byte errorByte = 0;
	private int secCount = 0;
	private boolean irqEnabled = false;
	private long lba = 0;
	private boolean deviceBusy = false;
	private boolean deviceReady = true;
	private boolean deviceFault = false;
	private boolean deviceSeekComplete = true;
	private boolean dataRequest = false;
	private boolean errorFlag = false;
	private boolean irqAckAwaited = false;
	private boolean irqPending = false;
	private boolean writeInProgress = false;

	private int waitingForBytesOfDataIn = 0;

	private LinkedList<Byte> dataInQueue = new LinkedList<Byte>();
	private LinkedList<Byte> dataOutQueue = new LinkedList<Byte>();

	private IBlockDevice[] drives = new IBlockDevice[2];

	public BarebonesATAChannel(short basePort, short controlPort) {
		this.basePort = basePort;
		this.controlPort = controlPort;
	}

	private void receiveData(byte data) {
		dataInQueue.add(data);
		processDataRequestStatus();
	}

	private byte sendData() {
		byte result = dataOutQueue.removeFirst();
		processDataRequestStatus();
		return result;
	}

	private void enqueueDataOut(byte dataOut) {
		dataOutQueue.addLast(dataOut);
	}

	private void enqueueDataOutWord(short dataOut) {
		enqueueDataOut((byte) (dataOut & 0xFF));
		enqueueDataOut((byte) ((dataOut & 0xFF00) >> 8));
	}

	private void enqueueDataOutArray(byte[] dataOut) {
		for (int i = 0; i < dataOut.length; i++) {
			enqueueDataOut(dataOut[i]);
		}
	}

	private void enqueueIdentifyData() {
		IBlockDevice selectedDevice = getSelectedDevice();
		if (selectedDevice == null) {
			return;
		}
		enqueueDataOutWord((short) (selectedDevice.isRemovable() ? 128 : 64));
		/* TODO: check if one of those is supposed to have -1 */
		enqueueDataOutWord((short) selectedDevice.getCylinders());
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) selectedDevice.getHeadsPerCylinder());
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) selectedDevice.getSectorsPerTrack());
		for (int i = 7; i <= 21; i++)
			enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 4);
		for (int i = 23; i <= 48; i++)
			enqueueDataOutWord((short) 0);
		/* lba supported */
		enqueueDataOutWord((short) 512);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 2);
		enqueueDataOutWord((short) 0);
		for (int i = 53; i <= 58; i++)
			enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		long totalSectors = selectedDevice.getTotalSectorCount();
		enqueueDataOutWord((short) (totalSectors & 0xFFFF));
		enqueueDataOutWord((short) ((totalSectors & 0xFFFF0000) >> 8));
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 2);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		for (int i = 71; i <= 255; i++)
			enqueueDataOutWord((short) 0);
	}

	private IBlockDevice getSelectedDevice() {
		if (slaveSelected)
			return drives[1];
		return drives[0];
	}

	@Override
	public boolean matchPort(short port) {
		if (port == controlPort)
			return true;
		if (port >= basePort && port <= (basePort + 7))
			return true;
		return false;
	}

	@Override
	public void writeByte(short port, byte data) {
		if (port >= controlPort) {
			switch (port - this.controlPort) {
			case 2:
				setControlRegister(data);
				break;
			}
		}
		System.out.println("Port: " + (port - this.basePort) + " with data " + (data & 0xFF));

		switch (port - this.basePort) {
		case 0:
			receiveData(data);
			break;
		case 2:
			manageSecCount(data);
			break;
		case 3:
		case 4:
		case 5:
			manageLba(port - this.basePort - 3, data);
			break;
		case 6:
			setHddvSel(data);
			break;
		case 7:
			issueCommand(data);
		}
	}

	/* only mandatory ATA-2 commands implemented */
	private void issueCommand(byte data) {
		switch (data & 0xFF) {
		case 0x90:
			errorByte = (byte) 0x01;
			break;
		case 0xEC:
			deviceBusy = true;

			enqueueIdentifyData();

			dataRequest = true;
			deviceBusy = false;
			if (irqEnabled)
				irqPending = true;

			break;
		case 0x91:
			int logicalSectorTranslation = secCount & 0xFF;
			int logicalHeads = (getHddvSel() & 0xF) + 1;
			IBlockDevice selectedDevice = getSelectedDevice();
			if (selectedDevice.getSectorsPerTrack() != logicalSectorTranslation
					|| selectedDevice.getHeadsPerCylinder() != logicalHeads) {
				errorByte = (byte) 4;
				errorFlag = true;
			}
			break;
		case 0x20:
		case 0x21:
			readSectors(true);
			break;
		case 0x40:
		case 0x41:
			readSectors(false);
			break;
		case 0x70:
		case 0x71:
		case 0x72:
		case 0x73:
		case 0x74:
		case 0x75:
		case 0x76:
		case 0x77:
		case 0x78:
		case 0x79:
		case 0x7A:
		case 0x7B:
		case 0x7C:
		case 0x7D:
		case 0x7E:
		case 0x7F:
			break;
		case 0x30:
		case 0x31:
			writeSectors();
			break;
		}
	}

	private void readSectors(boolean discardData) {
		deviceBusy = true;

		long effectiveLba = getEffectiveLbaForNormalIo();

		for (int i = 0; i < secCount; i++) {
			byte[] result = getSelectedDevice().read(i + effectiveLba);
			if (discardData)
				enqueueDataOutArray(result);
		}
		deviceBusy = false;
		if (discardData)
			dataRequest = true;
	}

	private long getEffectiveLbaForNormalIo() {
		short cyl = (short) (((lba & 0xFF0000) >> 16) | ((lba & 0xFF00) >> 8));
		byte head = headsOrLba28High4;
		byte sec = (byte) (lba & 0xFF);
		return this.usingLba ? ((lba & 0xFFFFFF) | (headsOrLba28High4 << 24))
				: getSelectedDevice().getLbaFromChs(cyl, head, sec);
	}

	private void writeSectors() {
		deviceBusy = true;

		dataRequest = true;
		deviceBusy = false;
		waitingForBytesOfDataIn = secCount * 512;
		writeInProgress = true;
		processDataRequestStatus();
	}

	private void processDataRequestStatus() {
		IBlockDevice selectedBlockDevice = getSelectedDevice();

		if (waitingForBytesOfDataIn <= dataInQueue.size() && 0 == dataOutQueue.size()) {
			if (writeInProgress) {
				long effectiveLba = getEffectiveLbaForNormalIo();
				for (int i = 0; i < secCount; i++) {
					byte[] currentSec = new byte[512];
					for (int j = 0; j < 512; j++)
						currentSec[j] = dataInQueue.removeFirst();
					boolean success = selectedBlockDevice.write(effectiveLba, currentSec);
					if (success == false) {
						errorFlag = true;
						errorByte = 0x04;
						break;
					}
				}
			}
			waitingForBytesOfDataIn = 0;
			dataInQueue.clear();
			writeInProgress = false;
			dataRequest = false;
		}
	}

	private void setControlRegister(byte datab) {
		int data = datab & 0xFF;
		irqEnabled = 0 != (data & 0x02);
		/*
		 * TODO: if (data & 0x04) doDevicesReset();
		 */
	}

	private void manageSecCount(byte data) {
		secCount = (secCount << 8);
		secCount = (secCount & (~0xFF)) | data;
	}

	private void manageLba(int lbaIndex, byte data) {
		int bitshift = lbaIndex * 8;
		int antimask = ~(0xFF << lbaIndex);
		
		lba = (lba & antimask) | (data << bitshift);
	}

	@Override
	public byte readByte(short port) {
		if (port >= controlPort) {
			switch (port - this.controlPort) {
			case 2:
				return getStatus();
			default:
				return -1;
			}
		}
		// System.out.println("Port: " + (port - this.basePort));

		switch (port - this.basePort) {
		case 0:
			return sendData();
		case 1:
			return errorByte;
		case 2:
			return (byte) (secCount & 0xFF);
		case 3:
			return (byte) (lba & 0xFF);
		case 4:
			return (byte) ((lba & 0xFF00) >> 8);
		case 5:
			return (byte) ((lba & 0xFF0000) >> 16);
		case 6:
			return getHddvSel();
		case 7:
			irqAckAwaited = false;
			irqPending = false;
			return getStatus();
		default:
			return -1;
		}
	}

	private byte getStatus() {
		int result = 0;

		if (getSelectedDevice() == null)
			return 0;

		if (errorFlag)
			result |= 1;
		if (dataRequest)
			result |= 8;
		if (deviceSeekComplete)
			result |= 16;
		if (deviceFault)
			result |= 32;
		if (deviceReady)
			result |= 64;
		if (deviceBusy)
			result |= 128;

		return (byte) (result & 0xFF);
	}

	private byte getHddvSel() {
		int result = 0;

		if (slaveSelected)
			result |= (1 << 4);

		result |= (headsOrLba28High4 & 0xF);

		if (usingLba)
			result |= (1 << 6);

		return (byte) (result & 0xFF);
	}

	private void setHddvSel(byte datab) {
		int data = datab & 0xFF;

		headsOrLba28High4 = (byte) (data & 0xF);
		usingLba = 0 != (data & (1 << 6));
		slaveSelected = 0 != (data & (1 << 4));
	}

	@Override
	public void load(CompoundTag tag) {

	}

	@Override
	public void save(CompoundTag tag) {

	}

	public void addDevice(IBlockDevice device, boolean slave) {
		if (slave)
			this.drives[1] = device;
		else
			this.drives[0] = device;
	}
}
