package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.CF;
import static thennx.vm8086.Registers8086.OF;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

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
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, Optional<Short> segment) throws CpuException {
		super.execute(vm, bytes, data, segment);
		vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~clearFlags)));
	}

}
