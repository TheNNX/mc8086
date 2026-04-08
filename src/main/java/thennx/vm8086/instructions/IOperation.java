package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.AF;
import static thennx.vm8086.Registers8086.CF;
import static thennx.vm8086.Registers8086.OF;
import static thennx.vm8086.Registers8086.PF;
import static thennx.vm8086.Registers8086.SF;
import static thennx.vm8086.Registers8086.ZF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public interface IOperation {
	public final static int defaultFlagMask = ZF | OF | SF | AF | PF | CF;

	int operation(VM8086 vm, int operand1, int operand2) throws CpuException;

	int getFlagBitMask();
}
