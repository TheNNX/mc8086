package pl.pflp.vm8086.instructions;

import static pl.pflp.vm8086.Registers8086.CF;
import static pl.pflp.vm8086.Registers8086.OF;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class LogicInstructionImmA extends ArithmeticInstructionImmA {
	private final int clearFlags;

	public LogicInstructionImmA() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = CF | OF;
	}

	public LogicInstructionImmA(int flagMask) {
		super(flagMask);
		clearFlags = CF | OF;
	}

	public LogicInstructionImmA(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
