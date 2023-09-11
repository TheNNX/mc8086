package pl.pflp.vm8086;

public abstract class ArithmeticModRegRmInstructionImmReg extends ArithmeticModRegRmInstruction {

	public ArithmeticModRegRmInstructionImmReg(int flagBitMask) {
		super(flagBitMask);
	}

	public ArithmeticModRegRmInstructionImmReg() {
		super();
	}

	public boolean getIsSigned(byte selfByte) {
		return (selfByte & 0x02) != 0;
	}

	/**
	 * @return False if the instruction is not possible in a sign extending variant
	 *         (that is the case with logic instruction for example). True
	 *         otherwise.
	 */
	public boolean isSignedApplicable() {
		return true;
	}

	@Override
	public boolean getDirection(VM8086 vm, byte selfByte) {
		return true;
	}

	@Override
	protected Object[] createExecutionArgs(VM8086 vm, byte selfByte, ModRegRmDecoded decoded) {
		boolean W = getWidth(vm, selfByte);
		boolean S = getIsSigned(selfByte);
		boolean sApplicable = isSignedApplicable();

		/* check for an illegal combination of bit flags in instruction encoding */
		if ((S && !sApplicable) || (S && !W)) {
			System.out.println("Invalid signed bit in a sign ignoring instruction");
			return null;
		}

		/* if W = 1 and S = 1, read 1 byte and sign extend it */
		boolean WforReadingImm = W && (!S);
		byte[] immBytes = this.readImmediateBytes(vm, WforReadingImm);

		/* calculate the immediate */
		short immediate = 0;

		/* do the sign extending */
		if (W && S) {
			byte[] signExtendedBytes = { immBytes[0], (byte) ((0 != (immBytes[0] & 0x80)) ? 0xFF : 0x00) };
			immediate = vm.shortFromBytes(signExtendedBytes);
		}
		/* do not sign extend, perform normal immediate calculation */
		else {
			immediate = vm.shortFromBytes(immBytes);
		}

		/* create a temp register to store immediate in */
		Registers8086.Register16 temp = vm.registers.createTempRegister(immediate);

		/* replace the decoded source with the temp register */
		decoded.source = temp;

		/* create execution args with the base class' method */
		return super.createExecutionArgs(vm, selfByte, decoded);
	}

}
