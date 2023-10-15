package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.DivideErrorException;
import pl.pflp.vm8086.VM8086;
import pl.pflp.vm8086.instructions.ModRegRmInstruction.ModRegRmDecoded;

public abstract class ArithmeticModRegRmInstruction extends ModRegRmInstruction implements IOperation {

	public final int flagBitMask;

	public ArithmeticModRegRmInstruction(int flagBitMask) {
		this.flagBitMask = flagBitMask;
	}

	public ArithmeticModRegRmInstruction() {
		this.flagBitMask = defaultFlagMask;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
		boolean w = getWidth(vm, bytes[0]);
		int bitnumber = w ? 16 : 8;

		int op2 = decoded.readSource(vm) & 0xFFFF;
		int op1 = decoded.readDestination(vm) & 0xFFFF;

		int result = vm.executeOperation(this, op1, op2, bitnumber);
		decoded.writeDestination((short) (result & 0xFFFF), vm);
	}

	public abstract int operation(VM8086 vm, int operand1, int operand2) throws DivideErrorException;

	@Override
	public int getFlagBitMask() {
		return flagBitMask;
	}
}
