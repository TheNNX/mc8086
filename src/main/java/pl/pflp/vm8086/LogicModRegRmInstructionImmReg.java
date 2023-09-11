package pl.pflp.vm8086;

import static pl.pflp.vm8086.Registers8086.*;

public abstract class LogicModRegRmInstructionImmReg extends ArithmeticModRegRmInstructionImmReg {
	private final int clearFlags;

	public LogicModRegRmInstructionImmReg() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = CF | OF;
	}

	public LogicModRegRmInstructionImmReg(int flagMask) {
		super(flagMask);
		clearFlags = CF | OF;
	}

	public LogicModRegRmInstructionImmReg(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
	public boolean isSignedApplicable() {
		return false;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}

}
