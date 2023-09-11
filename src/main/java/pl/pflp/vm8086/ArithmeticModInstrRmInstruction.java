package pl.pflp.vm8086;

public abstract class ArithmeticModInstrRmInstruction extends ArithmeticModRegRmInstruction {

	public ArithmeticModInstrRmInstruction() {
		super(ArithmeticModRegRmInstruction.defaultFlagMask);
	}

	public ArithmeticModInstrRmInstruction(int flagMask) {
		super(flagMask);
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfByte = bytes[0];
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];

		int op = decoded.readDestination(vm) & 0xFFFF;
		boolean W = this.getWidth(vm, selfByte);
		int bitnumber = W ? 16 : 8;

		int result = vm.executeOperation(this, op, op, bitnumber);
		decoded.writeDestination((short) (result & 0xFFFF), vm);
	}

	@Override
	public int operation(VM8086 vm, int operand1, int operand2) throws DivideErrorException {
		return operation(vm, operand1);
	}

	public abstract int operation(VM8086 vm, int operand);
}
