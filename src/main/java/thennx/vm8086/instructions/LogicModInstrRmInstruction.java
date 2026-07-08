package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_CF;
import static thennx.vm8086.Registers8086.MASK_OF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public abstract class LogicModInstrRmInstruction extends ArithmeticModInstrRmInstruction {
	private final int clearFlags;

	public LogicModInstrRmInstruction() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModInstrRmInstruction(int flagMask) {
		super(flagMask);
		clearFlags = MASK_CF | MASK_OF;
	}

	public LogicModInstrRmInstruction(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
