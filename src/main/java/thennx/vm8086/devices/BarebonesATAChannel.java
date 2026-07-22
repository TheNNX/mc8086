package thennx.vm8086.devices;

import java.io.IOException;
import java.util.LinkedList;

import thennx.vm8086.IStateStorage;

public class BarebonesATAChannel implements IPortSpaceDevice, IStateful, IInterruptSource {
	private final short basePort;
	private final short controlPort;
	private boolean slaveSelected;
	private byte headsOrLba28High4;
	private boolean usingLba;
	private byte errorByte;
	private int secCount;
	private boolean irqEnabled;
	private long lba;
	private boolean deviceBusy;
	private boolean deviceReady;
	private boolean deviceFault;
	private boolean deviceSeekComplete;
	private boolean dataRequest;
	private boolean errorFlag;
	private boolean irqPending;
	private boolean writeInProgress;
	private int waitingForBytesOfDataIn;

	private final LinkedList<Byte> dataInQueue = new LinkedList<>();
	private final LinkedList<Byte> dataOutQueue = new LinkedList<>();

	private final IBlockDevice[] drives = new IBlockDevice[2];

	public IBlockDevice getBlockDevice(int i) {
		return this.drives[i];
	}

	public BarebonesATAChannel(short basePort, short controlPort) {
		this.basePort = basePort;
		this.controlPort = controlPort;
		initialise();
	}

	@Override
	public void initialise() {
		this.slaveSelected = false;
		this.headsOrLba28High4 = 0;
		this.usingLba = false;
		this.secCount = 0;
		this.errorByte = 0;
		this.irqEnabled = true;
		this.lba = 0;
		this.deviceBusy = false;
		this.deviceReady = true;
		this.deviceFault = false;
		this.deviceSeekComplete = true;
		this.dataRequest = false;
		this.errorFlag = false;
		this.irqPending = false;
		this.writeInProgress = false;
		this.waitingForBytesOfDataIn = 0;
		this.dataInQueue.clear();
		this.dataOutQueue.clear();
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
		for (byte element : dataOut) {
			enqueueDataOut(element);
		}
	}

	private void enqueueIdentifyData() {
		IBlockDevice selectedDevice = getSelectedDevice();
		if (selectedDevice == null) {
			return;
		}
		enqueueDataOutWord((short) (selectedDevice.isRemovable() ? 128 : 64));
		/* TODO: check if one of those is supposed to have -1 */
		enqueueDataOutWord(selectedDevice.getCylinders());
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord(selectedDevice.getHeadsPerCylinder());
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord((short) 0);
		enqueueDataOutWord(selectedDevice.getSectorsPerTrack());
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
		if ((port == controlPort) || (port >= basePort && port <= (basePort + 7)))
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
		secCount = 0;
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

		if (waitingForBytesOfDataIn <= dataInQueue.size() && dataOutQueue.isEmpty()) {
			if (writeInProgress) {
				long effectiveLba = getEffectiveLbaForNormalIo();
				for (int i = 0; i < secCount; i++) {
					byte[] currentSec = new byte[512];
					for (int j = 0; j < 512; j++)
						currentSec[j] = dataInQueue.removeFirst();
					boolean success = selectedBlockDevice.write(effectiveLba, currentSec);
					if (!success) {
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
		int antimask = ~(0xFF << bitshift);

		lba = (lba & antimask) | ((long) data << bitshift);
	}

	@Override
	public byte readByte(short port) {
		if (port >= controlPort) {
            if (port - this.controlPort == 0) {
                return getStatus();
            }
            return -1;
        }

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
	public void save(IStateStorage stateStorage) throws IOException {
		stateStorage.set("slaveSelected", slaveSelected);
		stateStorage.set("headsOrLba28High4", headsOrLba28High4);
		stateStorage.set("usingLba", usingLba);
		stateStorage.set("errorByte", errorByte);
		stateStorage.set("secCount", secCount);
		stateStorage.set("irqEnabled", irqEnabled);
		stateStorage.set("lba", lba);
		stateStorage.set("deviceBusy", deviceBusy);
		stateStorage.set("deviceReady", deviceReady);
		stateStorage.set("deviceFault", deviceFault);
		stateStorage.set("deviceSeekComplete", deviceSeekComplete);
		stateStorage.set("dataRequest", dataRequest);
		stateStorage.set("errorFlag", errorFlag);
		stateStorage.set("irqPending", irqPending);
		stateStorage.set("writeInProgress", writeInProgress);
		stateStorage.set("waitingForBytesOfDataIn", waitingForBytesOfDataIn);

		for (IBlockDevice drive : drives) {
			if (drive instanceof IStateful stateful) {
				stateful.save(stateStorage);
			}
		}
	}

	@Override
	public void load(IStateStorage stateStorage) throws IOException {
		slaveSelected = stateStorage.getBoolean("slaveSelected").orElse(slaveSelected);
		headsOrLba28High4 = stateStorage.getByte("headsOrLba28High4").orElse(headsOrLba28High4);
		usingLba = stateStorage.getBoolean("usingLba").orElse(usingLba);
		errorByte = stateStorage.getByte("errorByte").orElse(errorByte);
		secCount = stateStorage.getInt("secCount").orElse(secCount);
		irqEnabled = stateStorage.getBoolean("irqEnabled").orElse(irqEnabled);
		lba = stateStorage.getLong("lba").orElse(lba);
		deviceBusy = stateStorage.getBoolean("deviceBusy").orElse(deviceBusy);
		deviceReady = stateStorage.getBoolean("deviceReady").orElse(deviceReady);
		deviceFault = stateStorage.getBoolean("deviceFault").orElse(deviceFault);
		deviceSeekComplete = stateStorage.getBoolean("deviceSeekComplete").orElse(deviceSeekComplete);
		dataRequest = stateStorage.getBoolean("dataRequest").orElse(dataRequest);
		errorFlag = stateStorage.getBoolean("errorFlag").orElse(errorFlag);
		irqPending = stateStorage.getBoolean("irqPending").orElse(irqPending);
		writeInProgress = stateStorage.getBoolean("writeInProgress").orElse(writeInProgress);
		waitingForBytesOfDataIn = stateStorage.getInt("waitingForBytesOfDataIn").orElse(waitingForBytesOfDataIn);

		for (IBlockDevice drive : drives) {
			if (drive instanceof IStateful stateful) {
				stateful.load(stateStorage);
			}
		}
	}

	@Override
	public void deleteSaved(IStateStorage stateStorage) throws IOException {
		for (IBlockDevice drive : drives) {
			if (drive != null) {
				drive.deleteImage();
			}
			if (drive instanceof IStateful stateful) {
				stateful.deleteSaved(stateStorage);
			}
		}
	}

	public void addDevice(IBlockDevice device, boolean slave) {
		if (slave)
			this.drives[1] = device;
		else
			this.drives[0] = device;
	}

	@Override
	public InterruptRequest consume() {
		irqPending = false;
		return new InterruptRequest(this);
	}

	@Override
	public InterruptRequest peek() {
		if (!irqPending) {
			return null;
		}

		return new InterruptRequest(this);
	}
}
