package pl.pflp.vm8086.instructions;

import static pl.pflp.vm8086.Registers8086.*;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.VM8086;

public interface IOperation {
	public final static int defaultFlagMask = ZF | OF | SF | AF | PF | CF;

	int operation(VM8086 vm, int operand1, int operand2) throws CpuException;

	int getFlagBitMask();
}
