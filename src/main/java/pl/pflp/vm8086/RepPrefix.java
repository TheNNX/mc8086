package pl.pflp.vm8086;

import static pl.pflp.vm8086.Registers8086.ZF;

public class RepPrefix extends Instruction {

	private final int[] repPrefixableBytes = { 0xA4, 0xA5, 0xAA, 0xAB, 0xAC, 0xAD };
	private final int[] repxePrefixableBytes = { 0xA6, 0xA7, 0xAE, 0xAF };

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte selfByte = bytes[0];
		byte nextInstructionByte = vm.getIpByte();

		boolean repxe = false;
		boolean rep = false;

		for (int i : repxePrefixableBytes) {
			if (i == (nextInstructionByte & 0xFF))
				repxe = true;
		}

		for (int i : repPrefixableBytes) {
			if (i == (nextInstructionByte & 0xFF))
				rep = true;
		}

		/*
		 * if the next instruction cannot take rep(n)(e) prefix or can (somefuckinghow)
		 * take both
		 */
		if (repxe == rep)
			throw new UndefinedOpcodeException(vm);

		/*
		 * this bit can only be set when it is a REPNE, it cannot be set for normal REP
		 */
		boolean desiredEquality = (selfByte & 0x01) != 0;
		if (!desiredEquality && rep) {
			throw new UndefinedOpcodeException(vm);
		}

		short currentIp = vm.registers.IP.shortValue();

		while (vm.registers.CX.intValue() != 0) {
			/* TODO: check for pending interrupts */

			/* restore IP in case the instruction changed it */
			vm.registers.IP.write(currentIp);

			/* decode and execute the incoming instruction */
			Instruction instruction = vm.decodeTable[nextInstructionByte & 0xFF];
			instruction.decodeAndExecute(vm, segment);

			/* if the equality match fails for REP(N)E, break */
			if (repxe) {
				boolean actualEquality = 0 == (vm.registers.FLAGS.intValue() & ZF);

				if (desiredEquality == actualEquality) {
					break;
				}
			}

			vm.registers.CX.add(-1);
		}
	}

}
