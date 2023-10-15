package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.VM8086;

public class JmpFlagInstruction extends JmpConditionalInstruction {

	private final int cmpMask;
	private final int valMask;

	public JmpFlagInstruction(int cmpMask, int valMask) {
		this.cmpMask = cmpMask;
		this.valMask = valMask;
	}

	@Override
	public boolean checkCondition(VM8086 vm, byte selfByte) {
		return (vm.registers.FLAGS.intValue() & cmpMask) == valMask;
	}

}
