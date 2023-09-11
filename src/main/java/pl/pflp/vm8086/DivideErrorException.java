package pl.pflp.vm8086;

public class DivideErrorException extends CpuException {

	private short ip;
	private short cs;

	public DivideErrorException(VM8086 vm, short cs, short ip) {
		super(vm);
		this.cs = cs;
		this.ip = ip;
	}

	@Override
	public byte interuptVector() {
		return 0;
	}

	@Override
	protected short getIp() {
		return ip;
	}

	@Override
	protected short getCs() {
		return cs;
	}
}
