package thennx.vm8086;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Registers8086 implements Iterable<thennx.vm8086.Registers8086.Register16> {

	private final List<Register16> registers = new ArrayList<>();

	public class Register16 extends Number {
		private short value;
		private final String name;

		public Register16(String name, short value) {
			this.value = value;
			this.name = name;
			registers.add(this);
		}

		public Register16(String name, int value) {
			this(name, (short) value);
		}

		public Register16(String name) {
			this(name, 0);
		}

		public Register16(String name, Register16 reg) {
			this(name, reg.value);
		}

		private Register16(short value) {
			this.value = value;
			this.name = "<temp>";
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String toString() {
			return String.format("%04X", value & 0xFFFF);
		}

		public <T extends Number> short add(T s) {
			value = (short) (intValue() + s.intValue());
			return value;
		}

		@Override
		public int intValue() {
			return shortValue() & 0xFFFF;
		}

		@Override
		public long longValue() {
			return this.intValue();
		}

		@Override
		public float floatValue() {
			return this.intValue();
		}

		@Override
		public double doubleValue() {
			return this.intValue();
		}

		@Override
		public short shortValue() {
			return value;
		}

		public void writeLow(int low) {
			this.value = (short) ((intValue() & 0xFF00) | (low & 0xFF));
		}

		public void writeHigh(int i) {
			this.value = (short) ((intValue() & 0xFF) | ((i * 256) & 0xFF00));
		}

		public int readLow() {
			return (intValue() & 0xFF);
		}

		public int readHigh() {
			return ((intValue() & 0xFF00) / 256);
		}

		public void write(short value) {
			this.value = value;
		}

		private Register16 getReg8Override(VM8086 vm) {
			if ((Object)this == vm.registers.SP) {
				return vm.registers.AX;
			} else if ((Object)this == vm.registers.BP) {
				return vm.registers.CX;
			} else if ((Object)this == vm.registers.SI) {
				return vm.registers.DX;
			} else if ((Object)this == vm.registers.DI) {
				return vm.registers.BX;
			}
			return null;
		}

		public short readDecoded(VM8086 vm, boolean W) {
			/* reg8 */
			if (!W) {
				Register16 override = null;

				/*
				 * this is a hacky way of making reg decoding width flag independent If W = 0,
				 * what would be SP with W = 1 becomes high byte of AX etc.
				 */
				override = getReg8Override(vm);

				/*
				 * if operand is a register with different meaning when dealing with 16 bit
				 * opcodes, use the override calculated above
				 */
				if (override != null) {
					return (short)override.readHigh();
				} else {
					/* this 8 bit register is a lower half of its 16 bit counterpart */
					return (short)this.readLow();
				}
			}
			/* reg16 */
			else {
				return this.shortValue();
			}
		}

		public void writeDecoded(VM8086 vm, boolean w, short data) {
			/* reg8 */
			if (!w) {
				Register16 override = getReg8Override(vm);

				if (override != null) {
					override.writeHigh((byte) (data & 0xFF));
				} else {
					this.writeLow((byte) (data & 0xFF));
				}
			}
			/* reg16 */
			else {
				this.write(data);
			}
		}
	}

	public Register16 createTempRegister(short value) {
		return new Register16(value);
	}

	public Register16 IP = new Register16("IP");
	public Register16 CS = new Register16("CS");
	public Register16 DS = new Register16("DS");
	public Register16 ES = new Register16("ES");
	public Register16 AX = new Register16("AX");
	public Register16 BX = new Register16("BX");
	public Register16 CX = new Register16("CX");
	public Register16 DX = new Register16("DX");
	public Register16 SS = new Register16("SS");
	public Register16 SP = new Register16("SP");
	public Register16 BP = new Register16("BP");
	public Register16 DI = new Register16("DI");
	public Register16 SI = new Register16("SI");
	public RegisterFlags FLAGS = new RegisterFlags();

	public class RegisterFlags extends Register16 {
		private static final String NAME = "FLAGS";

		public RegisterFlags() {
			super(NAME);
		}

		public RegisterFlags(short value) {
			super(NAME, value);
		}

		public RegisterFlags(int value) {
			super(NAME, value);
		}

		public RegisterFlags(Register16 reg) {
			super(NAME, reg);
		}

		@Override
		public short shortValue() {
			return (short) (super.shortValue() | 0xF000);
		}
	}

	public static final int MASK_CF = 0x0001;
	public static final int MASK_PF = 0x0004;
	public static final int MASK_AF = 0x0010;
	public static final int MASK_ZF = 0x0040;
	public static final int MASK_SF = 0x0080;
	public static final int MASK_TF = 0x0100;
	public static final int MASK_IF = 0x0200;
	public static final int MASK_DF = 0x0400;
	public static final int MASK_OF = 0x0800;

	@Override
	public Iterator<Register16> iterator() {
		return this.registers.iterator();
	}

}