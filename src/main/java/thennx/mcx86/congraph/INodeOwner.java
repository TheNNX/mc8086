package thennx.mcx86.congraph;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public interface INodeOwner {
    Node<?> getNode();
    void notifyNeighbourChange(Direction direction, @Nullable INodeOwner newNeighbour, @Nullable INodeOwner prevNeighbour);
}
