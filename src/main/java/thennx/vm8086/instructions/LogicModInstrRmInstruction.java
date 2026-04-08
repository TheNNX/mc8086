package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.CF;
import static thennx.vm8086.Registers8086.OF;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

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
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, Optional<Short> segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
