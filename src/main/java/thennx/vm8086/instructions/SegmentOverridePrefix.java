package thennx.vm8086.instructions;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;
import thennx.vm8086.Registers8086.Register16;

/* FIXME: this should check if the next opcode is valid for it to be used with the prefix */
public class SegmentOverridePrefix extends Instruction {

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		byte selfbyte = bytes[0];
		byte sreg = (byte) ((selfbyte & 0x18) >> 3);
		Register16 segreg = ModSegRmDecoder.instance.decodeReg(vm, sreg);
		short segmentOverride = segreg.shortValue();
		Instruction instruction = vm.fetch();
		instruction.decodeAndExecute(vm, segmentOverride);
	}

}
