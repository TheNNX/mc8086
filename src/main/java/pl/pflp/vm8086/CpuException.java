package pl.pflp.vm8086;

public abstract class CpuException extends Exception {
	public abstract byte interuptVector();

	protected final VM8086 vm;

	public CpuException(VM8086 vm) {
		this.vm = vm;
	}

	public void startInterupt() {
		vm.startInterrupt(this.interuptVector(), this.getCs(), this.getIp(), this.getFlags());
	}

	protected short getFlags() {
		return vm.registers.FLAGS.shortValue();
	}

	protected short getIp() {
		return vm.registers.IP.shortValue();
	}

	protected short getCs() {
		return vm.registers.CS.shortValue();
	}
}
