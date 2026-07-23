package thennx.vm8086.devices;

import thennx.vm8086.IVirtualMachine;

public interface IDevice {
    default void initialise() {}
    default void onAdded(IVirtualMachine machine) {}
    default void onRemoved(IVirtualMachine machine) {}
}
