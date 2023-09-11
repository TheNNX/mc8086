package pl.pflp.vm8086;

import static pl.pflp.vm8086.Registers8086.DF;

public abstract class StringInstruction extends Instruction {

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfByte = bytes[0];
		boolean W = getWidth(vm, selfByte);

		short sourceSegment, destinationSegment;
		short sourceOffset, destinationOffset;

		sourceSegment = segment;
		destinationSegment = vm.registers.ES.shortValue();
		sourceOffset = vm.registers.SI.shortValue();
		destinationOffset = vm.registers.DI.shortValue();

		int moveBytes = W ? 2 : 1;
		stringOperation(vm, selfByte, sourceSegment, sourceOffset, destinationSegment, destinationOffset);

		if (0 == (vm.registers.FLAGS.intValue() & DF)) {
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
