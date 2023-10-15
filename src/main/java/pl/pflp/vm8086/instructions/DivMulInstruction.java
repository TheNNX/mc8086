package pl.pflp.vm8086.instructions;

import static pl.pflp.vm8086.Registers8086.CF;
import static pl.pflp.vm8086.Registers8086.OF;

import pl.pflp.vm8086.DivideErrorException;
import pl.pflp.vm8086.VM8086;

public abstract class DivMulInstruction extends ArithmeticModInstrRmInstruction {

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws DivideErrorException {
		byte selfByte = bytes[0];
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];

		int op = decoded.readDestination(vm) & 0xFFFF;
		boolean W = this.getWidth(vm, selfByte);
		int bitnumber = W ? 16 : 8;

		operation(vm, op, bitnumber);
	}

	@Override
	@Deprecated
	public final int operation(VM8086 vm, int operand) {
		return 0;
	}

	@Override
	public abstract int operation(VM8086 vm, int operand, int bitNumber) throws DivideErrorException;

	protected void mul(VM8086 vm, int operand, int operand2, int bitnumber) {
		boolean setFlags = false;
		int result = operand * operand2;

		if (bitnumber == 8) {
			vm.registers.AX.write((short) (result & 0xFFFF));
			setFlags = (vm.registers.AX.readHigh() != 0);
		} else {
			short resultLow = (short) (result & 0xFFFF);
			short resultHigh = (short) ((result & 0xFFFF0000) >> 16);
			vm.registers.AX.write(resultLow);
			vm.registers.DX.write(resultHigh);
			setFlags = (resultHigh != 0);
		}

		if (setFlags)
			vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | (CF | OF)));
		else
			vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() & (~(CF | OF))));
	}

	protected void div(VM8086 vm, int operand1l, int operand1h, int operand2, int bitnumber)
			throws DivideErrorException {
		/*
		 * long, because Java doesn't have unsigned types - it would make too much sense
		 * :/ ...
		 */
		long dividing = operand1l + operand1h * 65536;
		if (operand2 == 0)
			throw new DivideErrorException(vm, vm.registers.CS.shortValue(), (short) (vm.registers.IP.intValue() - 1));
		int result = (int) (dividing / operand2);
		int remainder = (int) (dividing % operand2);
		if (bitnumber == 8) {
			vm.registers.AX.writeLow((byte) (result & 0xFF));
			vm.registers.AX.writeHigh((byte) (remainder & 0xFF));
		} else {
			vm.registers.AX.write((short) (result & 0xFFFF));
			vm.registers.DX.write((short) (remainder & 0xFFFF));
		}
	}
}
