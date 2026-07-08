package thennx.vm8086.devices;

import thennx.vm8086.IStateStorage;

import java.io.IOException;
import java.nio.file.Path;

public interface IStateful {
    void load(IStateStorage stateStorage) throws IOException;

    void save(IStateStorage stateStorage) throws IOException;

    void deleteSaved(IStateStorage stateStorage) throws IOException;
}
