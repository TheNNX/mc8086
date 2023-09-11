package pl.pflp.vm8086;

public interface IPS2Keyboard {

	void writeToDevice(byte data);

	void connectKeyboardController(IKeyboardController kc);

	IKeyboardController getKeyboardController();

	/**
	 * @implNote scancode is the native scancode of the implemented KEYBOARD, not
	 *           some host
	 */
	void keyPressed(int scancode);

	PS2Mode getCurrentMode();
}
