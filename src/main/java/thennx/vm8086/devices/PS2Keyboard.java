package thennx.vm8086.devices;

import java.util.concurrent.ConcurrentLinkedQueue;

import thennx.vm8086.Registers8086;
import thennx.vm8086.VM8086;

public class PS2Keyboard implements IPS2Keyboard {

	private IKeyboardController controller = null;

	public PS2Keyboard() {
		restoreDefaultParameters();
	}

	private PS2Mode mode = PS2Mode.Mode1;

	private static final byte RESEND = (byte) 0xFE;
	private static final byte ACK = (byte) 0xFA;
	private static final byte ECHO = (byte) 0xEE;
	private static final byte TEST_PASSED = (byte) 0xAA;

	private boolean scanningEnabled = true;
	private byte lastSentByte = RESEND;
	private int delayBeforeDecodedMs = 0;
	private int delayBetweenDecodedMs = 0;
	private byte ledStates = 0;
	
	private static class QueuedKey {
		public final char character;
		public final int scancode;
		public final boolean pressed;
		
		public QueuedKey(int scancode, boolean pressed, char character) {
			this.character = character;
			this.scancode = scancode;
			this.pressed = pressed;
		}
	}
	
	private final ConcurrentLinkedQueue<QueuedKey> queuedKeys = new ConcurrentLinkedQueue<>();

	private enum State {
		nextByteLEDStates, nextByteScanCodeSet, nextByteTypematicByte, normal
	}

	private State state = State.normal;

	private void restoreDefaultParameters() {
		state = State.normal;
		ledStates = 0;
		scanningEnabled = true;
		lastSentByte = RESEND;
		delayBeforeDecodedMs = 500;
		delayBetweenDecodedMs = 92;
	}

	private void sendToController(byte data) {
		lastSentByte = data;
		if (controller != null)
			controller.receiveFromDevice(data);
	}

	@Override
	public void writeToDevice(byte data) {
		int dataAsInt = data & 0xFF;
		
		if (state == State.normal) {
			switch (dataAsInt) {
			case 0xED:
				state = State.nextByteLEDStates;
				break;
			case 0xEE:
				sendToController(ECHO);
				break;
			case 0xF0:
				state = State.nextByteScanCodeSet;
				break;
			case 0xF2:
				sendToController(ACK);
				/* TODO */
				break;
			case 0xF3:
				state = State.nextByteTypematicByte;
				break;
			case 0xF4:
				scanningEnabled = true;
				sendToController(ACK);
				break;
			case 0xF5:
				scanningEnabled = false;
				sendToController(ACK);
				break;
			case 0xF6:
				restoreDefaultParameters();
				sendToController(ACK);
				break;
			case 0xFE:
				sendToController(lastSentByte);
				break;
			case 0xFF:
				restoreDefaultParameters();
				sendToController(TEST_PASSED);
				break;
			default:
				sendToController(RESEND);
				break;
			}
		} else {
			switch (state) {
			case nextByteLEDStates:
				ledStates = data;
				state = State.normal;
				sendToController(ACK);
				break;
			case nextByteScanCodeSet:
				byte subcommand = data;
				if (subcommand == 0) {
					if (mode == PS2Mode.Mode2)
						sendToController((byte) 0x43);
					else if (mode == PS2Mode.Mode1)
						sendToController((byte) 0x41);
				}
				sendToController(ACK);

				if (subcommand == 1)
					mode = PS2Mode.Mode1;
				else if (subcommand == 2)
					mode = PS2Mode.Mode2;

				state = State.normal;
				break;
			case nextByteTypematicByte:
				int repeatRateEncoded = data & 31;
				int delayBeforeRepeatEnc = (data & 96) >> 5;

				/* TODO */
				delayBeforeDecodedMs = 250 * (delayBeforeRepeatEnc + 1);
				delayBetweenDecodedMs = (int) (1000.0
						* ((1.0 / 30.0) + (0.5 - (1.0 / 30.0)) * repeatRateEncoded));

				sendToController(ACK);
				state = State.normal;
				break;
			default:
				sendToController(RESEND);
				state = State.normal;
				break;
			}
		}
	}

	@Override
	public void connectKeyboardController(IKeyboardController kc) {
		this.controller = kc;
	}

	@Override
	public IKeyboardController getKeyboardController() {
		return controller;
	}

	@Override
	public void keyPressed(int scancode) {
		if (scanningEnabled) {
			byte scancodeByte = (byte) (scancode & 0xFF);
			sendToController(scancodeByte);
		}
	}

	@Override
	public PS2Mode getCurrentMode() {
		return mode;
	}

	@Override
	public void queueKeystroke(int key, char character, boolean pressed) {
		queuedKeys.add(new QueuedKey(key, pressed, character));
	}

	@Override
	public boolean handleKeystrokeQueue(VM8086 vm8086) {
		if ((vm8086.registers.FLAGS.intValue() & Registers8086.MASK_IF) != 0) {
			QueuedKey input = queuedKeys.poll();
			if (input != null) {
				if (input.pressed)
					this.keyPressed(input.scancode);
				else
					this.keyPressed(input.scancode | 0x80);
				
				return true;
			} 	
		}
		return false;
	}
}
