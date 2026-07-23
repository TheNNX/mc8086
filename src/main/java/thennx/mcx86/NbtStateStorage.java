package thennx.mcx86;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.checkerframework.checker.units.qual.C;
import thennx.vm8086.IStateStorage;

import java.io.IOException;
import java.util.Optional;

public class NbtStateStorage implements IStateStorage {
    private final CompoundTag tag;

    public NbtStateStorage(CompoundTag tag) {
        this.tag = tag;
    }

    public CompoundTag getTag() {
        return tag;
    }

    @Override
    public Optional<Boolean> getBoolean(String name) throws IOException {
        if (!tag.contains(name))
            return Optional.empty();
        return Optional.of(tag.getBoolean(name));
    }

    @Override
    public void set(String name, Boolean b) throws IOException {
        if (b == null) {
            tag.remove(name);
            return;
        }
        tag.putBoolean(name, b);
    }

    @Override
    public Optional<Short> getShort(String name) {
        if (!tag.contains(name, Tag.TAG_SHORT))
            return Optional.empty();
        return Optional.of(tag.getShort(name));
    }

    @Override
    public void set(String name, Short s) {
        if (s == null) {
            tag.remove(name);
            return;
        }
        tag.putShort(name, s);
    }

    @Override
    public Optional<Byte> getByte(String name) {
        if (!tag.contains(name, Tag.TAG_BYTE))
            return Optional.empty();
        return Optional.of(tag.getByte(name));
    }

    @Override
    public void set(String name, Byte b) {
        if (b == null) {
            tag.remove(name);
            return;
        }
        tag.putByte(name, b);
    }

    @Override
    public Optional<Integer> getInt(String name) {
        if (!tag.contains(name, Tag.TAG_INT))
            return Optional.empty();
        return Optional.of(tag.getInt(name));
    }

    @Override
    public void set(String name, Integer i) {
        if (i == null) {
            tag.remove(name);
            return;
        }
        tag.putInt(name, i);
    }

    @Override
    public Optional<Long> getLong(String name) {
        if (!tag.contains(name, Tag.TAG_LONG))
            return Optional.empty();
        return Optional.of(tag.getLong(name));
    }

    @Override
    public void set(String name, Long l) {
        if (l == null) {
            tag.remove(name);
            return;
        }
        tag.putLong(name, l);
    }

    @Override
    public Optional<String> getString(String name) {
        if (!tag.contains(name, Tag.TAG_STRING))
            return Optional.empty();
        return Optional.of(tag.getString(name));
    }

    @Override
    public void set(String name, String s) {
        if (s == null) {
            tag.remove(name);
            return;
        }
        tag.putString(name, s);
    }

    @Override
    public Optional<byte[]> getBlob(String name) throws IOException {
        return Optional.empty();
    }

    @Override
    public void setBlob(String name, byte[] memory) throws IOException {
    }

    @Override
    public boolean containsBoolean(String name) {
        return tag.contains(name);
    }

    @Override
    public boolean containsByte(String name) {
        return tag.contains(name, Tag.TAG_BYTE);
    }

    @Override
    public boolean containsShort(String name) {
        return tag.contains(name, Tag.TAG_SHORT);
    }

    @Override
    public boolean containsInt(String name) {
        return tag.contains(name, Tag.TAG_INT);
    }

    @Override
    public boolean containsLong(String name) {
        return tag.contains(name, Tag.TAG_LONG);
    }

    @Override
    public boolean containsString(String name) {
        return tag.contains(name, Tag.TAG_STRING);
    }

    @Override
    public boolean containsBlob(String name) {
        return false;
    }

    @Override
    public Optional<IStateStorage> getSubtag(String name) throws IOException {
        return Optional.of(new NbtStateStorage(this.tag.getCompound(name)));
    }

    @Override
    public Optional<IStateStorage> createSubtag(String name) throws IOException {
        CompoundTag tag = new CompoundTag();
        this.tag.put(name, tag);
        return Optional.of(new NbtStateStorage(tag));
    }

    @Override
    public void deleteBlob(String format) {}
}
