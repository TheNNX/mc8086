package pl.pflp.vm8086;

import pl.pflp.vm8086.Registers8086.Register16;

public class LoadPtrToSegInstruction extends ModRegRmInstruction {

	private Register16 segmentReg;

	public LoadPtrToSegInstruction(Register16 segreg) {
		this.segmentReg = segreg;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short ptrSegment) {
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
		short pointerOffset = decoded.readSource(vm);
		short address = vm.readMemoryShort16(ptrSegment, pointerOffset);
		short segment = vm.readMemoryShort16(ptrSegment, (short) ((pointerOffset & 0xFFFF) + 2));
		segmentReg.write(segment);
		decoded.writeDestination(address, vm);
	}

}
