package thennx.vm8086.devices;

import thennx.vm8086.IVirtualMachine;

public interface IDevice {
    default void initialise() {}
    default boolean onAdded(IVirtualMachine machine) { return true; }
    default void onRemoved(IVirtualMachine machine) {}
}
