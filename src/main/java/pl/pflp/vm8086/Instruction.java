package pl.pflp.vm8086;

public abstract class Instruction {
	protected abstract void execute(VM8086 vm, byte[] bytes, Object[] data, short segment) throws CpuException;

	public void decodeAndExecute(VM8086 vm, short segment) throws CpuException {
		byte currentByte = vm.getIpByte();
		vm.registers.IP.add(1);
		execute(vm, new byte[] { currentByte }, new Object[] {}, segment);
	}

	public byte[] readImmediateBytes(VM8086 vm, boolean W) {
		if (W) {
			byte b1, b2;
			b1 = vm.getIpByte();
			vm.registers.IP.add(1);
			b2 = vm.getIpByte();
			vm.registers.IP.add(1);
			return new byte[] { b1, b2 };
		} else {
			byte b1 = vm.getIpByte();
			vm.registers.IP.add(1);
			return new byte[] { b1 };
		}
	}

	public boolean getDirection(VM8086 vm, byte selfByte) {
		return ((selfByte & 0x02) != 0);
	}

	public boolean getWidth(VM8086 vm, byte selfByte) {
		return ((selfByte & 0x01) != 0);
	}
}
