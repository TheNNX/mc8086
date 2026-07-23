package thennx.vm8086;

import java.nio.file.Path;

public class AbsolutePathProvider implements IPathProvider {
    private final Path path;

    public AbsolutePathProvider(Path path) {
        this.path = path;
    }

    @Override
    public Path getPath() {
        return path;
    }
}