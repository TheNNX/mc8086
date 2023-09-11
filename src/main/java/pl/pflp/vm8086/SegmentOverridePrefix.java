package pl.pflp.vm8086;

import pl.pflp.vm8086.Registers8086.Register16;

/* FIXME: this should check if the next opcode is valid for it to be used with the prefix */
public class SegmentOverridePrefix extends Instruction {

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfbyte = bytes[0];
		byte sreg = (byte) ((selfbyte & 0x18) >> 3);
		Register16 segreg = ModSegRmDecoder.instance.decodeReg(vm, sreg);
		short segmentOverride = segreg.shortValue();
		Instruction instruction = vm.fetch();
		instruction.decodeAndExecute(vm, segmentOverride);
	}

}
