package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class ArithmeticInstructionImmA extends ImmediateInstruction implements IOperation {
	public final int flagBitMask;

	public ArithmeticInstructionImmA(int flagBitMask) {
		this.flagBitMask = flagBitMask;
	}

	public ArithmeticInstructionImmA() {
		this.flagBitMask = ArithmeticModRegRmInstruction.defaultFlagMask;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfByte = bytes[0];
		boolean W = getWidth(vm, selfByte);
		int bitnumber = W ? 16 : 8;
		byte[] immBytes = (byte[]) data[0];

		int op1, op2;
		op1 = vm.registers.AX.readDecoded(vm, W);
		op2 = vm.shortFromBytes(immBytes);

		int result = vm.executeOperation(this, op1, op2, bitnumber);

		vm.registers.AX.writeDecoded(vm, W, (short) (result & 0xFFFF));
	}

	public abstract int operation(VM8086 vm, int operand1, int operand2);

	@Override
	public int getFlagBitMask() {
		return flagBitMask;
	}
}
