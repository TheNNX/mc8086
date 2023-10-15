package pl.pflp.vm8086.devices;

import java.util.ArrayList;
import java.util.LinkedList;

import net.minecraft.nbt.CompoundTag;
import pl.pflp.vm8086.VM8086;
import pl.pflp.vm8086.devices.InterruptSource.InterruptRequest;

/**
 * This is a temporary keyboard controller, later it will be replaced with a
 * 8255 PPI. This is loosely based on PS/2 8042 command interface.
 */
public class SimplifiedKeyboardController implements IPortSpaceDevice, IKeyboardController, InterruptSource {

	IPS2Keyboard keyboard = null;

	private static ArrayList<SimplifiedKeyboardController> controlers = new ArrayList<>();
	
	private InterruptRequest currentRequest = null;

	public void setKeyboard(IPS2Keyboard keyboard) {
		this.keyboard = keyboard;
		if (this.keyboard.getKeyboardController() != this)
			this.keyboard.connectKeyboardController(this);
	}

	public SimplifiedKeyboardController(VM8086 vm) {
		this.vm = vm;
		controlers.add(this);
	}

	@Override
	public boolean matchPort(short port) {
		return (port == 0x60) || (port == 0x64);
	}

	private VM8086 vm;

	public byte getStatus() {
		int result = 0;

		if (outputBufferFull)
			result |= 1;
		if (controllerInputBufferFull)
			result |= 2;
		if (firmwareFlag)
			result |= 4;
		if (commandDataFlag)
			result |= 8;
		if (parityError)
			result |= 64;
		if (timeoutError)
			result |= 128;

		return (byte) (result & 0xFF);
	}

	@Override
	public void load(CompoundTag tag) {

	}

	@Override
	public void save(CompoundTag tag) {
		tag.putBoolean("outputBufferFull", outputBufferFull);
		tag.putBoolean("controllerInputBufferFull", controllerInputBufferFull);
		tag.putBoolean("commandDataFlag", commandDataFlag);
		tag.putBoolean("parityError", parityError);
		tag.putBoolean("timeoutError", timeoutError);
		tag.putBoolean("firmwareFlag", firmwareFlag);
		tag.putByte("dataOut", dataOut);
		tag.putBoolean("systemReset", systemReset);
		tag.putBoolean("deviceIrqPending", deviceIrqPending);
		tag.putByte("lastCommand", lastCommand);
		tag.putBoolean("waitingForRamData", waitingForRamData);
		tag.putBoolean("waitingForCOPData", waitingForCOPData);
		tag.putByteArray("internalRam", internalRam);
		tag.putBoolean("firstPs2PortEnabled", firstPs2PortEnabled);
		byte[] deviceQueuedBytesArray = new byte[deviceQueuedBytes.size()];
		int i = 0;
		for (Byte b : deviceQueuedBytes)
			deviceQueuedBytesArray[i++] = b;
		tag.putByteArray("deviceQueuedBytes", deviceQueuedBytesArray);
	}

	private boolean outputBufferFull = false;
	private boolean controllerInputBufferFull = false;
	private boolean commandDataFlag = true;
	private boolean parityError = false;
	private boolean timeoutError = false;
	private boolean firmwareFlag = true;
	private byte dataOut;
	private boolean systemReset = true;
	private boolean deviceIrqPending = false;
	private byte lastCommand = 0;
	private boolean waitingForRamData = false;
	private boolean waitingForCOPData = false;
	private byte[] internalRam = new byte[32];
	private boolean firstPs2PortEnabled = true;
	LinkedList<Byte> deviceQueuedBytes = new LinkedList<Byte>();

