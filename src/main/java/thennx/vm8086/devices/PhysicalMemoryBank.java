package thennx.vm8086.devices;

public class PhysicalMemoryBank implements IMemoryBank {
    private boolean readonly = false;
    private byte[] data = new byte[IMemoryBank.BANK_SIZE];

    public PhysicalMemoryBank(boolean readonly, byte[] initialData) {
        this.readonly = readonly;
        if (initialData.length > data.length)
            throw new IllegalArgumentException("Data too large for the memory bank");
        System.arraycopy(initialData, 0, this.data, 0, initialData.length);
    }

    public PhysicalMemoryBank() {
    }

    @Override
    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    @Override
    public byte getByte(int offset) {
        return data[offset];
    }

    @Override
    public void setByte(int offset, byte b) {
        if (!readonly) {
            data[offset] = b;
        }
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

    @Override
    public void setData(byte[] data) {
        System.arraycopy(data, 0, this.data, 0, data.length);
    }
}
