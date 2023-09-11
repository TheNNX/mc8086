package pl.pflp.vm8086;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Registers8086 implements Iterable<pl.pflp.vm8086.Registers8086.Register16> {

	private List<Register16> registers = new ArrayList<>();

	public class Register16 extends Number {

		private short value;
		private String name;

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
			return "" + String.format("%04X", value & 0xFFFF);
		}

		public <T extends Number> short add(T s) {
			value = (short) (intValue() + s.intValue());
			return value;
		}

		@Override
		public int intValue() {
			return ((int) value) & 0xFFFF;
		}

		@Override
		public long longValue() {
			return (long) this.intValue();
		}

		@Override
		public float floatValue() {
			return (float) this.intValue();
		}

		@Override
		public double doubleValue() {
			return (double) this.intValue();
		}

		@Override
		public short shortValue() {
			return value;
		}

		public void writeLow(byte low) {
			this.value = (short) ((intValue() & 0xFF00) | (low & 0xFF));
		}

		public void writeHigh(byte high) {
			this.value = (short) ((intValue() & 0xFF) | (((int) high * 256) & 0xFF00));
		}

		public byte readLow() {
			return (byte) (intValue() & 0xFF);
		}

		public byte readHigh() {
			return (byte) ((intValue() & 0xFF00) / 256);
		}

		public void write(short value) {
			this.value = value;
		}

		public short readDecoded(VM8086 vm, boolean W) {
			/* reg8 */
			if (W == false) {
				Register16 override = null;

				/*
				 * this is a hacky way of making reg decoding width flag independent If W = 0,
				 * what would be SP with W = 1 becomes high byte of AX etc.
				 */
				if (this == vm.registers.SP) {
					override = vm.registers.AX;
				} else if (this == vm.registers.BP) {
					override = vm.registers.CX;
				} else if (this == vm.registers.SI) {
					override = vm.registers.DX;
				} else if (this == vm.registers.DI) {
					override = vm.registers.BX;
				}

				/*
				 * if operand is a register with different meaning when dealing with 16 bit
				 * opcodes, use the override calculated above
				 */
				if (override != null) {
					return override.readHigh();
				} else {
					/* this 8 bit register is a lower half of its 16 bit counterpart */
					return this.readLow();
				}
			}
			/* reg16 */
			else {
				return this.shortValue();
			}
		}

		public void writeDecoded(VM8086 vm, boolean w, short data) {
			/* reg8 */
			if (w == false) {
				Register16 override = null;

				if (this == vm.registers.SP) {
					override = vm.registers.AX;
				} else if (this == vm.registers.BP) {
					override = vm.registers.CX;
				} else if (this == vm.registers.SI) {
					override = vm.registers.DX;
				} else if (this == vm.registers.DI) {
					override = vm.registers.BX;
				}

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
	public Register16 FLAGS = new Register16("FLAGS");

	public static final int CF = 0x0001;
	public static final int PF = 0x0004;
	public static final int AF = 0x0010;
	public static final int ZF = 0x0040;
	public static final int SF = 0x0080;
	public static final int TF = 0x0100;
	public static final int IF = 0x0200;
	public static final int DF = 0x0400;
	public static final int OF = 0x0800;

	@Override
	public Iterator<Register16> iterator() {
		return this.registers.iterator();
	}

}