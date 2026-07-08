package thennx.vm8086;

import static thennx.vm8086.Registers8086.*;

import java.io.IOException;
import java.util.ArrayList;

import thennx.vm8086.Registers8086.Register16;
import thennx.vm8086.devices.*;
import thennx.vm8086.devices.IInterruptSource.InterruptRequest;
import thennx.vm8086.instructions.ArithmeticInstructionImmA;
import thennx.vm8086.instructions.ArithmeticModInstrRmInstruction;
import thennx.vm8086.instructions.ArithmeticModRegRmInstruction;
import thennx.vm8086.instructions.ArithmeticModRegRmInstructionImmReg;
import thennx.vm8086.instructions.CountModInstrRmInstruction;
import thennx.vm8086.instructions.DivMulInstruction;
import thennx.vm8086.instructions.IOperation;
import thennx.vm8086.instructions.ImmediateInstruction;
import thennx.vm8086.instructions.Instruction;
import thennx.vm8086.instructions.JmpConditionalInstruction;
import thennx.vm8086.instructions.JmpFlagInstruction;
import thennx.vm8086.instructions.LeaModRegRmDecoder;
import thennx.vm8086.instructions.LoadPtrToSegInstruction;
import thennx.vm8086.instructions.LogicInstructionImmA;
import thennx.vm8086.instructions.LogicModInstrRmInstruction;
import thennx.vm8086.instructions.LogicModRegRmInstruction;
import thennx.vm8086.instructions.LogicModRegRmInstructionImmReg;
import thennx.vm8086.instructions.ModInstrRmInstructions;
import thennx.vm8086.instructions.ModRegRmDecoder;
import thennx.vm8086.instructions.ModRegRmInstruction;
import thennx.vm8086.instructions.ModSegRmDecoder;
import thennx.vm8086.instructions.RepPrefix;
import thennx.vm8086.instructions.SegmentOverridePrefix;
import thennx.vm8086.instructions.StringInstruction;

public class VM8086 implements IVirtualMachine {

	public class IvtEntry {
		public IvtEntry(byte[] bytes) {
			assert(bytes.length == 4);

			byte[] firstHalf = { bytes[0], bytes[1] };
			byte[] secondHalf = { bytes[2], bytes[3] };

			this.Cs = shortFromBytes(secondHalf);
			this.Ip = shortFromBytes(firstHalf);
		}

		public final short Cs;
		public final short Ip;
	}

	public int executeCount = 0;
	private boolean halted = false;
	private boolean running = false;
	private boolean ipLogging = false;

	public Registers8086 registers;
	private SimplifiedKeyboardController keyboardController = new SimplifiedKeyboardController(this);
	public IMemoryBank[] memory = new IMemoryBank[1024 * 1024 / IMemoryBank.BANK_SIZE];
	private BarebonesATAChannel primaryIde = new BarebonesATAChannel((short) 0x1F0, (short) 0x3F6);
	private BarebonesATAChannel secondaryIde = new BarebonesATAChannel((short) 0x170, (short) 0x376);

	private PIC8259 masterPic = PIC8259.createMaster(this);
	private PIC8259 slavePic = PIC8259.createSlave(this);
	private PIT8254 pit = new PIT8254();

	private final ArrayList<IPortSpaceDevice> portSpaceDevices = new ArrayList<IPortSpaceDevice>();
	private final ArrayList<IStateful> statefulDevices = new ArrayList<IStateful>();
	public byte[] ioPorts = new byte[65536];

	private void initCpu() {
		registers = new Registers8086();
		initializeDecodeTable();

		portSpaceDevices.add(masterPic);
		portSpaceDevices.add(slavePic);
		portSpaceDevices.add(keyboardController);
		portSpaceDevices.add(pit);

		masterPic.connect(0, pit);
		masterPic.connect(1, keyboardController);

		if (primaryIde != null) {
			portSpaceDevices.add(primaryIde);
		}
		if (secondaryIde != null) {
			portSpaceDevices.add(secondaryIde);
		}

		registers.IP.write((short) 0x0000);
		registers.CS.write((short) 0xFFFF);
		registers.DX.write((short) 0x0000);
		registers.AX.write((short) 0);
		registers.CX.write((short) 0);

		running = true;
	}

	public void addDebugPorts() {
		portSpaceDevices.add(new DebugDataPort((short) 0xE8));
		portSpaceDevices.add(new DebugNumberPort((short) 0xE9));
	}

	public VM8086(int memorySize, byte[] bios) {
		running = false;

		memory[memory.length - 1] = new PhysicalMemoryBank(true, bios);

		for (int i = 0; i < memorySize / IMemoryBank.BANK_SIZE; i++) {
			if (memory[i] == null) {
				memory[i] = new PhysicalMemoryBank();
			}
		}

		initCpu();
	}

	private BarebonesATAChannel getIdeChannel(int index) {
		if (index == 0)
			return primaryIde;
		else
			return secondaryIde;
	}

	public void attachIdeDevice(int index, boolean slave, IBlockDevice device) {
		BarebonesATAChannel channel = this.getIdeChannel(index);
		channel.addDevice(device, slave);
	}

	@Override
	public byte readMemoryBytePhysical(int physicalAddress) {
		return memory[physicalAddress / IMemoryBank.BANK_SIZE].getByte(physicalAddress % IMemoryBank.BANK_SIZE);
	}

	@Override
	public short readMemoryShortPhysical(int physicalAddress) {
		/* java uses big endian */
		byte lsb = readMemoryBytePhysical(physicalAddress);
		byte msb = readMemoryBytePhysical(physicalAddress + 1);
		return (short) ((int) (msb & 0xFF) * 256 + (int) (lsb & 0xFF));
	}

	public int physicalAddressFromSegmentOffset(short segment, short offset) {
		int physicalAddress = ((int) (segment & 0xFFFF)) * 16 + (int) (offset & 0xFFFF);
		return physicalAddress & 0xFFFFF;
	}

	public byte readMemoryByte16(short segment, short offset) {
		int physicalAddress = physicalAddressFromSegmentOffset(segment, offset);
		return readMemoryBytePhysical(physicalAddress);
	}

	public short readMemoryShort16(short segment, short offset) {
		int physicalAddress = physicalAddressFromSegmentOffset(segment, offset);
		return readMemoryShortPhysical(physicalAddress);
	}

	@Override
	public void writeMemoryBytePhysical(int physicalAddress, byte b) {
		memory[physicalAddress / IMemoryBank.BANK_SIZE].setByte(physicalAddress % IMemoryBank.BANK_SIZE, b);;
	}

	@Override
	public void writeMemoryShortPhysical(int physicalAddress, short w) {
		writeMemoryBytePhysical(physicalAddress, (byte) (w & 0xFF));
		writeMemoryBytePhysical(physicalAddress + 1, (byte) ((w & 0xFF00) / 256));
	}

	@Override
	public long getFrequencyHz() {
		return 4770000;
	}

	public void writeMemoryShort16(short segment, short offset, short w) {
		int physicalAddress = physicalAddressFromSegmentOffset(segment, offset);
		writeMemoryShortPhysical(physicalAddress, w);
	}

	public void writeMemoryByte16(short segment, short offset, byte b) {
		int physicalAddress = physicalAddressFromSegmentOffset(segment, offset);
		writeMemoryBytePhysical(physicalAddress, b);
	}

	public void pushToStack(short data) {
		short sp = registers.SP.add(-2);
		int effectiveAddress = physicalAddressFromSegmentOffset(registers.SS.shortValue(), sp);
		writeMemoryShortPhysical(effectiveAddress, data);
	}

	public short popFromStack() {
		short sp = registers.SP.shortValue();
		short result = readMemoryShort16(registers.SS.shortValue(), sp);
		registers.SP.add(2);
		return result;
	}

	public short shortFromBytes(byte[] immediateBytes) {
		if (immediateBytes.length == 2) {
			return (short) (((immediateBytes[0] & 0xFF) | ((((int) immediateBytes[1]) * 256)) & 0xFF00) & 0xFFFF);
		} else {
			return (short) (immediateBytes[0] & 0xFF);
		}
	}

	public Instruction[] decodeTable = new Instruction[256];

	protected void adjustFlags(int clearMask, int setMask) {
		int cleared = registers.FLAGS.intValue() & ~clearMask;
		int set = cleared | setMask;
		registers.FLAGS.write((short) (set & 0xFFFF));
	}

	protected boolean shouldSetOF(int num1, int num2, int result, int bitnumber) {
		boolean resultMsBit = 0 != (result & (1 << (bitnumber - 1)));
		boolean num1MsBit = 0 != (num1 & (1 << (bitnumber - 1)));
		boolean num2MsBit = 0 != (num2 & (1 << (bitnumber - 1)));

		if (num1MsBit != num2MsBit)
			return false;

		return resultMsBit != num1MsBit;
	}

	protected int adc(int operand1, int operand2) {
		return operand1 + operand2 + (((registers.FLAGS.shortValue() & MASK_CF) != 0) ? 1 : 0);
	}

	protected int sbb(int operand1, int operand2) {
		return operand1 - (operand2 + (((registers.FLAGS.shortValue() & MASK_CF) != 0) ? 1 : 0));
	}

	public int executeOperation(IOperation o, int op1, int op2, int bitnumber) throws CpuException {
		int result = o.operation(this, op1 & 0xFFFF, op2 & 0xFFFF);

		/* Calculate the main arithmetic flags */
		int setMask = calculateFlagSetMask(result, (short) (result & getBitmask(bitnumber)), bitnumber);

		/*
		 * If the size of operation is 8 bits, and there was an overflow between the
		 * nibbles, set the AF this might be wrong, but I hope the Aux Carry Flag is
		 * obscure enough for no one to test this.
		 */
		if (bitnumber == 8 && ((o.operation(this, op1 & 0xF, op2 & 0xF) & 0xFF) > 0xF))
			setMask |= MASK_AF;

		int selfApply1 = o.operation(this, op1, 0);
		int selfApply2 = o.operation(this, 0, op2);
		if (shouldSetOF(selfApply1, selfApply2, result, bitnumber))
			setMask |= MASK_OF;

		adjustFlags(o.getFlagBitMask(), setMask & o.getFlagBitMask());
		return result;
	}

	private void initializeDecodeTable() {
		int incFlagMask = MASK_ZF | MASK_OF | MASK_SF | MASK_AF | MASK_PF;
		final IOperation incAddition = new IOperation() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 + operand2;
			}

