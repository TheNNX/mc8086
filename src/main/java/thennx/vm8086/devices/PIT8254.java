package thennx.vm8086.devices;

import thennx.vm8086.IStateStorage;

import java.util.ArrayDeque;
import java.util.Deque;

/* TODO: gate input and related modes unimplemented */
/* TODO: bcd mode unimplemented */
public class PIT8254 implements IPortSpaceDevice, IStateful, IInterruptSource {
    private static final long BASE_FREQUENCY_HZ = 1193182;
    private long currentNanoTime = System.nanoTime();

    class Channel {
        public void handleCommand(byte data) {
            switch (accessMode) {
                case 0:
                    latchCounter();
                    break;
                case 1:
                    buffer[0] = data;
                    break;
                case 2:
                    buffer[1] = data;
                    break;
                case 3:
                    if (buffer[0] == null) buffer[0] = data;
                    buffer[1] = data;
                    break;
            }

            if (buffer[0] != null && buffer[1] != null) {
                setReloadValue((buffer[1] << 8) | buffer[0]);
                buffer[0] = buffer[1] = null;
            }
        }

        public byte readData() {
            if (latchRegister.isEmpty()) {
                latchCounter();
            }

            return latchRegister.removeFirst();
        }

        private void latchCounter() {
            latchRegister.clear();
            latchRegister.add((byte) (currentCount & 0xFF));
            latchRegister.add((byte) ((currentCount >> 8) & 0xFF));
        }

        enum Mode{
            TerminalCount,
            OneShot,
            Rate,
            Square,
            SoftStrobe,
            HardStrobe
        }

        private final Deque<Byte> latchRegister = new ArrayDeque<>();
        private final Byte[] buffer = new Byte[] {null, null};
        private int freqDivider = 1;
        private Mode mode = Mode.Square;
        boolean bcdMode = false;
        int accessMode = 3;
        int reloadValue = 0;
        int currentCount = 0;
        protected boolean output = false;
        private boolean waiting = false;

        private long endNanoTime = currentNanoTime;

        Channel(int freqDivider) {
            setReloadValue(freqDivider);
        }

        Mode getMode() {
            return mode;
        }

        private long getFrequency() {
            if (mode == Mode.Square) {
                return 2 * BASE_FREQUENCY_HZ;
            }
            return BASE_FREQUENCY_HZ;
        }

        private void updateEndNanoTime() {
            endNanoTime = currentNanoTime + currentCount * (1000000000 / getFrequency());
        }

        int getFreqDivider() {
            return freqDivider;
        }

        boolean getBcdMode() {
            return bcdMode;
        }

        void setBcdMode(boolean bcdMode) {
            this.bcdMode = bcdMode;
        }

        void setMode(int modeNumber) {
            mode = switch (modeNumber) {
                case 0 -> Mode.TerminalCount;
                case 1 -> Mode.OneShot;
                case 2, 6 -> Mode.Rate;
                case 3, 7 -> Mode.Square;
                case 4 -> Mode.SoftStrobe;
                case 5 -> Mode.HardStrobe;
                default -> mode;
            };

            waiting = true;
        }

        long getCurrentCount() {
            if (mode == Mode.Square)
                return currentCount & ~1;
            return currentCount;
        }

        int getReloadValue () {
            return reloadValue;
        }

        void setReloadValue(int value) {
            reloadValue = value;

            if (mode != Mode.OneShot) {
                waiting = false;
            }

            if (mode != Mode.Rate && mode != Mode.Square) {
                currentCount = reloadValue;
            }
        }

        boolean getOutput() {
            return this.output;
        }

        void updateCount() {
            currentCount = (int) ((endNanoTime - currentNanoTime) / (1000000000 / getFrequency()));

            switch (mode) {
                case TerminalCount, OneShot:
                    if (currentCount <= 0 && !waiting) {
                        output = true;
                        waiting = true;
                    }
                    break;
                case Rate:
                    if (currentCount <= 1 && !waiting) {
                        output = false;
                        currentCount = getReloadValue();
                        updateEndNanoTime();
                    }
                    else {
                        output = true;
                    }
                    break;
                case Square:
                    if (currentCount <= 2 && !waiting) {
                        output = !output;
                        currentCount = getReloadValue();
                        updateEndNanoTime();
                    }
                    break;
                case SoftStrobe:
                    if (currentCount <= 0 && !waiting) {
                        output = false;
                        waiting = true;
                    }
                    else {
                        output = true;
                    }
                    break;
            }
        }
    }

    class Channel0 extends Channel implements IInterruptSource {
        public Channel0(int freqDivider) {
            super(freqDivider);
        }

        private int interruptsQueued = 0;
        private boolean lastOutput = false;

        private final InterruptRequest request = new InterruptRequest(this);

        @Override
        public InterruptRequest consume() {
            if (interruptsQueued > 0) {
                interruptsQueued--;
                return request;
            }

            return null;
        }

        @Override
        public InterruptRequest peek() {
            if (interruptsQueued > 0) {
                return request;
            }

            return null;
        }

        @Override
        void updateCount() {
            super.updateCount();
            if (output != lastOutput && !lastOutput) {
                interruptsQueued++;
            }
            lastOutput = output;
        }
    }

    private final Channel0 channel0 = new Channel0(65536);
    private final Channel[] channels = new Channel[]{channel0, new Channel(1), new Channel(1) };

    @Override
    public boolean matchPort(short port) {
        return port >= 0x40 && port <= 0x43;
    }

    void handleCommandPort(byte data) {

    }

    @Override
    public void writeByte(short port, byte data) {
        switch (port){
            case 0x43 -> handleCommandPort(data);
            case 0x40 -> channels[0].handleCommand(data);
            case 0x41 -> channels[1].handleCommand(data);
            case 0x42 -> channels[2].handleCommand(data);
        }
    }

    @Override
    public byte readByte(short port) {
        return switch (port){
            case 0x40 -> channels[0].readData();
            case 0x41 -> channels[1].readData();
            case 0x42 -> channels[2].readData();
            default -> (byte)0xFF;
        };
    }

    @Override
    public void load(IStateStorage stateStorage) {

    }

    @Override
    public void save(IStateStorage stateStorage) {

    }

    @Override
    public void deleteSaved(IStateStorage stateStorage) {

    }

    @Override
    public InterruptRequest consume() {
        return channel0.consume();
    }

    @Override
    public InterruptRequest peek() {
        return channel0.peek();
    }

    public void onStep() {
        currentNanoTime = System.nanoTime();

        for (Channel channel : channels) {
            channel.updateCount();
        }
    }
}
