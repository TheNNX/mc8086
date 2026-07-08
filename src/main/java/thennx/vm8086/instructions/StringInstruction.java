package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_DF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public abstract class StringInstruction extends Instruction {

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		byte selfByte = bytes[0];
		boolean W = getWidth(vm, selfByte);

		short sourceSegment, destinationSegment;
		short sourceOffset, destinationOffset;

		sourceSegment = (segment != null) ? segment : vm.registers.DS.shortValue();
		destinationSegment = vm.registers.ES.shortValue();
		sourceOffset = vm.registers.SI.shortValue();
		destinationOffset = vm.registers.DI.shortValue();

		int moveBytes = W ? 2 : 1;
		stringOperation(vm, selfByte, sourceSegment, sourceOffset, destinationSegment, destinationOffset);

		if (0 == (vm.registers.FLAGS.intValue() & MASK_DF)) {
			if (doesChangeDi())
				vm.registers.DI.add(moveBytes);
			if (doesChangeSi())
				vm.registers.SI.add(moveBytes);
		} else {
			if (doesChangeDi())
				vm.registers.DI.add(-moveBytes);
			if (doesChangeSi())
				vm.registers.SI.add(-moveBytes);
		}
	}

	protected abstract boolean doesChangeDi();

	protected abstract boolean doesChangeSi();

	protected abstract void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
			short destinationSegment, short destinationOffset) throws CpuException;
}
