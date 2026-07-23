package thennx.vm8086;

import thennx.vm8086.devices.ATAChannel;
import thennx.vm8086.devices.IDevice;
import thennx.vm8086.devices.IStateful;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public interface IVirtualMachine extends IStateful {
    boolean step();

    /**
     * Executes a given number of instructions.
     *
     * @return false if the execution was terminated whilst executing the
     *         instructions.
     */
    boolean step(int times);

    boolean isHalted();

    boolean isRunning();

    boolean shouldStep();

    void setRunning(boolean running);

    void setHalted(boolean halted);

    byte readMemoryBytePhysical(int physicalAddress);

    void writeMemoryBytePhysical(int physicalAddress, byte b);

    long getFrequencyHz();

    Map<String, IDevice> getDevices();

    <T extends IDevice> Map<String, T> getDevices(Class<T> clazz);

    boolean tryAddDevice(String key, IDevice device);

    @Nullable IDevice getDevice(String key);

    @Nullable IDevice tryRemoveDevice(String key);

    void restart();
}
