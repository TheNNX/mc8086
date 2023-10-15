package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public abstract class ImmediateInstruction extends Instruction {

	@Override
	public void decodeAndExecute(VM8086 vm, short segment) throws CpuException {
		byte selfByte = vm.getIpByte();
		vm.registers.IP.add(1);
		byte[] bytes;

		boolean W = getWidth(vm, selfByte);
		byte[] bytesImm = readImmediateBytes(vm, W);
		bytes = new byte[bytesImm.length + 1];
		bytes[0] = selfByte;

		for (int i = 0; i < bytesImm.length; i++)
			bytes[1 + i] = bytesImm[i];

		this.execute(vm, bytes, new Object[] { bytesImm }, segment);
	}
}
