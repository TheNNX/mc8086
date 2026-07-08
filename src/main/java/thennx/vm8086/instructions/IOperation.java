package thennx.vm8086.instructions;

import static thennx.vm8086.Registers8086.MASK_AF;
import static thennx.vm8086.Registers8086.MASK_CF;
import static thennx.vm8086.Registers8086.MASK_OF;
import static thennx.vm8086.Registers8086.MASK_PF;
import static thennx.vm8086.Registers8086.MASK_SF;
import static thennx.vm8086.Registers8086.MASK_ZF;

import thennx.vm8086.CpuException;
import thennx.vm8086.VM8086;

public interface IOperation {
	public final static int defaultFlagMask = MASK_ZF | MASK_OF | MASK_SF | MASK_AF | MASK_PF | MASK_CF;

	int operation(VM8086 vm, int operand1, int operand2) throws CpuException;

	int getFlagBitMask();
}
