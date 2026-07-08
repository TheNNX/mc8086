package thennx.vm8086.instructions;

import java.util.Optional;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public abstract class JmpConditionalInstruction extends ImmediateInstruction {

	@Override
	public boolean getWidth(VM8086 vm, byte selfByte) {
		return false;
	}

	@Override
    public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
		byte relAddress = ((byte[]) data[0])[0];
		if (this.checkCondition(vm, bytes[0])) {
			vm.registers.IP.add((int) relAddress);
		}
	}

	public abstract boolean checkCondition(VM8086 vm, byte selfByte);

}
