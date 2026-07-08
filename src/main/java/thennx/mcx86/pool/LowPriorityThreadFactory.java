package thennx.mcx86.pool;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadFactory;

public class LowPriorityThreadFactory implements ThreadFactory {
    private final ThreadGroup threadGroup = new ThreadGroup("WORKER");

    public LowPriorityThreadFactory() {
        threadGroup.setMaxPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(threadGroup, r);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }
}
