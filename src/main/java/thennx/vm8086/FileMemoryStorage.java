package thennx.vm8086;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public class FileMemoryStorage implements IStateStorage {
    private final Path memoryStorageDirPath;

    public FileMemoryStorage(Path memoryStorageDirPath) {
        this.memoryStorageDirPath = memoryStorageDirPath;
    }

    public Path getMemoryStorageDirPath() {
        return memoryStorageDirPath;
    }

    @Override
    public Optional<Boolean> getBoolean(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, Boolean b) throws IOException {
    }

    @Override
    public Optional<Short> getShort(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, Short s) throws IOException {
    }

    @Override
    public Optional<Byte> getByte(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, Byte b) throws IOException {
    }

    @Override
    public Optional<Integer> getInt(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, Integer i) throws IOException {
    }

    @Override
    public Optional<Long> getLong(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, Long l) throws IOException {
    }

    @Override
    public Optional<String> getString(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void set(String name, String s) throws IOException {
    }

    @Override
    public Optional<byte[]> getBlob(String name) throws IOException {
        Path memFilePath = getMemoryStorageDirPath().resolve(name + ".dat");
        if (Files.notExists(memFilePath))
            return Optional.empty();
        return Optional.of(Files.readAllBytes(memFilePath));
    }

    @Override
    public void setBlob(String name, byte[] memory) throws IOException {
        Path memFilePath = getMemoryStorageDirPath().resolve(name + ".dat");
        if (memory == null) {
            Files.deleteIfExists(memFilePath);
            return;
        }
        Files.write(memFilePath, memory, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    @Override
    public boolean containsBoolean(String name) {
        return false;
    }

    @Override
    public boolean containsByte(String name) {
        return false;
    }

    @Override
    public boolean containsShort(String name) {
        return false;
    }

    @Override
    public boolean containsInt(String name) {
        return false;
    }

    @Override
    public boolean containsLong(String name) {
        return false;
    }

    @Override
    public boolean containsString(String name) {
        return false;
    }

    @Override
    public boolean containsBlob(String name) {
        Path memFilePath = getMemoryStorageDirPath().resolve(name + ".dat");
        return Files.exists(memFilePath);
    }

    @Override
    public void deleteBlob(String name) throws IOException {
        Path memFilePath = getMemoryStorageDirPath().resolve(name + ".dat");
        Files.deleteIfExists(memFilePath);
    }
}
