package pl.pflp.vm8086.instructions;

import pl.pflp.vm8086.CpuException;
import pl.pflp.vm8086.Registers8086;
import pl.pflp.vm8086.VM8086;
import pl.pflp.vm8086.Registers8086.Register16;

public abstract class ModRegRmInstruction extends Instruction {
	private ModRegRmDecoder decoder = ModRegRmDecoder.instance;

	public class ModRegRmDecoded {

		public ModRegRmDecoded(byte modRegRm, byte selfByte, VM8086 vm, boolean D, boolean W, short segmentOverride,
				boolean regAsPartOfInstruction) {
			byte mod = (byte) ((modRegRm & 0xC0) / 64);
			byte reg = (byte) ((modRegRm & 0x38) / 8);
			byte rm = (byte) (modRegRm & 0x07);

			/* copy the direction and width flags */
			this.D = D;
			this.W = W;

			int displacement = 0;

			switch (mod) {
			case 0:
				if (rm == 6) {
					/* if rm == 110, displacement is an address, unsigned */
					displacement = vm.readMemoryShort16(vm.registers.CS.shortValue(), vm.registers.IP.shortValue())
							& 0xFFFF;
					vm.registers.IP.add(2);
				}
				/* else, no displacement */
				break;
			case 1:
				/* 8 bits sign extended to 16 bits */
				displacement = vm.readMemoryShort16(vm.registers.CS.shortValue(), vm.registers.IP.shortValue());
				vm.registers.IP.add(1);
				break;
			case 2:
				/* 16 bit displacement, unsigned */
				displacement = vm.readMemoryShort16(vm.registers.CS.shortValue(), vm.registers.IP.shortValue())
						& 0xFFFF;
				vm.registers.IP.add(2);
				break;
			case 3:
				break;
			default:
				System.out.println("Invalid mod-reg-rm encoding\n");
			}

			Object operand1, operand2;

			operand2 = decoder.decodeRm(vm, rm, mod, displacement, segmentOverride);

			/*
			 * if the reg field is a part of instruction encoding itself, and not the
			 * ModRegR/M field, ignore it - the instruction is a one operand one and
			 * operand1 can be equal operand2
			 */
			if (regAsPartOfInstruction == false)
				operand1 = decoder.decodeReg(vm, reg);
			else
				operand1 = operand2;

			if (D) {
				this.source = operand2;
				this.destination = operand1;
			} else {
				this.destination = operand2;
				this.source = operand1;
			}
		}

		public boolean W;
		public boolean D;

		public Object source;
		public Object destination;

		public void writeOperand(short data, VM8086 vm, Object operand) {
			byte data8 = (byte) (data & 0xFF);

			if (operand instanceof Integer) {
				/* address to a word */
				if (this.W) {
					vm.writeMemoryShortPhysical((Integer) operand, data);
				}
				/* address to a byte */
				else {
					vm.writeMemoryBytePhysical((Integer) operand, data8);
				}
			} else if (operand instanceof Register16) {
				((Register16) operand).writeDecoded(vm, this.W, data);
			} else {
				System.out.println("Invalid destination");
			}
		}

		public short readOperand(VM8086 vm, Object operand) {
			if (operand instanceof Integer) {
				/* address to a word */
				if (this.W) {
					return vm.readMemoryShortPhysical((Integer) operand);
				}
				/* address to a byte */
				else {
					return vm.readMemoryBytePhysical((Integer) operand);
				}
			} else if (operand instanceof Register16) {
				return ((Register16) operand).readDecoded(vm, this.W);
			} else {
				System.out.println("Invalid operand");
				return 0;
			}
		}

		public short readSource(VM8086 vm) {
			return readOperand(vm, this.source);
		}

		public short readDestination(VM8086 vm) {
			return readOperand(vm, this.destination);
		}

		public void writeSource(short data, VM8086 vm) {
			writeOperand(data, vm, source);
		}

		public void writeDestination(short data, VM8086 vm) {
			writeOperand(data, vm, destination);
		}

		public int readEffectiveAddressOfSource() {
			if (this.source instanceof Integer) {
				return (int) this.source;
			} else {
				System.out.println("Invalid lea encoding");
			}
			return 0;
		}

	}

	public ModRegRmInstruction() {
	}

	public ModRegRmInstruction(ModRegRmDecoder decoder) {
		this.decoder = decoder;
	}

	protected Object[] createExecutionArgs(VM8086 vm, byte selfByte, ModRegRmDecoded decoded) {
		return new Object[] { decoded };
	}

	@Override
	public void decodeAndExecute(VM8086 vm, short segment) throws CpuException {
		decode(vm, segment, false);
	}

	public void decode(VM8086 vm, short segment, boolean ignoreRegInModRegRmTranslation) throws CpuException {
		byte selfByte, modRegRm;

		/* read the self byte and the ModRegR/M */
		selfByte = vm.readMemoryByte16(vm.registers.CS.shortValue(), vm.registers.IP.shortValue());
		modRegRm = vm.readMemoryByte16(vm.registers.CS.shortValue(), (short) (vm.registers.IP.intValue() + 1));

		/* skip the selfByte and ModRegR/M */
		vm.registers.IP.add(2);

		/* get the direction and width */
		boolean D, W;
		D = getDirection(vm, selfByte);
		W = getWidth(vm, selfByte);

		/* decode ModRegR/M */
		ModRegRmDecoded decoded = new ModRegRmDecoded(modRegRm, selfByte, vm, D, W, segment,
				ignoreRegInModRegRmTranslation);
		Object[] args = createExecutionArgs(vm, selfByte, decoded);

		/* execute the instruction */
		execute(vm, new byte[] { selfByte, modRegRm }, args, segment);
	}
}
