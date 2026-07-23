package thennx.vm8086.devices;

import thennx.vm8086.IVirtualMachine;

public interface IDevice {
    default void initialise() {}
    default boolean onAdded(IVirtualMachine machine, String key) { return true; }
    default void onRemoved(IVirtualMachine machine, String key) {}

    default void onOtherAdded(IVirtualMachine machine, String selfKey, String addedKey, IDevice added) {}
    default void onOtherRemoved(IVirtualMachine machine, String selfKey, String removedKey, IDevice removed) {}
}
