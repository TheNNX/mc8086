package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.Registers8086;
import pl.pflp.vm8086.VM8086;
import pl.pflp.vm8086.Registers8086.Register16;

public class ModRegRmDecoder {
	public final static ModRegRmDecoder instance = new ModRegRmDecoder();

	/* W = 0 is handled elsewhere, set the registers as if W = 1 */
	public Register16 decodeReg(VM8086 vm, byte reg) {
		switch (reg) {
		case 0:
			return vm.registers.AX;
		case 1:
			return vm.registers.CX;
		case 2:
			return vm.registers.DX;
		case 3:
			return vm.registers.BX;
		case 4:
			return vm.registers.SP;
		case 5:
			return vm.registers.BP;
		case 6:
			return vm.registers.SI;
		case 7:
			return vm.registers.DI;
		default:
			System.out.println("Invalid reg field");
		}

		return null;
	}

	public Object decodeRm(VM8086 vm, byte rm, byte mod, int displacement, short segment) {
		int result = 0;

		if (mod == 3)
			return instance.decodeReg(vm, rm);

		switch (rm) {
		case 0:
			result = vm.registers.BX.intValue() + vm.registers.SI.intValue();
			break;
		case 1:
			result = vm.registers.BX.intValue() + vm.registers.DI.intValue();
			break;
		case 2:
			result = vm.registers.BP.intValue() + vm.registers.SI.intValue();
			break;
		case 3:
			result = vm.registers.BP.intValue() + vm.registers.DI.intValue();
			break;
		case 4:
			result = vm.registers.SI.intValue();
			break;
		case 5:
			result = vm.registers.DI.intValue();
			break;
		case 6:
			if (mod != 0) {
				result = vm.registers.BP.intValue();
			}
			break;
		case 7:
			result = vm.registers.BX.intValue();
			break;
		default:
			System.out.println("Invalid r/m field");
			return 0;
		}

		result += displacement + (((int) segment) & 0xFFFF) * 16;
		return result;
	}
}
