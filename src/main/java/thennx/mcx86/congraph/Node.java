package thennx.mcx86.congraph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.*;

public class Node<T extends INodeOwner> {
    private T owner = null;
    private Node<?>[] neighbours = new Node<?>[6];

    public Node(T owner) {
        this.owner = owner;
    }

    public T getOwner() {
        return this.owner;
    }

    public Node<?> getNeighbour(Direction direction) {
        return neighbours[direction.ordinal()];
    }

    public void setNeighbour(Direction direction, @Nullable Node<?> value) {
        Node<?> prevNeighbour = neighbours[direction.ordinal()];

        neighbours[direction.ordinal()] = value;
        if (value != null) {
            Node<?> prevValuesNeighbour = value.getNeighbour(direction.getOpposite());
            value.neighbours[direction.getOpposite().ordinal()] = this;
            value.getOwner().notifyNeighbourChange(direction.getOpposite(), this.getOwner(), prevValuesNeighbour == null ? null : prevValuesNeighbour.getOwner());
        }
        getOwner().notifyNeighbourChange(direction, value == null ? null : value.getOwner(), prevNeighbour == null ? null : prevNeighbour.getOwner());
    }

    public void removeNeighbours() {
        for (Direction direction : Direction.values()) {
            setNeighbour(direction, null);
        }
    }

    public void detectNeighbours(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.offset(direction.getNormal());
            BlockState neighbourState = level.getBlockState(neighbourPos);

            if (neighbourState.hasBlockEntity() && level.getBlockEntity(neighbourPos) instanceof INodeOwner neighbourNodeOwner) {
                Node<?> neighbourNode = neighbourNodeOwner.getNode();
                if (neighbourNode != getNeighbour(direction)) {
                    setNeighbour(direction, neighbourNode);
                }
            }
            else if (getNeighbour(direction) != null){
                setNeighbour(direction, null);
            }
        }
    }

    public List<INodeOwner> findInNetwork(Class<?> type) {
        ArrayList<INodeOwner> result = new ArrayList<>();
        Deque<INodeOwner> queue = new ArrayDeque<>();
        Set<INodeOwner> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        queue.add(getOwner());
        visited.add(getOwner());

        while (!queue.isEmpty()) {
            INodeOwner current = queue.removeFirst();

            if (type.isInstance(current)) {
                result.add(current);
            }

            for (Direction d : Direction.values()) {
                Node<?> neighbour = current.getNode().getNeighbour(d);
                if (neighbour != null && !visited.contains(neighbour.getOwner())) {
                    queue.add(neighbour.getOwner());
                    visited.add(neighbour.getOwner());
                }
            }
        }

        return result;
    }
}
