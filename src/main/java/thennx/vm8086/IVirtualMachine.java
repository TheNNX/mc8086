package thennx.vm8086;

import thennx.vm8086.devices.IStateful;

public interface IVirtualMachine extends IStateful {
    void run();

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

    short readMemoryShortPhysical(int physicalAddress);

    void writeMemoryBytePhysical(int physicalAddress, byte b);

    void writeMemoryShortPhysical(int physicalAddress, short w);

    long getFrequencyHz();
}
