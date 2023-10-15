package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class JmpConditionalInstruction extends ImmediateInstruction {

	@Override
	public boolean getWidth(VM8086 vm, byte selfByte) {
		return false;
	}

	@Override
	protected void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException {
		byte relAddress = ((byte[]) data[0])[0];
		if (this.checkCondition(vm, bytes[0])) {
			vm.registers.IP.add((int) relAddress);
		}
	}

	public abstract boolean checkCondition(VM8086 vm, byte selfByte);

}
