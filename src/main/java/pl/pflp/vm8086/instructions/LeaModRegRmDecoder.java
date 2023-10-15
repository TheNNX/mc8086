package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.VM8086;

public class LeaModRegRmDecoder extends ModRegRmDecoder {

	public static final LeaModRegRmDecoder instance = new LeaModRegRmDecoder();

	/* basically the same as the normal version, but do not add the segment */
	public Object decodeRm(VM8086 vm, byte rm, byte mod, int displacement, short segment) {
		int result = 0;

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

		result += displacement;
		return result;
	}
}
