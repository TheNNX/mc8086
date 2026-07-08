package thennx.vm8086.instructions;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.UndefinedOpcodeException;
import thennx.vm8086.VM8086;

public class ModInstrRmInstructions extends ModRegRmInstruction {

	public ModRegRmInstruction[] subinstructions = null;

	public ModInstrRmInstructions(ModRegRmInstruction[] subinstructions) {
		this.subinstructions = subinstructions;
	}

	@Override
    public final void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
	}

	@Override
	public void decodeAndExecute(VM8086 vm, Short segment) throws CpuException {
		/* read the reg field */
		byte modRegRm = vm.readMemoryByte16(vm.registers.CS.shortValue(), (short) (vm.registers.IP.intValue() + 1));
		byte reg = (byte) ((modRegRm & 0x38) / 8);

		if (reg >= this.subinstructions.length)
			throw new UndefinedOpcodeException(vm);
			
		/* select subinstruction by the value of reg field in ModRegR/M */
		ModRegRmInstruction subinstruction = this.subinstructions[reg];

		if (subinstruction == null)
			throw new UndefinedOpcodeException(vm);

		/* decode the subinstruction instead */
		subinstruction.decode(vm, segment, true);
	}

}
