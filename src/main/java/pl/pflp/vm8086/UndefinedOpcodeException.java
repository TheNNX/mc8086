package pl.pflp.vm8086;

public class UndefinedOpcodeException extends CpuException {

	public UndefinedOpcodeException(VM8086 vm) {
		super(vm);
	}

	@Override
	public byte interuptVector() {
		return 6;
	}

}
