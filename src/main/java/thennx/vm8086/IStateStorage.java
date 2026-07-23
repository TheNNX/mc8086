package thennx.vm8086;

import java.io.IOException;
import java.util.Optional;

public interface IStateStorage {
    Optional<Boolean> getBoolean(String name) throws IOException;
    void set(String name, Boolean b) throws IOException;

    Optional<Short> getShort(String name) throws IOException;
    void set(String name, Short s) throws IOException;

    Optional<Byte> getByte(String name) throws IOException;
    void set(String name, Byte b) throws IOException;

    Optional<Integer> getInt(String name) throws IOException;
    void set(String name, Integer i) throws IOException;

    Optional<Long> getLong(String name) throws IOException;
    void set(String name, Long l) throws IOException;

    Optional<String> getString(String name) throws IOException;
    void set(String name, String s) throws IOException;

    Optional<byte[]> getBlob(String name) throws IOException;
    void setBlob(String name, byte[] memory) throws IOException;

    boolean containsBoolean(String name);
    boolean containsByte(String name);
    boolean containsShort(String name);
    boolean containsInt(String name);
    boolean containsLong(String name);
    boolean containsString(String name);
    boolean containsBlob(String name);

    Optional<IStateStorage> getSubtag(String name) throws IOException;
    Optional<IStateStorage> createSubtag(String name) throws IOException;

    void deleteBlob(String format) throws IOException;
}