	private void writeToCommandRegister(byte dataByte) {
		/* cast to int to avoid casting later */
		int data = dataByte & 0xFF;
		waitingForRamData = false;

		lastCommand = dataByte;

		/* read internal RAM */
		if (data >= 0x20 && data <= 0x3F) {
			dataOut = internalRam[data - 0x20];
			outputBufferFull = true;
		}
		/* write to internal RAM */
		else if (data >= 0x60 && data <= 0x7F) {
			controllerInputBufferFull = false;
			waitingForRamData = true;
		}
		/* test PS/2 controller */
		else if (data == 0xAA) {
			if (outputBufferFull == true)
				return;
			/* this is an emulator, the test always passes */
			dataOut = 0x55;
			outputBufferFull = true;
		}
		/* test PS/2 port 1 */
		else if (data == 0xAB) {
			if (outputBufferFull == true)
				return;
			/* this is an emulator, the test always passes */
			dataOut = 0x00;
			outputBufferFull = true;
		}
		/* disable PS/2 port 1 */
		else if (data == 0xAD) {
			firstPs2PortEnabled = false;
		}
		/* enable PS/2 port 1 */
		else if (data == 0xAE) {
			firstPs2PortEnabled = true;
		}
		/* read Controller Output Port */
		else if (data == 0xD0) {
			if (outputBufferFull == true)
				return;
			dataOut = getControllerOutputPort();
			outputBufferFull = true;
		}
		/* write to Controller Output Port */
		else if (data == 0xD1) {
			waitingForCOPData = true;
			controllerInputBufferFull = false;
		}
	}

	private byte getControllerOutputPort() {
		int result = 0;

		if (systemReset)
			result |= 1;
		if (deviceIrqPending)
			result |= 16;

		return (byte) (result & 0xFF);
	}

	private void writeToControllerOutputPort(byte outputPort) {
		int data = outputPort & 0xFF;

		/* do NOT set to one */
		if ((data & 1) != 0) {
			vm.isRunning = false;
		}
		/* set IRQ pending status */
		deviceIrqPending = ((data & 16) != 0);
		
		if (deviceIrqPending) {
			/* TODO */
			this.currentRequest = new InterruptRequest(this);
			deviceIrqPending = false;
		}
	}

	@Override
	public void writeByte(short port, byte data) {
		if (controllerInputBufferFull == true)
			return;

		/* write to the command register */
		if (port == 0x64) {
			writeToCommandRegister(data);
		}
		/* write to the data register */
		if (port == 0x60) {
			/* if the controller is waiting for data, send the data to controller */
			if (waitingForRamData == true) {
				int ramAddress = lastCommand - 0x20;
				internalRam[ramAddress] = data;
			} else if (waitingForCOPData == true) {
				writeToControllerOutputPort(data);
			}
			/* otherwise, send data to the device */
			else {
				if (keyboard != null)
					keyboard.writeToDevice(data);
			}

			waitingForRamData = false;
			waitingForCOPData = false;
		}
	}

	public void receiveFromDevice(byte data) {
		if (firstPs2PortEnabled == false)
			return;

		if (outputBufferFull == false) {
			outputBufferFull = true;
			dataOut = data;
		} else {
			deviceQueuedBytes.add(data);
		}
		
		this.currentRequest = new InterruptRequest(this);
	}

	private void updateDataOut() {
		outputBufferFull = deviceQueuedBytes.size() != 0;

		if (deviceQueuedBytes.size() == 0) {
			dataOut = (byte) 0xFF;
		}
		/* if there's a byte in the queue, update dataOut and status */
		else {
			dataOut = deviceQueuedBytes.removeFirst();
		}
	}

	@Override
	public byte readByte(short port) {
		byte result = dataOut;

		if (port == (short) 0x64) {
			return getStatus();
		}

		if (outputBufferFull == false) {
			return (byte) 0xFF;
		}

		updateDataOut();

		if (port == (short) 0x60) {
			return result;
		}

		return (byte) 0xFF;
	}

	@Override
	public InterruptRequest consume() {
		InterruptRequest result = this.peek();
		this.currentRequest = null;
		return result;
	}

	@Override
	public InterruptRequest peek() {
		return this.currentRequest;
	}

}
