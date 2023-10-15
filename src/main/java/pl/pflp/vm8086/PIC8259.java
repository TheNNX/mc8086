package pl.pflp.vm8086;

import net.minecraft.nbt.CompoundTag;

/**
 * Implements the PIC8259. 
 * Only 8086 mode is supported. 
 */

public class PIC8259 implements IPortSpaceDevice, InterruptSource {

	private enum Mode {
		Mode8086
	}
	
	private int startVector;
	private int interruptMask = 0;
	private int inService = 0;
	private boolean single;
	private int initStep = 0;
	private boolean icw4Required;
	private byte commandRead = 0;
	private short portBase;
	
	private Mode mode = Mode.Mode8086;
	
	private InterruptSource[] sources = new InterruptSource[8];
	private int icw3 = 0;
	
	public PIC8259(short portBase, int startVector, VM8086 vm) {
		this.startVector = startVector;
		this.portBase = portBase;
		this.single = false;
	}
	
	@Override
	public boolean matchPort(short port) {
		return port == portBase || port == portBase + 1;
	}

	private void handleInit(byte data) {
		switch (initStep) {
		case 1:
			this.startVector = data;
			if (single && !icw4Required)
				this.initStep = 0;
			else if (single && icw4Required)
				this.initStep = 3;
			else 
				this.initStep = 2;
			break;
		case 2:
			icw3 = data;
				
			if (this.icw4Required == false)
				this.initStep = 0;
			else
				this.initStep = 3;
			break;
		case 3:
			/* TODO */
			if ((data & 1) != 0)
				this.mode = Mode.Mode8086;
			else
				System.out.println("Warning: unsuported PIC mode. ");
			break;
		}
	}
	
	private void handleCommandWrite(byte data) {
		if ((data & 0x10) != 0) {
			initStep = 1;
			icw4Required =  ((data & 0x1) != 0);
			single = ((data & 0x2) != 0);
		}
		else if (data == 0x20) {
			onEoi();
		}
		else if (data == 0x0A) {
			this.commandRead = this.readIrr();
		}
		else if (data == 0x0B) {
			this.commandRead = (byte)this.inService;
		}
	}
	
	public void handleDataWrite(byte data) {
		this.interruptMask = data;
	}
	
	public byte handleDataRead() {
		return (byte)this.interruptMask;
	}
	
	@Override
	public void writeByte(short port, byte data) {
		boolean isData = (port & 1) != 0;
		
		if (isData) {
			handleDataWrite(data);
		}
		/* Command */
		else {
			if (initStep != 0) {
				handleInit(data);
				return;
			}
			handleCommandWrite(data);
		}

	}
	
	public byte handleCommandRead() {
		byte result = this.commandRead;
		this.commandRead = (byte)0xFF;
		return result;
	}
	
	@Override
	public byte readByte(short port) {
		boolean isData = (port & 1) != 0;
		
		if (isData) {
			return handleDataRead();
		}
		else {
			return handleCommandRead();
		}
	}

	@Override
	public void load(CompoundTag tag) {
		// TODO Auto-generated method stub

	}

	@Override
	public void save(CompoundTag tag) {
		// TODO Auto-generated method stub

	}
	
	private void onEoi() {
		for (int mask = 0x80; mask >= 1; mask = mask >> 1) {
			if ((inService & mask) != 0) {
				inService &= ~mask;
				break;
			}
		}
	}
	
	private void moveToService(byte input) {
		int mask = (1 << input);
		inService |= mask;
	}
	
	private boolean isRequestValid(int i) {
		if( sources[i] != null && sources[i].peek() != null)
			return (inService & ((1 << (i + 1)) - 1)) == 0;
		return false;
	}

	private byte readIrr() {
		int mask = 0;
		
		for (int i = 0; i < this.sources.length; i++)
			if (sources[i] != null && this.sources[i].peek() != null)
				mask |= (1 << i);
		
		return (byte) mask;
	}
	
	@Override
	public InterruptRequest consume() {		
		for (int i = 0; i < sources.length; i++) {
			if (isRequestValid(i)) {
				moveToService((byte)i);
				InterruptRequest result = sources[i].consume();
				result.assignVector(i + this.startVector);
				return result;
			}
		}
		return null;
	}

	@Override
	public InterruptRequest peek() {
		for (int i = 0; i < sources.length; i++)
			if (isRequestValid(i))
				return sources[i].peek();
			
		return null;
	}

	public static PIC8259 createMaster(VM8086 vm8086) {
		return new PIC8259((byte)0x20, 0x08, vm8086);
	}
	
	public static PIC8259 createSlave(VM8086 vm8086) {
		return new PIC8259((byte)0xA0, 0x70, vm8086);
	}

	public void connect(int i, InterruptSource source) {
		this.sources[i] = source;
	}
}
