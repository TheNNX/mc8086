package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class CountModInstrRmInstruction extends ModRegRmInstruction {

	public int getCount(VM8086 vm, byte selfByte) {
		if (0 != (selfByte & 0x02))
			if (this.getWidth(vm, selfByte))
				return vm.registers.CX.intValue();
			else
				return vm.registers.CX.readLow() & 0xFF;
		return 1;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfByte = bytes[0];
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
		int maxCount = getCount(vm, selfByte);
		int bitcount = getWidth(vm, selfByte) ? 16 : 8;
		int begin = decoded.readDestination(vm);
		int currentData = begin & vm.getBitmask(bitcount);

		for (int i = 0; i < maxCount; i++)
			currentData = onIteration(vm, i, maxCount, currentData, bitcount);

		decoded.writeDestination((short) (currentData & 0xFFFF), vm);
	}

	public abstract int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount);

}
