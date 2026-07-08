package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_CF;
import static thennx.vm8086.Registers8086.MASK_OF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public abstract class LogicModRegRmInstructionImmReg extends ArithmeticModRegRmInstructionImmReg {
	private final int clearFlags;

	public LogicModRegRmInstructionImmReg() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModRegRmInstructionImmReg(int flagMask) {
		super(flagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModRegRmInstructionImmReg(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}

}
