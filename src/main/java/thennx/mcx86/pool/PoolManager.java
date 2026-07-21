package thennx.mcx86.pool;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.IVirtualMachine;

import java.util.*;
import java.util.concurrent.*;

public class PoolManager {
    private final ScheduledThreadPoolExecutor executor;
    private final IdentityHashMap<ComputerBlockEntity, PoolJob> registered = new IdentityHashMap<>();
    private static final double FREQ_SCALE = 0.8;
    private int emulationQuantum = 50;

    private static final PoolManager INSTANCE = new PoolManager(1);

    public static PoolManager getInstance() {
        return INSTANCE;
    }

    private class PoolJob implements Runnable {
        private final ComputerBlockEntity blockEntity;
        private long lastTimeMs = System.currentTimeMillis();
        public boolean canceled = false;

        public PoolJob(ComputerBlockEntity blockEntity) {
            this.blockEntity = blockEntity;
        }

        @Override
        public void run() {
            synchronized (this) {
                long currentTimeMs = System.currentTimeMillis();
                long deltaMs = currentTimeMs - lastTimeMs;

                if (deltaMs > 2L * emulationQuantum) {
                    deltaMs = 2L * emulationQuantum;
                }

                lastTimeMs = currentTimeMs;
                synchronized (registered) {
                    if (!registered.containsKey(blockEntity))
                        return;
                }

                IVirtualMachine vm = blockEntity.getVM();
                if (vm == null) {
                    return;
                }

                int steps;
                synchronized (vm) {
                    steps = (int) (FREQ_SCALE * deltaMs * ((double) vm.getFrequencyHz() / 1000));
                }

                /* Split up the synchronization on vm */
                for (int i = 0; i < 10 && !canceled; i++) {
                    synchronized (vm) {
                        for (int j = 0; j < steps / 10 && !canceled; j++) {
                            vm.step();
                        }
                    }
                }
            }
            blockEntity.setChanged();
        }
    }

    private PoolManager(int numThreads) {
        executor = new ScheduledThreadPoolExecutor(numThreads, new LowPriorityThreadFactory());
    }

    public void registerBlockEntity(ComputerBlockEntity computerBlockEntity) {
        synchronized (registered) {
            if (!registered.containsKey(computerBlockEntity)) {
                PoolJob job = new PoolJob(computerBlockEntity);
                registered.put(computerBlockEntity, job);
                executor.scheduleAtFixedRate(job, 0, emulationQuantum, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void unregisterBlockEntity(ComputerBlockEntity computerBlockEntity) {
        PoolJob job = null;

        synchronized (registered) {
            job = registered.remove(computerBlockEntity);
            if (job == null) {
                return;
            }
            job.canceled = true;
        }

        synchronized (job) {
            executor.remove(job);
        }
    }

    public void resizePool(int size) {
        executor.setCorePoolSize(size);
        executor.setMaximumPoolSize(size);
    }

    public int getPoolSize() {
        return executor.getCorePoolSize();
    }

    public void changeQuantum(int emulationQuantum) {
        this.emulationQuantum = emulationQuantum;
        Set<PoolJob> set = Collections.newSetFromMap(new IdentityHashMap<>());

        synchronized (registered) {
            set.addAll(registered.values());
        }

        for (PoolJob job : set) {
            synchronized (job) {
                executor.remove(job);
                executor.scheduleAtFixedRate(job, 0, emulationQuantum, TimeUnit.MILLISECONDS);
            }
        }
    }
}
