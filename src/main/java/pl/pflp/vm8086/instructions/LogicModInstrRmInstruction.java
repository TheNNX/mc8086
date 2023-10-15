package pl.pflp.vm8086.instructions;

import static pl.pflp.vm8086.Registers8086.CF;
import static pl.pflp.vm8086.Registers8086.OF;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class LogicModInstrRmInstruction extends ArithmeticModInstrRmInstruction {
	private final int clearFlags;

	public LogicModInstrRmInstruction() {
		super(LogicModRegRmInstruction.defaultLogicFlagMask);
		clearFlags = CF | OF;
	}

	public LogicModInstrRmInstruction(int flagMask) {
		super(flagMask);
		clearFlags = CF | OF;
	}

	public LogicModInstrRmInstruction(int flagMask, int forceClearMask) {
		super(flagMask);
		clearFlags = forceClearMask;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
