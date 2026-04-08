package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.CF;
import static thennx.vm8086.Registers8086.OF;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

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
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, Optional<Short> segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}
}