			@Override
			public int getFlagBitMask() {
				return incFlagMask;
			}
		};

		final IOperation comparisonOperator = new IOperation() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}

			@Override
			public int getFlagBitMask() {
				return IOperation.defaultFlagMask;
			}
		};

		Instruction hlt = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				vm.halted = true;
				System.err.printf("%d: HLT - %X:%X\n", vm.executeCount, vm.registers.CS.intValue(), vm.registers.IP.shortValue());
			}
		};
		decodeTable[0xF4] = hlt;

		ModRegRmInstruction mov = new ModRegRmInstruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				short data1 = decoded.readSource(vm);
				decoded.writeDestination(data1, vm);
			}
		};

		decodeTable[0x88] = mov;
		decodeTable[0x89] = mov;
		decodeTable[0x8A] = mov;
		decodeTable[0x8B] = mov;

		ModRegRmInstruction movImmRmSub = new ModRegRmInstruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				boolean W = decoded.W;
				byte[] immediateBytes = this.readImmediateBytes(vm, W);
				short immediate = shortFromBytes(immediateBytes);
				decoded.writeDestination(immediate, vm);
			}
		};

		ModInstrRmInstructions movImmRm = new ModInstrRmInstructions(new ModRegRmInstruction[] { movImmRmSub });

		decodeTable[0xC6] = movImmRm;
		decodeTable[0xC7] = movImmRm;

		ImmediateInstruction movImm = new ImmediateInstruction() {
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return (selfByte & 0x8) != 0;
			}

			@Override
			public boolean getDirection(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				boolean W = getWidth(vm, bytes[0]);
				byte reg = (byte) (bytes[0] & 0x7);
				Registers8086.Register16 dst = ModRegRmDecoder.instance.decodeReg(vm, reg);
				short immediate = shortFromBytes((byte[]) data[0]);
				dst.writeDecoded(vm, W, immediate);
			}
		};

		for (int i = 0; i < 16; i++)
			decodeTable[0xB0 + i] = movImm;

		Instruction movMemAcc = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte[] immediateBytes = readImmediateBytes(vm, true);
				short immediate = shortFromBytes(immediateBytes);
				/* accumulator to mem */
				if (getDirection(vm, bytes[0])) {
					if (getWidth(vm, bytes[0])) {
						vm.writeMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()), immediate,
								vm.registers.AX.shortValue());
					} else {
						vm.writeMemoryByte16((segment != null) ? segment : (vm.registers.DS.shortValue()), immediate,
								(byte) vm.registers.AX.readLow());
					}
				}
				/* mem to accumulator */
				else {
					if (getWidth(vm, bytes[0])) {
						vm.registers.AX
								.write(vm.readMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()), immediate));
					} else {
						vm.registers.AX
								.writeLow(vm.readMemoryByte16((segment != null) ? segment : (vm.registers.DS.shortValue()), immediate));
					}
				}
			}
		};

		decodeTable[0xA0] = movMemAcc;
		decodeTable[0xA1] = movMemAcc;
		decodeTable[0xA2] = movMemAcc;
		decodeTable[0xA3] = movMemAcc;

		ModRegRmInstruction movSreg = new ModRegRmInstruction(ModSegRmDecoder.instance) {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				short data1 = decoded.readSource(vm);
				decoded.writeDestination(data1, vm);
			}
		};

		decodeTable[0x8C] = movSreg;
		decodeTable[0x8E] = movSreg;

		ModRegRmInstruction push = new ModRegRmInstruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				if (getWidth(vm, bytes[0]) == false) {
					throw new UndefinedOpcodeException(vm);
				}
				vm.pushToStack(decoded.readSource(vm));
			}
		};

		ModRegRmInstruction inc = new ModRegRmInstruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				boolean w = getWidth(vm, bytes[0]);
				int bitnumber = w ? 16 : 8;
				int src = decoded.readSource(vm) & 0xFFFF;

				int result = vm.executeOperation(incAddition, src, 1, bitnumber);

				decoded.writeDestination((short) (result & 0xFFFF), vm);
			}
		};

		ModRegRmInstruction dec = new ModRegRmInstruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				boolean w = getWidth(vm, bytes[0]);
				int bitnumber = w ? 16 : 8;
				int src = decoded.readSource(vm) & 0xFFFF;

				int result = vm.executeOperation(incAddition, src, -1, bitnumber);

				decoded.writeDestination((short) (result & 0xFFFF), vm);
			}
		};

		ModRegRmInstruction callShortIndir = new ModRegRmInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				short newIp = decoded.readSource(vm);
				pushToStack(vm.registers.IP.shortValue());
				vm.registers.IP.write(newIp);
			}
		};

		ModRegRmInstruction jmpShortIndir = new ModRegRmInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				short newIp = decoded.readSource(vm);
				vm.registers.IP.write(newIp);
			}
		};

		ModRegRmInstruction callFarIndir = new ModRegRmInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				int offsetToFarPtr;
				short newCs, newIp;

				/* FIXME: This can't be right... */
				if (decoded.source instanceof Integer) {
					offsetToFarPtr = ((Integer) decoded.source);
					newCs = vm.readMemoryShortPhysical(offsetToFarPtr + 2);
					newIp = vm.readMemoryShortPhysical(offsetToFarPtr);
				} else {
					offsetToFarPtr = decoded.readSource(vm);
					newCs = vm.readMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()),
							(short) (offsetToFarPtr + 2));
					newIp = vm.readMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()),
							(short) (offsetToFarPtr));
				}

				pushToStack(vm.registers.CS.shortValue());
				pushToStack(vm.registers.IP.shortValue());

				vm.registers.IP.write(newIp);
				vm.registers.CS.write(newCs);
			}
		};

		ModRegRmInstruction jmpFarIndir = new ModRegRmInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				int offsetToFarPtr;
				short newCs, newIp;

				/* FIXME: This can't be right... */
				if (decoded.source instanceof Integer) {
					offsetToFarPtr = ((Integer) decoded.source);
					newCs = vm.readMemoryShortPhysical(offsetToFarPtr + 2);
					newIp = vm.readMemoryShortPhysical(offsetToFarPtr);
				} else {
					offsetToFarPtr = decoded.readSource(vm);
					newCs = vm.readMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()),
							(short) (offsetToFarPtr + 2));
					newIp = vm.readMemoryShort16((segment != null) ? segment : (vm.registers.DS.shortValue()),
							(short) (offsetToFarPtr));
				}
				vm.registers.CS.write(newCs);
				vm.registers.IP.write(newIp);
			}
		};

		ModInstrRmInstructions misc = new ModInstrRmInstructions(
				new ModRegRmInstruction[] { inc, dec, callShortIndir, callFarIndir, jmpShortIndir, jmpFarIndir, push });

		decodeTable[0xFE] = misc;
		decodeTable[0xFF] = misc;

		Instruction pushOrPopReg = new Instruction() {

			@Override
			public boolean getDirection(VM8086 vm, byte selfByte) {
				return (selfByte & 0x08) == 0;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte selfByte = bytes[0];
				byte reg = (byte) (selfByte & 0x7);
				Register16 operand = ModRegRmDecoder.instance.decodeReg(vm, reg);
				if (this.getDirection(vm, selfByte))
					pushToStack(operand.shortValue());
				else
					operand.write(popFromStack());
			}
		};

		for (int i = 0; i < 8; i++) {
			decodeTable[0x50 + i] = pushOrPopReg;
			decodeTable[0x58 + i] = pushOrPopReg;
		}

		Instruction pushOrPopSreg = new Instruction() {

			@Override
			public boolean getDirection(VM8086 vm, byte selfByte) {
				return (selfByte & 0x01) == 0;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte selfByte = bytes[0];
				byte reg = (byte) ((selfByte & 0x18) >> 3);

				Register16 operand = ModSegRmDecoder.instance.decodeReg(vm, reg);

				if (this.getDirection(vm, selfByte))
					pushToStack(operand.shortValue());
				else
					operand.write(popFromStack());
			}
		};

		decodeTable[0x06] = pushOrPopSreg;
		decodeTable[0x0E] = pushOrPopSreg;
		decodeTable[0x16] = pushOrPopSreg;
		decodeTable[0x1E] = pushOrPopSreg;
		decodeTable[0x07] = pushOrPopSreg;
		/*
		 * According to https://en.wikipedia.org/wiki/X86_instruction_listings:
		 * "POP CS (opcode 0x0F) works only on 8086/8088. Later CPUs use 0x0F as a prefix for newer instructions."
		 */
		decodeTable[0x0F] = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) throws CpuException {
				byte subopcode = vm.getIpByte();
				if (subopcode == 0x06) {
					decodeTable[0xCF].execute(vm, bytes, data, segment);
					//logState("0F 06");
				}
			}
		};
		decodeTable[0x17] = pushOrPopSreg;
		decodeTable[0x1F] = pushOrPopSreg;

		ModInstrRmInstructions pop = new ModInstrRmInstructions(new ModRegRmInstruction[] { new ModRegRmInstruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				decoded.writeDestination(vm.popFromStack(), vm);
			}
		} }) {
		};

		decodeTable[0x8F] = pop;

		ModRegRmInstruction xchg = new ModRegRmInstruction() {

			/* not important */
			@Override
			public boolean getDirection(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				short data1 = decoded.readSource(vm);
				short data2 = decoded.readDestination(vm);

				decoded.writeDestination(data1, vm);
				decoded.writeSource(data2, vm);
			}
		};

		decodeTable[0x86] = xchg;
		decodeTable[0x87] = xchg;

		Instruction xchga = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte selfByte = bytes[0];
				byte reg = (byte) (selfByte & 0x07);
				Register16 regToXchg = ModRegRmDecoder.instance.decodeReg(vm, reg);
				short temp = regToXchg.shortValue();
				regToXchg.write(vm.registers.AX.shortValue());
				vm.registers.AX.write(temp);
			}
		};

		for (int i = 0; i < 8; i++)
			decodeTable[0x90 + i] = xchga;

		ImmediateInstruction inOrOutImm = new ImmediateInstruction() {

			/* for getting the immediate */
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return false;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte selfByte = bytes[0];
				byte immediate8 = (byte) (shortFromBytes((byte[]) data[0]) & 0xFF);

				/*
				 * get the real width - the one in overriden getWidth is only for getting the
				 * immediate
				 */
				boolean W = (selfByte & 0x01) != 0;
				boolean D = getDirection(vm, selfByte);

				if (D == false) {
					if (W) {
						registers.AX.writeDecoded(vm, W, (short) (vm.readPort((short) (immediate8 & 0xFF)) & 0xFFFF));
					} else {
						registers.AX.writeDecoded(vm, W, (short) (vm.readPortByte((byte) (immediate8 & 0xFF)) & 0xFF));
					}
				}
				else {
					short decoded = registers.AX.readDecoded(vm, W);
					if (W) {
						vm.writePort((short) (immediate8 & 0xFF), decoded);
					} else {
						vm.writePortByte((short) (immediate8 & 0xFF), (byte) (decoded & 0xFF));
					}
				}
			}
		};

		decodeTable[0xE4] = inOrOutImm;
		decodeTable[0xE5] = inOrOutImm;
		decodeTable[0xE6] = inOrOutImm;
		decodeTable[0xE7] = inOrOutImm;

		Instruction inOrOut = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				byte selfByte = bytes[0];
				boolean W = getWidth(vm, selfByte);
				boolean D = getDirection(vm, selfByte);

				if (D == false) {
					if (W) {
						registers.AX.writeDecoded(vm, W, (short) (vm.readPort(vm.registers.DX.shortValue()) & 0xFFFF));
					}
					else {
						registers.AX.writeDecoded(vm, W,
								(short) (vm.readPortByte(vm.registers.DX.shortValue()) & 0xFF));
					}
				}
				else {
					short decoded = registers.AX.readDecoded(vm, W);
					if (W) {
						vm.writePort(vm.registers.DX.shortValue(), decoded);
					} else {
						vm.writePortByte(vm.registers.DX.shortValue(), (byte) (decoded & 0xFF));
					}
				}
			}
		};

		decodeTable[0xEC] = inOrOut;
		decodeTable[0xED] = inOrOut;
		decodeTable[0xEE] = inOrOut;
		decodeTable[0xEF] = inOrOut;

		Instruction xlat = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				int effectiveAddress = vm.physicalAddressFromSegmentOffset(segment == null ? vm.registers.DS.shortValue() : segment,
						vm.registers.BX.shortValue());
				effectiveAddress += (vm.registers.AX.readLow() & 0xFF);
				vm.registers.AX.writeLow(vm.readMemoryBytePhysical(effectiveAddress));
			}
		};

		decodeTable[0xD7] = xlat;

		ModRegRmInstruction lea = new ModRegRmInstruction(LeaModRegRmDecoder.instance) {

			@Override
			public boolean getDirection(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				int effectiveAddress = decoded.readEffectiveAddressOfSource();
				decoded.writeDestination((short) (effectiveAddress & 0xFFFF), vm);
			}
		};

		decodeTable[0x8D] = lea;

		LoadPtrToSegInstruction lds = new LoadPtrToSegInstruction(this.registers.DS);
		LoadPtrToSegInstruction les = new LoadPtrToSegInstruction(this.registers.ES);

		decodeTable[0xC5] = lds;
		decodeTable[0xC4] = les;

		Instruction lahf = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				vm.registers.AX.writeHigh(vm.registers.FLAGS.readLow());
			}
		};

		Instruction sahf = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				vm.registers.FLAGS.writeLow(vm.registers.AX.readHigh());
			}
		};

		decodeTable[0x9F] = lahf;
		decodeTable[0x9E] = sahf;

		Instruction pushf = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				vm.pushToStack(vm.registers.FLAGS.shortValue());
			}
		};

		Instruction popf = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				vm.registers.FLAGS.write(vm.popFromStack());
			}
		};

		decodeTable[0x9C] = pushf;
		decodeTable[0x9D] = popf;

		ArithmeticModRegRmInstruction add = new ArithmeticModRegRmInstruction() {
			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 + operand2;
			}
		};

		decodeTable[0x00] = add;
		decodeTable[0x01] = add;
		decodeTable[0x02] = add;
		decodeTable[0x03] = add;

		ArithmeticModRegRmInstructionImmReg addImmReg = new ArithmeticModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 + operand2;
			}
		};

		LogicModRegRmInstructionImmReg orImmReg = new LogicModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 | operand2;
			}
		};

		ArithmeticModRegRmInstructionImmReg adcImmReg = new ArithmeticModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.adc(operand1, operand2);
			}
		};

		ArithmeticModRegRmInstructionImmReg sbbImmReg = new ArithmeticModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.sbb(operand1, operand2);
			}
		};

		LogicModRegRmInstructionImmReg andImmReg = new LogicModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		ArithmeticModRegRmInstructionImmReg subImmReg = new ArithmeticModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}
		};

		LogicModRegRmInstructionImmReg xorImmReg = new LogicModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 ^ operand2;
			}
		};

		ArithmeticModRegRmInstructionImmReg cmpImmReg = new ArithmeticModRegRmInstructionImmReg() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				/* create a temp register to store the destination without overwriting it */
				decoded.destination = vm.registers.createTempRegister(decoded.readDestination(vm));
				super.execute(vm, bytes, data, segment);
			}
		};

		ModInstrRmInstructions aluImmReg = new ModInstrRmInstructions(new ModRegRmInstruction[] { addImmReg, orImmReg,
				adcImmReg, sbbImmReg, andImmReg, subImmReg, xorImmReg, cmpImmReg });

		decodeTable[0x80] = aluImmReg;
		decodeTable[0x81] = aluImmReg;
		/* S = 1 W = 0 is invalid */
		// decodeTable[0x82] = aluImmReg;
		decodeTable[0x83] = aluImmReg;

		ArithmeticInstructionImmA addImmA = new ArithmeticInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 + operand2;
			}
		};

		decodeTable[0x04] = addImmA;
		decodeTable[0x05] = addImmA;

		ArithmeticModRegRmInstruction adc = new ArithmeticModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.adc(operand1, operand2);
			}
		};

		decodeTable[0x10] = adc;
		decodeTable[0x11] = adc;
		decodeTable[0x12] = adc;
		decodeTable[0x13] = adc;

		ArithmeticInstructionImmA adcImmA = new ArithmeticInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.adc(operand1, operand2);
			}
		};

		decodeTable[0x14] = adcImmA;
		decodeTable[0x15] = adcImmA;

		Instruction incReg = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				byte selfByte = bytes[0];
				byte reg = (byte) (selfByte & 0x7);
				Register16 register = ModRegRmDecoder.instance.decodeReg(vm, reg);
				int src = register.shortValue() & 0xFFFF;
				int result = vm.executeOperation(incAddition, src, 1, 16);

				register.write((short) (result & 0xFFFF));
			}
		};

		for (int i = 0; i < 8; i++)
			decodeTable[0x40 + i] = incReg;

		Instruction aaa = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				if ((vm.registers.AX.readLow() & 0xF) > 9 || (0 != (vm.registers.FLAGS.shortValue() & MASK_AF))) {
					vm.registers.AX.add(0x106);
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | (MASK_CF | MASK_AF)));
				} else {
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~(MASK_CF | MASK_AF))));
				}
			}
		};

		decodeTable[0x37] = aaa;

		Instruction daa = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				int oldAl = vm.registers.AX.readLow();
				int oldCfMask = vm.registers.FLAGS.shortValue() & MASK_CF;
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~oldCfMask)));
				if ((vm.registers.AX.readLow() & 0xF) > 9 || (0 != (vm.registers.FLAGS.shortValue() & MASK_AF))) {
					vm.registers.AX.writeLow((byte) (((oldAl & 0xFF) + 6) & 0xFF));
					int newCfMask = (((int) (oldAl & 0xFF) + 6) > 0xFF) ? MASK_CF : 0;
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | oldCfMask | newCfMask));
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | MASK_AF));
				} else {
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~MASK_AF)));
				}
				if ((oldAl & 0xFF) > 0x99 || (oldCfMask != 0)) {
					oldAl = vm.registers.AX.readLow();
					vm.registers.AX.writeLow((byte) (((oldAl & 0xFF) + 0x60) & 0xFF));
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | MASK_CF));
				} else {
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~MASK_CF)));
				}
			}
		};

		decodeTable[0x27] = daa;

		ArithmeticModRegRmInstruction sub = new ArithmeticModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}
		};

		decodeTable[0x28] = sub;
		decodeTable[0x29] = sub;
		decodeTable[0x2A] = sub;
		decodeTable[0x2B] = sub;

		ArithmeticInstructionImmA subImmA = new ArithmeticInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}
		};

		decodeTable[0x2C] = subImmA;
		decodeTable[0x2D] = subImmA;

		ArithmeticModRegRmInstruction sbb = new ArithmeticModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.sbb(operand1, operand2);
			}
		};

		decodeTable[0x18] = sbb;
		decodeTable[0x19] = sbb;
		decodeTable[0x1A] = sbb;
		decodeTable[0x1B] = sbb;

		ArithmeticInstructionImmA sbbImmA = new ArithmeticInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return vm.sbb(operand1, operand2);
			}
		};

		decodeTable[0x1C] = sbbImmA;
		decodeTable[0x1D] = sbbImmA;

		Instruction decReg = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				byte selfByte = bytes[0];
				byte reg = (byte) (selfByte & 0x7);
				Register16 register = ModRegRmDecoder.instance.decodeReg(vm, reg);
				int src = register.shortValue() & 0xFFFF;
				int result = vm.executeOperation(incAddition, src, -1, 16);

				register.write((short) (result & 0xFFFF));
			}
		};

		for (int i = 0; i < 8; i++)
			decodeTable[0x48 + i] = decReg;

		LogicModInstrRmInstruction not = new LogicModInstrRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand) {
				return ~operand;
			}
		};

		ArithmeticModInstrRmInstruction neg = new ArithmeticModInstrRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand) {
				return -operand;
			}
		};

		/* unsigned multiply */
		DivMulInstruction mul = new DivMulInstruction() {
			@Override
			public int operation(VM8086 vm, int operand, int bitnumber) {
				int operand2;
				if (bitnumber == 8)
					operand2 = vm.registers.AX.readLow() & 0xFF;
				else
					operand2 = vm.registers.AX.intValue();

				mul(vm, operand, operand2, bitnumber);
				return operand * operand2;
			}
		};

		/* signed multiplication */
		DivMulInstruction imul = new DivMulInstruction() {
			@Override
			public int operation(VM8086 vm, int operand, int bitnumber) {
				int operand2;

				/* those will get sign extended, as shorts and bytes are signed in Java */
				if (bitnumber == 8)
					operand2 = vm.registers.AX.readLow();
				else
					operand2 = vm.registers.AX.shortValue();

				mul(vm, operand, operand2, bitnumber);
				return operand * operand2;
			}
		};

		/* unsigned division */
		DivMulInstruction div = new DivMulInstruction() {

			@Override
			public int operation(VM8086 vm, int operand2, int bitNumber) throws DivideErrorException {
				int operand1l, operand1h;

				operand1l = vm.registers.AX.intValue();
				if (bitNumber == 8) {
					operand1h = 0;
				} else {
					operand1h = vm.registers.DX.intValue();
				}

				div(vm, operand1l, operand1h, operand2, bitNumber);
				return (operand1l + 65536 * operand1h) / operand2;
			}
		};

		/* signed division */
		DivMulInstruction idiv = new DivMulInstruction() {

			@Override
			public int operation(VM8086 vm, int operand2, int bitNumber) throws DivideErrorException {
				int operand1l, operand1h;

				if (bitNumber == 8) {
					operand1l = vm.registers.AX.shortValue();
					operand1h = 0;
				} else {
					operand1h = vm.registers.DX.shortValue();
					if (operand1h < 0)
						operand1l = -vm.registers.AX.intValue();
					else
						operand1l = vm.registers.AX.intValue();
				}

				div(vm, operand1l, operand1h, operand2, bitNumber);
				return (operand1l + 65536 * operand1h) / operand2;
			}
		};

		LogicModRegRmInstructionImmReg testImmReg = new LogicModRegRmInstructionImmReg() {

			@Override
			public boolean getIsSigned(byte selfByte) {
				return false;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				/* create a temp register to store the destination without overwriting it */
				decoded.destination = vm.registers.createTempRegister(decoded.readDestination(vm));
				super.execute(vm, bytes, data, segment);
			}

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		ModInstrRmInstructions divMulNegNotTest = new ModInstrRmInstructions(
				new ModRegRmInstruction[] { testImmReg, testImmReg, not, neg, mul, imul, div, idiv });
		decodeTable[0xF6] = divMulNegNotTest;
		decodeTable[0xF7] = divMulNegNotTest;

		ArithmeticModRegRmInstruction cmp = new ArithmeticModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				/* create a temp register to store the destination without overwriting it */
				decoded.destination = vm.registers.createTempRegister(decoded.readDestination(vm));
				super.execute(vm, bytes, data, segment);
			}
		};

		decodeTable[0x38] = cmp;
		decodeTable[0x39] = cmp;
		decodeTable[0x3A] = cmp;
		decodeTable[0x3B] = cmp;

		ArithmeticInstructionImmA cmpImmA = new ArithmeticInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 - operand2;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short oldAx = vm.registers.AX.shortValue();
				super.execute(vm, bytes, data, segment);
				vm.registers.AX.write(oldAx);
			}
		};

		decodeTable[0x3C] = cmpImmA;
		decodeTable[0x3D] = cmpImmA;

		Instruction aas = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				if ((vm.registers.AX.readLow() & 0xF) > 9 || (0 != (vm.registers.FLAGS.shortValue() & MASK_AF))) {
					vm.registers.AX.add(-6);
					vm.registers.AX.add(-0x100);
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | (MASK_CF | MASK_AF)));
				} else {
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~(MASK_CF | MASK_AF))));
				}

				vm.registers.AX.write((short) (vm.registers.AX.intValue() & 0xFF0F));
			}
		};

		decodeTable[0x3F] = aas;

		Instruction das = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment) {
				int oldAl = vm.registers.AX.readLow();
				int oldCfMask = vm.registers.FLAGS.shortValue() & MASK_CF;
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~oldCfMask)));
				if ((vm.registers.AX.readLow() & 0xF) > 9 || (0 != (vm.registers.FLAGS.shortValue() & MASK_AF))) {
					vm.registers.AX.writeLow((byte) (((oldAl & 0xFF) - 6) & 0xFF));
					int newCfMask = ((((int) (oldAl & 0xFF) - 6) & 0xFFF) > 0xFF) ? MASK_CF : 0;
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | oldCfMask | newCfMask));
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | MASK_AF));
				} else {
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() & (~MASK_AF)));
				}
				if ((oldAl & 0xFF) > 0x99 || (oldCfMask != 0)) {
					oldAl = vm.registers.AX.readLow();
					vm.registers.AX.writeLow((byte) (((oldAl & 0xFF) - 0x60) & 0xFF));
					vm.registers.FLAGS.write((short) (vm.registers.FLAGS.shortValue() | MASK_CF));
				}
			}
		};

		decodeTable[0x2F] = das;

		ImmediateInstruction aam = new ImmediateInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return false;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int tempAl = vm.registers.AX.readLow() & 0xFF;

				short imm8 = vm.shortFromBytes((byte[]) data[0]);
				if (imm8 == 0)
					throw new DivideErrorException(vm, vm.registers.CS.shortValue(),
							(short) (vm.registers.IP.intValue() - 2));

				vm.registers.AX.writeHigh((byte) (tempAl / imm8));
				vm.registers.AX.writeLow((byte) (tempAl % imm8));
			}
		};

		decodeTable[0xD4] = aam;

		ImmediateInstruction aad = new ImmediateInstruction() {

			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return false;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int tempAl = vm.registers.AX.readLow() & 0xFF;
				int tempAh = vm.registers.AX.readHigh() & 0xFF;

				short imm8 = vm.shortFromBytes((byte[]) data[0]);

				vm.registers.AX.writeHigh((byte) 0);
				vm.registers.AX.writeLow((byte) ((tempAl + (tempAh * imm8)) & 0xFF));
			}
		};

		decodeTable[0xD5] = aad;

		Instruction cbw = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				if (vm.registers.AX.readLow() < 0)
					vm.registers.AX.writeHigh((byte) 0xFF);
				else
					vm.registers.AX.writeHigh((byte) 0);
			}
		};

		decodeTable[0x98] = cbw;

		Instruction cwd = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				if (vm.registers.AX.shortValue() < 0)
					vm.registers.DX.write((short) 0xFFFF);
				else
					vm.registers.DX.write((short) 0);
			}
		};

		decodeTable[0x99] = cwd;

		CountModInstrRmInstruction shl = new CountModInstrRmInstruction() {
			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				boolean msbSet = vm.isMsbSet(currentData, bitcount);
				int setMask = calculateFlagSetMask(currentData, (short) (currentData & getBitmask(bitcount)), bitcount);
				vm.adjustFlags(MASK_CF | MASK_ZF | MASK_SF | MASK_PF, msbSet ? MASK_CF : 0 | setMask);
				return (currentData << 1) & vm.getBitmask(bitcount);
			}
		};

		CountModInstrRmInstruction shr = new CountModInstrRmInstruction() {
			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				boolean lsbSet = 0 != (currentData & 1);
				int setMask = calculateFlagSetMask(currentData, (short) (currentData & getBitmask(bitcount)), bitcount);
				vm.adjustFlags(MASK_CF | MASK_ZF | MASK_SF | MASK_PF, lsbSet ? MASK_CF : 0 | setMask);
				return (currentData >> 1) & vm.getBitmask(bitcount);
			}
		};

		CountModInstrRmInstruction sar = new CountModInstrRmInstruction() {
			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				boolean lsbSet = 0 != (currentData & 1);
				boolean msbSet = vm.isMsbSet(currentData, bitcount);
				int setMask = calculateFlagSetMask(currentData, (short) (currentData & getBitmask(bitcount)), bitcount);
				vm.adjustFlags(MASK_CF | MASK_ZF | MASK_SF | MASK_PF, lsbSet ? MASK_CF : 0 | setMask);
				return vm.setMsb((currentData >> 1), bitcount, msbSet) & vm.getBitmask(bitcount);
			}
		};

		CountModInstrRmInstruction rol = new CountModInstrRmInstruction() {

			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				return ((currentData << 1) + (vm.isMsbSet(currentData, bitcount) ? 1 : 0)) & vm.getBitmask(bitcount);
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int count = this.getCount(vm, bytes[0]);
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				super.execute(vm, bytes, data, segment);
				int bitnumber = getWidth(vm, bytes[0]) ? 16 : 8;

				int dst = decoded.readDestination(vm) & 0xFFFF;

				if ((count & 0xF) != 0) {
					vm.adjustFlags(MASK_CF, (0 != (dst & 1)) ? MASK_CF : 0);
				}
				if ((count & 0xF) == 1) {
					boolean cfSet = 0 != (vm.registers.FLAGS.intValue() & MASK_CF);
					boolean msbSet = vm.isMsbSet(dst, bitnumber);
					vm.adjustFlags(MASK_OF, (cfSet != msbSet) ? MASK_OF : 0);
				}
			}
		};

		CountModInstrRmInstruction ror = new CountModInstrRmInstruction() {

			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				return ((currentData >> 1) + (((currentData & 1) != 0) ? (1 << (bitcount - 1)) : 0))
						& vm.getBitmask(bitcount);
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int count = this.getCount(vm, bytes[0]);
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				super.execute(vm, bytes, data, segment);
				int bitnumber = getWidth(vm, bytes[0]) ? 16 : 8;

				int dst = decoded.readDestination(vm) & 0xFFFF;

				if ((count & 0xF) != 0) {
					vm.adjustFlags(MASK_CF, (vm.isMsbSet(count, bitnumber)) ? MASK_CF : 0);
				}
				if ((count & 0xF) == 1) {
					/*
					 * FIXME: documentation unclear "OF := MSB(DEST) XOR MSB − 1(DEST);"
					 */
					boolean cfSet = 0 != (vm.registers.FLAGS.intValue() & MASK_CF);
					boolean msbSet = vm.isMsbSet(dst, bitnumber);
					vm.adjustFlags(MASK_OF, (cfSet != msbSet) ? MASK_OF : 0);
				}
			}
		};

		CountModInstrRmInstruction rcl = new CountModInstrRmInstruction() {

			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				boolean tempCF = vm.isMsbSet(currentData, bitcount);
				boolean CFset = (vm.registers.FLAGS.intValue() & MASK_CF) != 0;
				currentData = (currentData << 1) + (CFset ? 1 : 0);
				int setMask = calculateFlagSetMask(currentData, (short) (currentData & getBitmask(bitcount)), bitcount);
				vm.adjustFlags(MASK_CF | MASK_ZF | MASK_SF | MASK_PF, tempCF ? MASK_CF : 0 | setMask);
				return currentData & vm.getBitmask(bitcount + 1);
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int count = getCount(vm, bytes[0]);
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				int bitnumber = getWidth(vm, bytes[0]) ? 16 : 8;
				super.execute(vm, bytes, data, segment);
				int dst = decoded.readDestination(vm) & 0xFFFF;
				if ((count & 0xF) == 1) {
					boolean cfSet = 0 != (vm.registers.FLAGS.intValue() & MASK_CF);
					boolean msbSet = vm.isMsbSet(dst, bitnumber);
					vm.adjustFlags(MASK_OF, (cfSet != msbSet) ? MASK_OF : 0);
				}
			}
		};

		CountModInstrRmInstruction rcr = new CountModInstrRmInstruction() {

			@Override
			public int onIteration(VM8086 vm, int currentIndex, int maxCounter, int currentData, int bitcount) {
				boolean tempCF = (currentData & 1) != 0;
				boolean CFset = (vm.registers.FLAGS.intValue() & MASK_CF) != 0;
				currentData = (currentData >> 1) + (CFset ? (1 << bitcount) : 0);
				int setMask = calculateFlagSetMask(currentData, (short) (currentData & getBitmask(bitcount)), bitcount);
				vm.adjustFlags(MASK_CF | MASK_ZF | MASK_SF | MASK_PF, tempCF ? MASK_CF : 0 | setMask);
				return currentData & vm.getBitmask(bitcount + 1);
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				int count = getCount(vm, bytes[0]);
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				int bitnumber = getWidth(vm, bytes[0]) ? 16 : 8;

				int dst = decoded.readDestination(vm) & 0xFFFF;
				if ((count & 0xF) == 1) {
					boolean cfSet = 0 != (vm.registers.FLAGS.intValue() & MASK_CF);
					boolean msbSet = vm.isMsbSet(dst, bitnumber);
					vm.adjustFlags(MASK_OF, (cfSet != msbSet) ? MASK_OF : 0);
				}

				super.execute(vm, bytes, data, segment);
			}
		};

		ModInstrRmInstructions shifts = new ModInstrRmInstructions(
				new ModRegRmInstruction[] { rol, ror, rcl, rcr, shl, shr, null, sar });
		decodeTable[0xD0] = shifts;
		decodeTable[0xD1] = shifts;
		decodeTable[0xD2] = shifts;
		decodeTable[0xD3] = shifts;

		LogicModRegRmInstruction and = new LogicModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		decodeTable[0x20] = and;
		decodeTable[0x21] = and;
		decodeTable[0x22] = and;
		decodeTable[0x23] = and;

		LogicInstructionImmA andImmA = new LogicInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		decodeTable[0x24] = andImmA;
		decodeTable[0x25] = andImmA;

		LogicModRegRmInstruction test = new LogicModRegRmInstruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				ModRegRmDecoded decoded = (ModRegRmDecoded) data[0];
				/* create a temp register to store the destination without overwriting it */
				decoded.destination = vm.registers.createTempRegister(decoded.readDestination(vm));
				super.execute(vm, bytes, data, segment);
			}

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		decodeTable[0x84] = test;
		decodeTable[0x85] = test;

		LogicInstructionImmA testImmA = new LogicInstructionImmA() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short originalAx = vm.registers.AX.shortValue();

				super.execute(vm, bytes, data, segment);

				/* Restore the value of the accumulator */
				vm.registers.AX.write(originalAx);
			}

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 & operand2;
			}
		};

		decodeTable[0xA8] = testImmA;
		decodeTable[0xA9] = testImmA;

		LogicModRegRmInstruction or = new LogicModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 | operand2;
			}
		};

		decodeTable[0x08] = or;
		decodeTable[0x09] = or;
		decodeTable[0x0A] = or;
		decodeTable[0x0B] = or;

		LogicInstructionImmA orImmA = new LogicInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 | operand2;
			}
		};

		decodeTable[0x0C] = orImmA;
		decodeTable[0x0D] = orImmA;

		LogicModRegRmInstruction xor = new LogicModRegRmInstruction() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 ^ operand2;
			}
		};

		decodeTable[0x30] = xor;
		decodeTable[0x31] = xor;
		decodeTable[0x32] = xor;
		decodeTable[0x33] = xor;

		LogicInstructionImmA xorImmA = new LogicInstructionImmA() {

			@Override
			public int operation(VM8086 vm, int operand1, int operand2) {
				return operand1 ^ operand2;
			}
		};

		decodeTable[0x34] = xorImmA;
		decodeTable[0x35] = xorImmA;

		/* string instructions */
		RepPrefix rep = new RepPrefix();
		decodeTable[0xF2] = rep;
		decodeTable[0xF3] = rep;

		StringInstruction movs = new StringInstruction() {

			@Override
			protected void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
					short destinationSegment, short destinationOffset) {

				boolean W = getWidth(vm, selfByte);
				if (W) {
					// System.out.printf("*%04X=*%04X=%04X ",
					// vm.physicalAddressFromSegmentOffset(destinationSegment,
					// destinationOffset),vm.physicalAddressFromSegmentOffset(sourceSegment,
					// sourceOffset), vm.readMemoryShort16(sourceSegment, sourceOffset));
					vm.writeMemoryShort16(destinationSegment, destinationOffset,
							vm.readMemoryShort16(sourceSegment, sourceOffset));
				} else {
					// System.out.printf("*%04X=*%04X=%04X ",
					// vm.physicalAddressFromSegmentOffset(destinationSegment,
					// destinationOffset),vm.physicalAddressFromSegmentOffset(sourceSegment,
					// sourceOffset), vm.readMemoryByte16(sourceSegment, sourceOffset));
					vm.writeMemoryByte16(destinationSegment, destinationOffset,
							vm.readMemoryByte16(sourceSegment, sourceOffset));
				}
			}

			@Override
			protected boolean doesChangeDi() {
				return true;
			}

			@Override
			protected boolean doesChangeSi() {
				return true;
			}
		};

		decodeTable[0xA4] = movs;
		decodeTable[0xA5] = movs;

		StringInstruction cmps = new StringInstruction() {

			@Override
			protected void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
					short destinationSegment, short destinationOffset) throws CpuException {
				short op1, op2;

				boolean W = getWidth(vm, selfByte);

				if (W) {
					op2 = vm.readMemoryShort16(destinationSegment, destinationOffset);
					op1 = vm.readMemoryShort16(sourceSegment, sourceOffset);
				} else {
					op2 = (short) (vm.readMemoryByte16(destinationSegment, destinationOffset) & 0xFF);
					op1 = (short) (vm.readMemoryByte16(sourceSegment, sourceOffset) & 0xFF);
				}

				int moveBytes = W ? 2 : 1;
				vm.executeOperation(comparisonOperator, op1, op2, moveBytes * 8);
			}

			@Override
			protected boolean doesChangeDi() {
				return true;
			}

			@Override
			protected boolean doesChangeSi() {
				return true;
			}

		};

		decodeTable[0xA6] = cmps;
		decodeTable[0xA7] = cmps;

		StringInstruction scas = new StringInstruction() {

			@Override
			protected void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
					short destinationSegment, short destinationOffset) throws CpuException {
				short op1, op2;
				boolean W = getWidth(vm, selfByte);

				if (W) {
					op2 = vm.readMemoryShort16(destinationSegment, destinationOffset);
					op1 = vm.registers.AX.shortValue();
				} else {
					op2 = (short) (vm.readMemoryByte16(destinationSegment, destinationOffset) & 0xFF);
					op1 = (short) (vm.registers.AX.readLow() & 0xFF);
				}

				int moveBytes = W ? 2 : 1;
				vm.executeOperation(comparisonOperator, op1, op2, moveBytes * 8);
			}

			@Override
			protected boolean doesChangeDi() {
				return true;
			}

			@Override
			protected boolean doesChangeSi() {
				return false;
			}
		};

		decodeTable[0xAE] = scas;
		decodeTable[0xAF] = scas;

		StringInstruction lods = new StringInstruction() {

			@Override
			protected void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
					short destinationSegment, short destinationOffset) {
				boolean W = getWidth(vm, selfByte);

				if (W) {
					vm.registers.AX.write(vm.readMemoryShort16(sourceSegment, sourceOffset));
				} else {
					vm.registers.AX.writeLow(vm.readMemoryByte16(sourceSegment, sourceOffset));
				}
			}

			@Override
			protected boolean doesChangeSi() {
				return true;
			}

			@Override
			protected boolean doesChangeDi() {
				return false;
			}
		};

		decodeTable[0xAC] = lods;
		decodeTable[0xAD] = lods;

		StringInstruction stos = new StringInstruction() {

			@Override
			protected void stringOperation(VM8086 vm, byte selfByte, short sourceSegment, short sourceOffset,
					short destinationSegment, short destinationOffset) {
				boolean W = getWidth(vm, selfByte);

				if (W) {
					vm.writeMemoryShort16(destinationSegment, destinationOffset, vm.registers.AX.shortValue());
				} else {
					vm.writeMemoryByte16(destinationSegment, destinationOffset, (byte) vm.registers.AX.readLow());
				}
			}

			@Override
			protected boolean doesChangeSi() {
				return false;
			}

			@Override
			protected boolean doesChangeDi() {
				return true;
			}
		};

		decodeTable[0xAA] = stos;
		decodeTable[0xAB] = stos;

		Instruction shortCallRel = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short destination;
				/* read two bytes */
				byte dstBytes[] = this.readImmediateBytes(vm, true);
				destination = vm.shortFromBytes(dstBytes);
				vm.pushToStack(vm.registers.IP.shortValue());
				vm.registers.IP.add(destination & 0xFFFF);
			}
		};

		decodeTable[0xE8] = shortCallRel;

		Instruction farCall = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short destinationOffset, destinationSegment;
				/* read two bytes for the offset */
				byte dstOffBytes[] = this.readImmediateBytes(vm, true);
				destinationOffset = vm.shortFromBytes(dstOffBytes);

				/* read two bytes for the segment */
				byte dstSegBytes[] = this.readImmediateBytes(vm, true);
				destinationSegment = vm.shortFromBytes(dstSegBytes);

				vm.pushToStack(vm.registers.CS.shortValue());
				vm.pushToStack(vm.registers.IP.shortValue());

				vm.registers.IP.write(destinationOffset);
				vm.registers.CS.write(destinationSegment);
			}
		};

		decodeTable[0x9A] = farCall;

		Instruction nearJmpRel = new Instruction() {
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return 0 == (selfByte & 0x02);
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short destination;
				byte selfByte = bytes[0];
				/* read two bytes */
				boolean W = getWidth(vm, selfByte);
				byte dstBytes[] = this.readImmediateBytes(vm, W);
				destination = vm.shortFromBytes(dstBytes);

				if (W == false) {
					if (0 != (destination & 0x80)) {
						destination |= 0xFF00;
					}
				}

				vm.registers.IP.add(destination & 0xFFFF);
			}
		};

		decodeTable[0xE9] = nearJmpRel;
		decodeTable[0xEB] = nearJmpRel;

		Instruction farJmp = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				short destinationOffset, destinationSegment;
				/* read two bytes for the offset */
				byte dstOffBytes[] = this.readImmediateBytes(vm, true);
				destinationOffset = vm.shortFromBytes(dstOffBytes);

				/* read two bytes for the segment */
				byte dstSegBytes[] = this.readImmediateBytes(vm, true);
				destinationSegment = vm.shortFromBytes(dstSegBytes);

				vm.registers.IP.write(destinationOffset);
				vm.registers.CS.write(destinationSegment);
			}
		};

		decodeTable[0xEA] = farJmp;

		Instruction ret = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.IP.write(vm.popFromStack());
			}
		};

		decodeTable[0xC3] = ret;

		ImmediateInstruction retImm = new ImmediateInstruction() {
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				byte[] immData = (byte[]) data[0];
				int stackChange = vm.shortFromBytes(immData) & 0xFFFF;
				vm.registers.IP.write(vm.popFromStack());
				vm.registers.SP.add(stackChange);
			}
		};

		decodeTable[0xC2] = retImm;

		Instruction retFar = new Instruction() {
			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.IP.write(vm.popFromStack());
				vm.registers.CS.write(vm.popFromStack());
			}
		};

		decodeTable[0xCB] = retFar;

		ImmediateInstruction retFarImm = new ImmediateInstruction() {
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return true;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				byte[] immData = (byte[]) data[0];
				int stackChange = vm.shortFromBytes(immData) & 0xFFFF;
				vm.registers.IP.write(vm.popFromStack());
				vm.registers.CS.write(vm.popFromStack());
				vm.registers.SP.add(stackChange);
			}
		};

		decodeTable[0xCA] = retFarImm;

		/* conditional jumps */

		JmpFlagInstruction ja = new JmpFlagInstruction(MASK_CF | MASK_ZF, 0);
		decodeTable[0x77] = ja;

		JmpFlagInstruction jae = new JmpFlagInstruction(MASK_CF, 0);
		decodeTable[0x73] = jae;

		JmpFlagInstruction jb = new JmpFlagInstruction(MASK_CF, MASK_CF);
		decodeTable[0x72] = jb;

		JmpFlagInstruction je = new JmpFlagInstruction(MASK_ZF, MASK_ZF);
		decodeTable[0x74] = je;

		JmpFlagInstruction jne = new JmpFlagInstruction(MASK_ZF, 0);
		decodeTable[0x75] = jne;

		JmpConditionalInstruction jl = new JmpConditionalInstruction() {
			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				boolean ofSet = (vm.registers.FLAGS.intValue() & MASK_OF) != 0;
				boolean sfSet = (vm.registers.FLAGS.intValue() & MASK_SF) != 0;
				return ofSet != sfSet;
			}
		};
		decodeTable[0x7C] = jl;

		JmpConditionalInstruction jle = new JmpConditionalInstruction() {

			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				return jl.checkCondition(vm, selfByte) || je.checkCondition(vm, selfByte);
			}
		};
		decodeTable[0x7E] = jle;

		JmpConditionalInstruction jge = new JmpConditionalInstruction() {

			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				return !jl.checkCondition(vm, selfByte);
			}
		};
		decodeTable[0x7D] = jge;

		JmpConditionalInstruction jg = new JmpConditionalInstruction() {

			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				return jge.checkCondition(vm, selfByte) && !je.checkCondition(vm, selfByte);
			}
		};
		decodeTable[0x7F] = jg;

		JmpConditionalInstruction jbe = new JmpConditionalInstruction() {

			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				return jb.checkCondition(vm, selfByte) || je.checkCondition(vm, selfByte);
			}
		};
		decodeTable[0x76] = jbe;

		JmpFlagInstruction jp = new JmpFlagInstruction(MASK_PF, MASK_PF);
		decodeTable[0x7A] = jp;

		JmpFlagInstruction jnp = new JmpFlagInstruction(MASK_PF, 0);
		decodeTable[0x7B] = jnp;

		JmpFlagInstruction jo = new JmpFlagInstruction(MASK_OF, MASK_OF);
		decodeTable[0x70] = jo;

		JmpFlagInstruction jno = new JmpFlagInstruction(MASK_OF, 0);
		decodeTable[0x71] = jno;

		JmpFlagInstruction js = new JmpFlagInstruction(MASK_SF, MASK_SF);
		decodeTable[0x78] = js;

		JmpFlagInstruction jns = new JmpFlagInstruction(MASK_SF, 0);
		decodeTable[0x79] = jns;

		/* loops */

		JmpConditionalInstruction loop = new JmpConditionalInstruction() {
			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				vm.registers.CX.add(-1);
				return vm.registers.CX.intValue() != 0;
			}
		};
		decodeTable[0xE2] = loop;

		JmpConditionalInstruction loope = new JmpConditionalInstruction() {
			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				vm.registers.CX.add(-1);
				if (je.checkCondition(vm, selfByte) == false)
					return false;
				return vm.registers.CX.intValue() != 0;
			}
		};
		decodeTable[0xE1] = loope;

		JmpConditionalInstruction loopne = new JmpConditionalInstruction() {
			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				vm.registers.CX.add(-1);
				if (je.checkCondition(vm, selfByte) == true)
					return false;
				return vm.registers.CX.intValue() != 0;
			}
		};
		decodeTable[0xE0] = loopne;

		JmpConditionalInstruction jcxz = new JmpConditionalInstruction() {

			@Override
			public boolean checkCondition(VM8086 vm, byte selfByte) {
				return vm.registers.CX.intValue() == 0;
			}
		};

		decodeTable[0xE3] = jcxz;

		/* interrupts */

		ImmediateInstruction interrupt = new ImmediateInstruction() {

			/* INT alwyas takes an 8 bit immediate */
			@Override
			public boolean getWidth(VM8086 vm, byte selfByte) {
				return false;
			}

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.startInterrupt(((byte[]) data[0])[0], vm.registers.IP.shortValue(), vm.registers.CS.shortValue(),
						vm.registers.FLAGS.shortValue());
			}
		};

		decodeTable[0xCD] = interrupt;

		Instruction int3 = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.startInterrupt((byte) 3, vm.registers.IP.shortValue(), vm.registers.CS.shortValue(),
						vm.registers.FLAGS.shortValue());
			}
		};

		decodeTable[0xCC] = int3;

		Instruction intO = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				if ((vm.registers.FLAGS.intValue() & MASK_OF) != 0)
					vm.startInterrupt((byte) 3, vm.registers.IP.shortValue(), vm.registers.CS.shortValue(),
							vm.registers.FLAGS.shortValue());
			}
		};

		decodeTable[0xCE] = intO;

		Instruction iret = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.IP.write(vm.popFromStack());
				vm.registers.CS.write(vm.popFromStack());
				vm.registers.FLAGS.write(vm.popFromStack());
			}
		};

		decodeTable[0xCF] = iret;

		/* FLAGS control instructions */

		Instruction clc = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() & (~MASK_CF)));
			}
		};

		decodeTable[0xF8] = clc;

		Instruction cmc = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() ^ (MASK_CF)));
			}
		};

		decodeTable[0xF5] = cmc;

		Instruction stc = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | MASK_CF));
			}
		};

		decodeTable[0xF9] = stc;

		Instruction cld = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() & (~MASK_DF)));
			}
		};

		decodeTable[0xFC] = cld;

		Instruction std = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | MASK_DF));
			}
		};

		decodeTable[0xFD] = std;

		Instruction cli = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() & (~MASK_IF)));
			}
		};

		decodeTable[0xFA] = cli;

		Instruction sti = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				vm.registers.FLAGS.write((short) (vm.registers.FLAGS.intValue() | MASK_IF));
			}
		};

		decodeTable[0xFB] = sti;

		/* Unimplemented instructions */

		Instruction fwait = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				// TODO Auto-generated method stub
			}
		};

		decodeTable[0x9B] = fwait;

		Instruction lock = new Instruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				// TODO Auto-generated method stub
			}
		};

		decodeTable[0xF0] = lock;

		ModRegRmInstruction esc = new ModRegRmInstruction() {

			@Override
            public void execute(VM8086 vm, byte[] bytes, Object[] data, Short segment)
					throws CpuException {
				// TODO Auto-generated method stub
			}
		};

		for (int i = 0; i < 8; i++) {
			decodeTable[0xD8 + i] = esc;
		}

		SegmentOverridePrefix segOverride = new SegmentOverridePrefix();
		decodeTable[0x26] = segOverride;
		decodeTable[0x2E] = segOverride;
		decodeTable[0x36] = segOverride;
		decodeTable[0x3E] = segOverride;
	}

	public int getBitmask(int bitcount) {
		return (1 << bitcount) - 1;
	}

	protected boolean isMsbSet(int currentData, int bitcount) {
		return 0 != ((1 << (bitcount - 1)) & currentData);
	}

	protected int setMsb(int currentData, int bitcount, boolean msbSet) {
		return currentData | (msbSet ? (1 << (bitcount - 1)) : 0);
	}

	protected short readPort(short s) {
		int result = readPortByte(s) & 0xFF;
		result |= ((readPortByte(s) & 0xFF) << 8);
		return (short) (result & 0xFFFF);
	}

	protected byte readPortByte(short s) {
		ioPorts[0x20] = 0; // PIC EOI

		for (IPortSpaceDevice device : portSpaceDevices) {
			if (device.matchPort(s)) {
				return device.readByte(s);
			}
		}

		return ioPorts[s & 0xFFFF];
	}

	protected void writePort(short s, short data) {
		writePortByte(s, (byte) (data & 0xFF));
		writePortByte(s, (byte) ((data & 0xFF00) >> 8));
	}

	protected void writePortByte(short s, byte data) {

		for (IPortSpaceDevice device : portSpaceDevices) {
			if (device.matchPort(s)) {
				device.writeByte(s, data);
				return;
			}
		}

		ioPorts[s & 0xFFFF] = data;
	}

	private void logState(String reason) {
		System.out.printf("CS:IP=%s:%s " + reason + " - AX=%s ES:BX=%s:%s CX=%s DX=%s\n", registers.CS.toString(), registers.IP.toString(), registers.AX.toString(),
				registers.ES.toString(),registers.BX.toString(), registers.CX.toString(), registers.DX.toString());
	}

	public void startInterrupt(byte interruptNumber, short originalIp, short originalCs, short flags) {
		if (ipLogging) {
			logState(String.format("INT%X", interruptNumber));
		}

		halted = false;
		this.pushToStack(flags);
		this.pushToStack(originalCs);
		this.pushToStack(originalIp);

		IvtEntry ivtEntry = this.readIvtEntry(interruptNumber);
		this.registers.FLAGS.write((short) (flags & (~(MASK_IF | MASK_TF))));
		this.registers.CS.write(ivtEntry.Cs);
		this.registers.IP.write(ivtEntry.Ip);
	}

	protected IvtEntry readIvtEntry(byte interruptNumber) {
		int ivtEntryOffset = (interruptNumber & 0xFF) * 4;
		byte[] memory = new byte[4];
		for (int i = 0; i < memory.length; i++)
			memory[i] = this.readMemoryBytePhysical(i + ivtEntryOffset);
		return new IvtEntry(memory);
	}

	protected int calculateFlagSetMask(int precut, short postcut, int bits) {
		int mask = 0;

		int bitsSet = 0;

		for (int i = 0; i < 8; i++) {
			if ((postcut & (1 << i)) != 0) {
				bitsSet++;
			}
		}

		if (bitsSet % 2 == 0)
			mask |= MASK_PF;

		if (postcut == 0)
			mask |= MASK_ZF;

		int signMask = (1 << (bits - 1));

		if ((postcut & signMask) != 0)
			mask |= MASK_SF;

		int carryMask = ~((1 << bits) - 1);
		if ((precut & carryMask) != 0)
			mask |= MASK_CF;

		return mask;
	}

	/**
	 * Gets a byte at CS:IP.
	 */
	public byte getIpByte() {
		return readMemoryByte16(registers.CS.shortValue(), registers.IP.shortValue());
	}

	/**
	 * Gets an Instruction object for a byte at CS:IP.
	 */
	public Instruction fetch() {
		byte instructionByte = getIpByte();
		return decodeTable[((int) instructionByte) & 0xFF];
	}

	public void run() {
		running = true;

		while (running) {
			running = step();
		}
	}

	public static final short VGA_ROM_F16[] = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7e, 0x81, 0xa5, 0x81, 0x81, 0xbd, 0x99, 0x81, 0x81, 0x7e, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x7e, 0xff, 0xdb, 0xff, 0xff, 0xc3, 0xe7, 0xff, 0xff, 0x7e, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x6c, 0xfe, 0xfe, 0xfe, 0xfe, 0x7c, 0x38, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x10, 0x38, 0x7c, 0xfe, 0x7c, 0x38, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18,
			0x3c, 0x3c, 0xe7, 0xe7, 0xe7, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c, 0x7e,
			0xff, 0xff, 0x7e, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c,
			0x3c, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xe7, 0xc3, 0xc3, 0xe7,
			0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x66, 0x42, 0x42, 0x66, 0x3c, 0x00,
			0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xc3, 0x99, 0xbd, 0xbd, 0x99, 0xc3, 0xff, 0xff, 0xff,
			0xff, 0xff, 0x00, 0x00, 0x1e, 0x0e, 0x1a, 0x32, 0x78, 0xcc, 0xcc, 0xcc, 0xcc, 0x78, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x3c, 0x66, 0x66, 0x66, 0x66, 0x3c, 0x18, 0x7e, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x3f, 0x33, 0x3f, 0x30, 0x30, 0x30, 0x30, 0x70, 0xf0, 0xe0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7f, 0x63,
			0x7f, 0x63, 0x63, 0x63, 0x63, 0x67, 0xe7, 0xe6, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0xdb,
			0x3c, 0xe7, 0x3c, 0xdb, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0xc0, 0xe0, 0xf0, 0xf8, 0xfe, 0xf8,
			0xf0, 0xe0, 0xc0, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x06, 0x0e, 0x1e, 0x3e, 0xfe, 0x3e, 0x1e, 0x0e,
			0x06, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c, 0x7e, 0x18, 0x18, 0x18, 0x7e, 0x3c, 0x18, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x00, 0x66, 0x66, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x7f, 0xdb, 0xdb, 0xdb, 0x7b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x7c, 0xc6, 0x60, 0x38, 0x6c, 0xc6, 0xc6, 0x6c, 0x38, 0x0c, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0xfe, 0xfe, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c,
			0x7e, 0x18, 0x18, 0x18, 0x7e, 0x3c, 0x18, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c, 0x7e, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x7e, 0x3c, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x0c, 0xfe, 0x0c, 0x18,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x60, 0xfe, 0x60, 0x30, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc0, 0xc0, 0xc0, 0xfe, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x24, 0x66, 0xff, 0x66, 0x24, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x10, 0x38, 0x38, 0x7c, 0x7c, 0xfe, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0xfe, 0xfe, 0x7c, 0x7c, 0x38, 0x38, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c, 0x3c, 0x3c,
			0x18, 0x18, 0x18, 0x00, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x66, 0x66, 0x66, 0x24, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x6c, 0x6c, 0xfe, 0x6c, 0x6c, 0x6c, 0xfe,
			0x6c, 0x6c, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x7c, 0xc6, 0xc2, 0xc0, 0x7c, 0x06, 0x06, 0x86, 0xc6, 0x7c,
			0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc2, 0xc6, 0x0c, 0x18, 0x30, 0x60, 0xc6, 0x86, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0x6c, 0x38, 0x76, 0xdc, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x30, 0x30, 0x30, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x0c, 0x18, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x18, 0x0c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x18,
			0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x18, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x66,
			0x3c, 0xff, 0x3c, 0x66, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x7e,
			0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18,
			0x18, 0x18, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x06, 0x0c, 0x18, 0x30, 0x60, 0xc0, 0x80, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x3c, 0x66, 0xc3, 0xc3, 0xdb, 0xdb, 0xc3, 0xc3, 0x66, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x18, 0x38, 0x78, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6,
			0x06, 0x0c, 0x18, 0x30, 0x60, 0xc0, 0xc6, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0x06, 0x06,
			0x3c, 0x06, 0x06, 0x06, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0c, 0x1c, 0x3c, 0x6c, 0xcc, 0xfe,
			0x0c, 0x0c, 0x0c, 0x1e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0xc0, 0xc0, 0xc0, 0xfc, 0x06, 0x06, 0x06,
			0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x60, 0xc0, 0xc0, 0xfc, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0xc6, 0x06, 0x06, 0x0c, 0x18, 0x30, 0x30, 0x30, 0x30, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0x7e, 0x06, 0x06, 0x06, 0x0c, 0x78, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x18, 0x18, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x18, 0x18, 0x00, 0x00, 0x00, 0x18, 0x18, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x0c, 0x18,
			0x30, 0x60, 0x30, 0x18, 0x0c, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7e, 0x00, 0x00,
			0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x60, 0x30, 0x18, 0x0c, 0x06, 0x0c, 0x18,
			0x30, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0x0c, 0x18, 0x18, 0x18, 0x00, 0x18, 0x18,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xde, 0xde, 0xde, 0xdc, 0xc0, 0x7c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x10, 0x38, 0x6c, 0xc6, 0xc6, 0xfe, 0xc6, 0xc6, 0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0xfc, 0x66, 0x66, 0x66, 0x7c, 0x66, 0x66, 0x66, 0x66, 0xfc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x3c, 0x66, 0xc2, 0xc0, 0xc0, 0xc0, 0xc0, 0xc2, 0x66, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xf8, 0x6c,
			0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x6c, 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x66, 0x62, 0x68,
			0x78, 0x68, 0x60, 0x62, 0x66, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x66, 0x62, 0x68, 0x78, 0x68,
			0x60, 0x60, 0x60, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x66, 0xc2, 0xc0, 0xc0, 0xde, 0xc6, 0xc6,
			0x66, 0x3a, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc6, 0xc6, 0xc6, 0xc6, 0xfe, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x1e, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0xcc, 0xcc, 0xcc, 0x78, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0xe6, 0x66, 0x66, 0x6c, 0x78, 0x78, 0x6c, 0x66, 0x66, 0xe6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0xf0, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x62, 0x66, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xe7,
			0xff, 0xff, 0xdb, 0xc3, 0xc3, 0xc3, 0xc3, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc6, 0xe6, 0xf6, 0xfe,
			0xde, 0xce, 0xc6, 0xc6, 0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6,
			0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfc, 0x66, 0x66, 0x66, 0x7c, 0x60, 0x60, 0x60,
			0x60, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xd6, 0xde, 0x7c,
			0x0c, 0x0e, 0x00, 0x00, 0x00, 0x00, 0xfc, 0x66, 0x66, 0x66, 0x7c, 0x6c, 0x66, 0x66, 0x66, 0xe6, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0x60, 0x38, 0x0c, 0x06, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0xff, 0xdb, 0x99, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3,
			0xc3, 0xc3, 0xc3, 0xc3, 0xc3, 0x66, 0x3c, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3, 0xc3, 0xc3,
			0xc3, 0xdb, 0xdb, 0xff, 0x66, 0x66, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3, 0x66, 0x3c, 0x18, 0x18,
			0x3c, 0x66, 0xc3, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3, 0xc3, 0x66, 0x3c, 0x18, 0x18, 0x18,
			0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xc3, 0x86, 0x0c, 0x18, 0x30, 0x60, 0xc1, 0xc3, 0xff,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x3c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x80, 0xc0, 0xe0, 0x70, 0x38, 0x1c, 0x0e, 0x06, 0x02, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x3c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x10, 0x38,
			0x6c, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0x00, 0x00, 0x30, 0x30, 0x18, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78, 0x0c, 0x7c,
			0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xe0, 0x60, 0x60, 0x78, 0x6c, 0x66, 0x66, 0x66,
			0x66, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc0, 0xc0, 0xc0, 0xc6, 0x7c,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1c, 0x0c, 0x0c, 0x3c, 0x6c, 0xcc, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xfe, 0xc0, 0xc0, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x38, 0x6c, 0x64, 0x60, 0xf0, 0x60, 0x60, 0x60, 0x60, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x76, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x7c, 0x0c, 0xcc, 0x78, 0x00, 0x00, 0x00, 0xe0, 0x60,
			0x60, 0x6c, 0x76, 0x66, 0x66, 0x66, 0x66, 0xe6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00, 0x38,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x06, 0x00, 0x0e, 0x06, 0x06,
			0x06, 0x06, 0x06, 0x06, 0x66, 0x66, 0x3c, 0x00, 0x00, 0x00, 0xe0, 0x60, 0x60, 0x66, 0x6c, 0x78, 0x78, 0x6c,
			0x66, 0xe6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3c,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xe6, 0xff, 0xdb, 0xdb, 0xdb, 0xdb, 0xdb, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xdc, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xdc, 0x66, 0x66, 0x66, 0x66, 0x66, 0x7c, 0x60, 0x60, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x76, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x7c, 0x0c, 0x0c, 0x1e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xdc,
			0x76, 0x66, 0x60, 0x60, 0x60, 0xf0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0xc6, 0x60,
			0x38, 0x0c, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x30, 0x30, 0xfc, 0x30, 0x30, 0x30, 0x30,
			0x36, 0x1c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x76,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3, 0xc3, 0xc3, 0x66, 0x3c, 0x18, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0xc3, 0xc3, 0xdb, 0xdb, 0xff, 0x66, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0xc3, 0x66, 0x3c, 0x18, 0x3c, 0x66, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7e, 0x06, 0x0c, 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0xfe, 0xcc, 0x18, 0x30, 0x60, 0xc6, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e, 0x18, 0x18, 0x18,
			0x70, 0x18, 0x18, 0x18, 0x18, 0x0e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x18, 0x18, 0x00, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0x18, 0x18, 0x18, 0x0e, 0x18, 0x18, 0x18,
			0x18, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x76, 0xdc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x38, 0x6c, 0xc6, 0xc6, 0xc6, 0xfe, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x3c, 0x66, 0xc2, 0xc0, 0xc0, 0xc0, 0xc2, 0x66, 0x3c, 0x0c, 0x06, 0x7c, 0x00, 0x00,
			0x00, 0x00, 0xcc, 0x00, 0x00, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0c,
			0x18, 0x30, 0x00, 0x7c, 0xc6, 0xfe, 0xc0, 0xc0, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x38, 0x6c,
			0x00, 0x78, 0x0c, 0x7c, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xcc, 0x00, 0x00, 0x78,
			0x0c, 0x7c, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x60, 0x30, 0x18, 0x00, 0x78, 0x0c, 0x7c,
			0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0x38, 0x00, 0x78, 0x0c, 0x7c, 0xcc, 0xcc,
			0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c, 0x66, 0x60, 0x60, 0x66, 0x3c, 0x0c, 0x06,
			0x3c, 0x00, 0x00, 0x00, 0x00, 0x10, 0x38, 0x6c, 0x00, 0x7c, 0xc6, 0xfe, 0xc0, 0xc0, 0xc6, 0x7c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0xc6, 0x00, 0x00, 0x7c, 0xc6, 0xfe, 0xc0, 0xc0, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x60, 0x30, 0x18, 0x00, 0x7c, 0xc6, 0xfe, 0xc0, 0xc0, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x66, 0x00, 0x00, 0x38, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x3c, 0x66,
			0x00, 0x38, 0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x60, 0x30, 0x18, 0x00, 0x38,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc6, 0x00, 0x10, 0x38, 0x6c, 0xc6, 0xc6,
			0xfe, 0xc6, 0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0x38, 0x00, 0x38, 0x6c, 0xc6, 0xc6, 0xfe, 0xc6,
			0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x18, 0x30, 0x60, 0x00, 0xfe, 0x66, 0x60, 0x7c, 0x60, 0x60, 0x66, 0xfe,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x6e, 0x3b, 0x1b, 0x7e, 0xd8, 0xdc, 0x77, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x3e, 0x6c, 0xcc, 0xcc, 0xfe, 0xcc, 0xcc, 0xcc, 0xcc, 0xce, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x10, 0x38, 0x6c, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0xc6, 0x00, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x60, 0x30, 0x18,
			0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x78, 0xcc, 0x00, 0xcc,
			0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x60, 0x30, 0x18, 0x00, 0xcc, 0xcc, 0xcc,
			0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc6, 0x00, 0x00, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6,
			0xc6, 0x7e, 0x06, 0x0c, 0x78, 0x00, 0x00, 0xc6, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c,
			0x00, 0x00, 0x00, 0x00, 0x00, 0xc6, 0x00, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x18, 0x18, 0x7e, 0xc3, 0xc0, 0xc0, 0xc0, 0xc3, 0x7e, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x38, 0x6c, 0x64, 0x60, 0xf0, 0x60, 0x60, 0x60, 0x60, 0xe6, 0xfc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0xc3, 0x66, 0x3c, 0x18, 0xff, 0x18, 0xff, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfc, 0x66, 0x66,
			0x7c, 0x62, 0x66, 0x6f, 0x66, 0x66, 0x66, 0xf3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e, 0x1b, 0x18, 0x18, 0x18,
			0x7e, 0x18, 0x18, 0x18, 0x18, 0x18, 0xd8, 0x70, 0x00, 0x00, 0x00, 0x18, 0x30, 0x60, 0x00, 0x78, 0x0c, 0x7c,
			0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0c, 0x18, 0x30, 0x00, 0x38, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x3c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x30, 0x60, 0x00, 0x7c, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x7c,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x30, 0x60, 0x00, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0xcc, 0x76, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x76, 0xdc, 0x00, 0xdc, 0x66, 0x66, 0x66, 0x66, 0x66, 0x66, 0x00, 0x00, 0x00, 0x00,
			0x76, 0xdc, 0x00, 0xc6, 0xe6, 0xf6, 0xfe, 0xde, 0xce, 0xc6, 0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3c,
			0x6c, 0x6c, 0x3e, 0x00, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0x6c,
			0x38, 0x00, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x30, 0x00, 0x30,
			0x30, 0x60, 0xc0, 0xc6, 0xc6, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0xc0,
			0xc0, 0xc0, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x06, 0x06, 0x06,
			0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xc0, 0xc0, 0xc2, 0xc6, 0xcc, 0x18, 0x30, 0x60, 0xce, 0x9b, 0x06,
			0x0c, 0x1f, 0x00, 0x00, 0x00, 0xc0, 0xc0, 0xc2, 0xc6, 0xcc, 0x18, 0x30, 0x66, 0xce, 0x96, 0x3e, 0x06, 0x06,
			0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00, 0x18, 0x18, 0x18, 0x3c, 0x3c, 0x3c, 0x18, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x6c, 0xd8, 0x6c, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xd8, 0x6c, 0x36, 0x6c, 0xd8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, 0x44, 0x11, 0x44,
			0x11, 0x44, 0x11, 0x44, 0x11, 0x44, 0x11, 0x44, 0x11, 0x44, 0x11, 0x44, 0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa,
			0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa, 0x55, 0xaa, 0xdd, 0x77, 0xdd, 0x77, 0xdd, 0x77, 0xdd, 0x77,
			0xdd, 0x77, 0xdd, 0x77, 0xdd, 0x77, 0xdd, 0x77, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0xf8, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0xf8, 0x18, 0xf8, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0xf6, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xf8, 0x18, 0xf8, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x36, 0x36, 0x36, 0x36,
			0x36, 0xf6, 0x06, 0xf6, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x06, 0xf6,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0xf6, 0x06, 0xfe, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0xfe, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x18, 0x18, 0x18, 0xf8, 0x18, 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xf8, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x1f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xff, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x1f, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0xff, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x1f, 0x18, 0x1f, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x37, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x37, 0x30, 0x3f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x3f, 0x30, 0x37, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,
			0x36, 0x36, 0x36, 0xf7, 0x00, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0xff, 0x00, 0xf7, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x37,
			0x30, 0x37, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0x00, 0xff,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x36, 0x36, 0x36, 0x36, 0xf7, 0x00, 0xf7, 0x36, 0x36,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x18, 0x18, 0x18, 0x18, 0x18, 0xff, 0x00, 0xff, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0x00, 0xff, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x3f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x1f, 0x18, 0x1f, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1f,
			0x18, 0x1f, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3f,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0xff, 0x36, 0x36,
			0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x18, 0x18, 0x18, 0x18, 0x18, 0xff, 0x18, 0xff, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1f, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xf0, 0xf0, 0xf0, 0xf0,
			0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0xf0, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f,
			0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x76, 0xdc, 0xd8, 0xd8, 0xd8,
			0xdc, 0x76, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78, 0xcc, 0xcc, 0xcc, 0xd8, 0xcc, 0xc6, 0xc6, 0xc6, 0xcc,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0xc6, 0xc6, 0xc0, 0xc0, 0xc0, 0xc0, 0xc0, 0xc0, 0xc0, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x6c, 0x6c, 0x6c, 0x6c, 0x6c, 0x6c, 0x6c, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0xfe, 0xc6, 0x60, 0x30, 0x18, 0x30, 0x60, 0xc6, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x7e, 0xd8, 0xd8, 0xd8, 0xd8, 0xd8, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x66, 0x66, 0x66, 0x66, 0x66, 0x7c, 0x60, 0x60, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x76, 0xdc,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7e, 0x18, 0x3c, 0x66, 0x66,
			0x66, 0x3c, 0x18, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0xc6, 0xc6, 0xfe, 0xc6, 0xc6,
			0x6c, 0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0xc6, 0xc6, 0xc6, 0x6c, 0x6c, 0x6c, 0x6c, 0xee,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1e, 0x30, 0x18, 0x0c, 0x3e, 0x66, 0x66, 0x66, 0x66, 0x3c, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7e, 0xdb, 0xdb, 0xdb, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x03, 0x06, 0x7e, 0xdb, 0xdb, 0xf3, 0x7e, 0x60, 0xc0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x1c, 0x30, 0x60, 0x60, 0x7c, 0x60, 0x60, 0x60, 0x30, 0x1c, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c,
			0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0xc6, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xfe, 0x00,
			0x00, 0xfe, 0x00, 0x00, 0xfe, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x7e, 0x18,
			0x18, 0x00, 0x00, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x18, 0x0c, 0x06, 0x0c, 0x18, 0x30,
			0x00, 0x7e, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0c, 0x18, 0x30, 0x60, 0x30, 0x18, 0x0c, 0x00, 0x7e,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0e, 0x1b, 0x1b, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18,
			0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0xd8, 0xd8, 0xd8, 0x70, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x18, 0x18, 0x00, 0x7e, 0x00, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x76, 0xdc, 0x00, 0x76, 0xdc, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x38, 0x6c, 0x6c,
			0x38, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x18, 0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x18, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0f, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0xec, 0x6c, 0x6c,
			0x3c, 0x1c, 0x00, 0x00, 0x00, 0x00, 0x00, 0xd8, 0x6c, 0x6c, 0x6c, 0x6c, 0x6c, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0xd8, 0x30, 0x60, 0xc8, 0xf8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

	@Override
	public boolean step(int times) {
		for (int i = 0; i < times && running; i++) {
			if (halted && (registers.FLAGS.intValue() & Registers8086.MASK_IF) == 0) {
				running = false;
				return false;
			}

			if (!step()) {
				return false;
			}
		}
		return running;
	}

	@Override
	public boolean step() {
		//pit.onStep();

		if (masterPic.peek() != null && (registers.FLAGS.intValue() & Registers8086.MASK_IF) != 0) {
			InterruptRequest request = masterPic.consume();
			this.startInterrupt((byte) request.getVector());
			return running;
		}

		if (keyboardController != null && keyboardController.getKeyboard() != null) {
			keyboardController.getKeyboard().handleKeystrokeQueue(this);
		}

		if (registers.IP.intValue() == 0x0BEB && registers.CS.intValue() == 0x9E58) {
			//ipLogging = true;
		}

		Instruction currentInstruction = fetch();
		try {
			if (currentInstruction == null)
				throw new UndefinedOpcodeException(this);
			currentInstruction.decodeAndExecute(this, null);
		} catch (CpuException e) {
			e.startInterrupt();
		}

		executeCount++;
		return running;
	}

	/**
	 * Starts an interrupt.
	 */
	public void startInterrupt(byte vector) {
		startInterrupt(vector, this.registers.IP.shortValue(), this.registers.CS.shortValue(),
				this.registers.FLAGS.shortValue());
	}

	public void attachPS2Keyboard(IPS2Keyboard ps2keyboard) {
		this.keyboardController.setKeyboard(ps2keyboard);
	}

	@Override
	public boolean shouldStep() {
		return this.running && !this.halted;
	}

	@Override
	public boolean isHalted() {
		return this.halted;
	}

	@Override
	public void setHalted(boolean halted) {
		this.halted = halted;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	@Override
	public void setRunning(boolean running) {
		this.running = running;
	}

	@Override
	public void load(IStateStorage stateStorage) throws IOException {
		for (int i = 0; i < memory.length; i++) {
			byte[] memory = stateStorage.getBlob(String.format("memory%X", i)).orElse(null);

			if (memory == null)
				continue;

			this.memory[i].setData(memory);
		}

		for (Register16 register : registers) {
			register.write(stateStorage.getShort(register.getName()).orElse(register.shortValue()));
		}

		setHalted(stateStorage.getBoolean("halted").orElse(isHalted()));
		setRunning(stateStorage.getBoolean("running").orElse(isRunning()));

		for (IStateful device : statefulDevices) {
			device.load(stateStorage);
		}
	}

	@Override
	public void save(IStateStorage stateStorage) throws IOException {
		for (int i = 0; i < memory.length; i++) {
			stateStorage.setBlob(String.format("memory%X", i), memory[i].getData());
		}

		for (Register16 register : registers) {
			stateStorage.set(register.getName(), register.shortValue());
		}

		stateStorage.set("halted", isHalted());
		stateStorage.set("running", isRunning());

		for (IStateful device : statefulDevices) {
			device.save(stateStorage);
		}
	}

	@Override
	public void deleteSaved(IStateStorage storage) throws IOException {
		for (int i = 0; i < memory.length; i++) {
			storage.deleteBlob(String.format("memory%X", i));
		}
	}
}
