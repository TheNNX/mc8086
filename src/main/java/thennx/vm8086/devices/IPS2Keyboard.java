package thennx.vm8086.devices;

import thennx.vm8086.VM8086;

public interface IPS2Keyboard extends IDevice {

	void writeToDevice(byte data);

	void connectKeyboardController(IKeyboardController kc);

	IKeyboardController getKeyboardController();

	/**
	 * @implNote scancode is the native scancode of the implemented KEYBOARD, not
	 *           some host
	 */
	void keyPressed(int scancode);

	PS2Mode getCurrentMode();

	void queueKeystroke(int key, char character, boolean pressed);

	boolean handleKeystrokeQueue(VM8086 vm);
}
