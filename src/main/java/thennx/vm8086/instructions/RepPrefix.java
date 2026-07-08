package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_ZF;

import thennx.vm8086.CpuException;
import thennx.vm8086.UndefinedOpcodeException;
import thennx.vm8086.VM8086;

public class RepPrefix extends Instruction {

	private final int[] repPrefixableBytes = { 0xA4, 0xA5, 0xAA, 0xAB, 0xAC, 0xAD };
	private final int[] repxePrefixableBytes = { 0xA6, 0xA7, 0xAE, 0xAF };

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		byte selfByte = bytes[0];
		byte nextInstructionByte = vm.getIpByte();

		boolean repxe = false;
		boolean rep = false;

		for (int i : repxePrefixableBytes) {
			if (i == (nextInstructionByte & 0xFF))
			{
				repxe = true;
				rep = true;
			}
		}

		for (int i : repPrefixableBytes) {
			if (i == (nextInstructionByte & 0xFF))
				rep = true;
		}

		if (!repxe && !rep)
			throw new UndefinedOpcodeException(vm);

		/*
		 * this bit can only be set when it is a REPNE, it cannot be set for normal REP
		 */
		boolean desiredEquality = (selfByte & 0x01) != 0;

		short currentIp = vm.registers.IP.shortValue();

		if (vm.registers.CX.intValue() == 0) {
			/* FIXME */
			vm.registers.IP.add(1);
			return;
		}
		
		//System.out.println("REP Count " + vm.registers.CX.intValue() + " ES:DI="+vm.registers.ES+":"+vm.registers.DI+ " DS:SI="
		//		+vm.registers.DS+":"+vm.registers.SI);
		while (vm.registers.CX.intValue() != 0) {
			/* TODO: check for pending interrupts */

			/* restore IP in case the instruction changed it */
			vm.registers.IP.write(currentIp);

			/* decode and execute the incoming instruction */
			Instruction instruction = vm.decodeTable[nextInstructionByte & 0xFF];
			instruction.decodeAndExecute(vm, segment);

			vm.registers.CX.add(-1);
			/* if the equality match fails for REP(N)E, break */
			if (repxe) {
				boolean actualEquality = 0 == (vm.registers.FLAGS.intValue() & MASK_ZF);

				if (desiredEquality == actualEquality) {
					break;
				}
			}
		}
	}

}
