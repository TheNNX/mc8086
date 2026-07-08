package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_CF;
import static thennx.vm8086.Registers8086.MASK_OF;
import static thennx.vm8086.Registers8086.MASK_PF;
import static thennx.vm8086.Registers8086.MASK_SF;
import static thennx.vm8086.Registers8086.MASK_ZF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public abstract class LogicModRegRmInstruction extends ArithmeticModRegRmInstruction {
	private final int clearFlags;
	public static final int defaultLogicFlagMask = MASK_SF | MASK_ZF | MASK_PF;

	public LogicModRegRmInstruction() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModRegRmInstruction(int flagMask) {
		super(flagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModRegRmInstruction(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
