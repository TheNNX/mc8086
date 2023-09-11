package pl.pflp.vm8086;

public class ModInstrRmInstructions extends ModRegRmInstruction {

	public ModRegRmInstruction[] subinstructions = null;

	public ModInstrRmInstructions(ModRegRmInstruction[] subinstructions) {
		this.subinstructions = subinstructions;
	}

	@Override
	protected final void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) {
	}

	@Override
	public void decodeAndExecute(VM8086 vm, short segment) throws CpuException {
		/* read the reg field */
		byte modRegRm = vm.readMemoryByte16(vm.registers.CS.shortValue(), (short) (vm.registers.IP.intValue() + 1));
		byte reg = (byte) ((modRegRm & 0x38) / 8);

		/* select subinstruction by the value of reg field in ModRegR/M */
		ModRegRmInstruction subinstruction = this.subinstructions[(int) reg];

		if (subinstruction == null)
			throw new UndefinedOpcodeException(vm);

		/* decode the subinstruction instead */
		subinstruction.decode(vm, segment, true);
	}

}
