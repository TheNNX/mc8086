package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.Registers8086;
import pl.pflp.vm8086.VM8086;
import pl.pflp.vm8086.Registers8086.Register16;

public class ModSegRmDecoder extends ModRegRmDecoder {
	public final static ModSegRmDecoder instance = new ModSegRmDecoder();

	@Override
	/* W = 0 is handled elsewhere, set the registers as if W = 1 */
	public Register16 decodeReg(VM8086 vm, byte reg) {
		switch (reg & 0x3) {
		case 0:
			return vm.registers.ES;
		case 1:
			return vm.registers.CS;
		case 2:
			return vm.registers.SS;
		case 3:
			return vm.registers.DS;
		default:
			System.out.println("Invalid sreg field");
		}

		return null;
	}
}
