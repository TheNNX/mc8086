package thennx.vm8086.instructions;

import java.util.Optional;

import thennx.vm8086.VM8086;
import thennx.vm8086.Registers8086.Register16;

public class LoadPtrToSegInstruction extends ModRegRmInstruction {

	private Register16 segmentReg;

	public LoadPtrToSegInstruction(Register16 segreg) {
		this.segmentReg = segreg;
	}

	@Override
	public boolean getDirection(VM8086 vm, byte selfByte) {
		return true;
	}

	@Override
	public boolean getWidth(VM8086 vm, byte selfByte) {
		return true;
	}

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short ptrSegment) {
		ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
		int pointerOffset = (Integer)decoded.source;
		short address = vm.readMemoryShortPhysical(pointerOffset);
		short segment = vm.readMemoryShortPhysical(pointerOffset + 2);
		segmentReg.write(segment);
		decoded.writeDestination(address, vm);
	}

}
