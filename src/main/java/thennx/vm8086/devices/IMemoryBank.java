package thennx.vm8086.devices;

public interface IMemoryBank {
    int BANK_SIZE = 65536;

    byte getByte(int offset);
    void setByte(int offset, byte b);
    byte[] getData();
    void setData(byte[] data);

    boolean isReadonly();
}
