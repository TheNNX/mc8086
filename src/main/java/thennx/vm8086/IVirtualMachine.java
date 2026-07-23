package thennx.vm8086;

import thennx.vm8086.devices.IDevice;
import thennx.vm8086.devices.IPortSpaceDevice;
import thennx.vm8086.devices.IStateful;

import java.util.List;

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

    List<IPortSpaceDevice> getDevices();

    boolean tryAddDevice(IDevice device);

    boolean tryRemoveDevice(IDevice device);

    void restart();
}
