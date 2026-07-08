package thennx.vm8086;

import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import thennx.vm8086.devices.DummyIdeDrive;
import thennx.vm8086.devices.PS2Keyboard;

public class TestVM extends Frame {

	private BufferedImage screen = new BufferedImage(80 * 9, 25 * 16, BufferedImage.TYPE_INT_ARGB);
	JLabel label;
	
	class ScreenUpdate extends Thread {
		private final VM8086 vm;
		
		public ScreenUpdate(VM8086 vm) {
			this.vm = vm;
		}
		
		@Override
		public void run() {
			while (this.isInterrupted() == false) {
				screenUpdate(vm);
			}
			screenUpdate(vm);
		}
	}

	VM8086 vm8086 = null;
	private TestVM() throws IOException {
		PS2Keyboard keyboard = new PS2Keyboard();

		this.setLayout(null);
		this.setSize(700, 500);
		this.setVisible(true);
		this.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}

			@Override
			public void windowClosed(WindowEvent e) {

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}
		});

		this.addKeyListener(new KeyListener() {

			private static int getPs2Scancode(KeyEvent event) {
                return switch (event.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> 0x01;
                    case KeyEvent.VK_1 -> 0x02;
                    case KeyEvent.VK_2 -> 0x03;
                    case KeyEvent.VK_3 -> 0x04;
                    case KeyEvent.VK_4 -> 0x05;
                    case KeyEvent.VK_5 -> 0x06;
                    case KeyEvent.VK_6 -> 0x07;
                    case KeyEvent.VK_7 -> 0x08;
                    case KeyEvent.VK_8 -> 0x09;
                    case KeyEvent.VK_9 -> 0x0A;
                    case KeyEvent.VK_0 -> 0x0B;
                    case KeyEvent.VK_MINUS -> 0x0C;
                    case KeyEvent.VK_PLUS -> 0x0D;
                    case KeyEvent.VK_BACK_SPACE -> 0x0E;
                    case KeyEvent.VK_TAB -> 0x0F;
                    case KeyEvent.VK_Q -> 0x10;
                    case KeyEvent.VK_W -> 0x11;
                    case KeyEvent.VK_E -> 0x12;
                    case KeyEvent.VK_R -> 0x13;
                    case KeyEvent.VK_T -> 0x14;
                    case KeyEvent.VK_Y -> 0x15;
                    case KeyEvent.VK_U -> 0x16;
                    case KeyEvent.VK_I -> 0x17;
                    case KeyEvent.VK_O -> 0x18;
                    case KeyEvent.VK_P -> 0x19;
                    case KeyEvent.VK_OPEN_BRACKET -> 0x1A;
                    case KeyEvent.VK_CLOSE_BRACKET -> 0x1B;
                    case KeyEvent.VK_ENTER -> 0x1C;
                    case KeyEvent.VK_CONTROL -> 0x1D;
                    case KeyEvent.VK_A -> 0x1E;
                    case KeyEvent.VK_S -> 0x1F;
                    case KeyEvent.VK_D -> 0x20;
                    case KeyEvent.VK_F -> 0x21;
                    case KeyEvent.VK_G -> 0x22;
                    case KeyEvent.VK_H -> 0x23;
                    case KeyEvent.VK_J -> 0x24;
                    case KeyEvent.VK_K -> 0x25;
                    case KeyEvent.VK_L -> 0x26;
                    case KeyEvent.VK_SEMICOLON -> 0x27;
                    case KeyEvent.VK_QUOTE -> 0x28;
                    case KeyEvent.VK_BACK_QUOTE -> 0x29;
                    case KeyEvent.VK_SHIFT -> {
                        if (event.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT)
                            yield 0x2A;
                        yield 0x36;
                    }
                    case KeyEvent.VK_Z -> 0x2C;
                    case KeyEvent.VK_X -> 0x2D;
                    case KeyEvent.VK_C -> 0x2E;
                    case KeyEvent.VK_V -> 0x2F;
                    case KeyEvent.VK_B -> 0x30;
                    case KeyEvent.VK_N -> 0x31;
                    case KeyEvent.VK_M -> 0x32;
                    case KeyEvent.VK_COMMA -> 0x33;
                    case KeyEvent.VK_PERIOD -> 0x34;
                    case KeyEvent.VK_SLASH -> 0x35;
                    case KeyEvent.VK_SPACE -> 0x39;
                    case KeyEvent.VK_CAPS_LOCK -> 0x3A;
                    case KeyEvent.VK_DOWN -> 0x50;
                    case KeyEvent.VK_UP -> 0x48;
                    case KeyEvent.VK_RIGHT -> 0x4D;
                    case KeyEvent.VK_LEFT -> 0x4B;
                    default -> 0x1E;
                };
			}

			@Override
			public void keyTyped(KeyEvent e) {
				// TODO Auto-generated method stub
			}

			@Override
			public void keyReleased(KeyEvent e) {
				keyboard.queueKeystroke(getPs2Scancode(e), e.getKeyChar(), false);
			}

			@Override
			public void keyPressed(KeyEvent e) {
				keyboard.queueKeystroke(getPs2Scancode(e), e.getKeyChar(), true);
			}
		});

		for (int i = 0; i < 80 * 9; i++) {
			for (int j = 0; j < 25 * 16; j++) {
				screen.setRGB(i, j, 0xFF00FF00);
			}
		}

		screen.flush();
		label = new JLabel(new ImageIcon(screen));
		this.add(label);
		label.setVisible(true);
		Insets ins = this.getInsets();
		label.setLocation(ins.left + 5, ins.top + 5);
		label.setSize(80 * 9, 25 * 16);

		byte[] bios;

		File biosFile = new File("C:\\Users\\Marcin\\Desktop\\oc86boot\\bios.bin");
		bios = Files.readAllBytes(biosFile.toPath());

		vm8086 = new VM8086(1024 * 1024, bios);
		vm8086.attachPS2Keyboard(keyboard);
		vm8086.attachIdeDevice(0, false, new DummyIdeDrive(vm8086, true));
		// vm8086.addDebugPorts();

		ScreenUpdate screenUpdate = new ScreenUpdate(vm8086);
		screenUpdate.setPriority(Thread.MIN_PRIORITY);
		screenUpdate.start();

		while (vm8086.isRunning()) {
			vm8086.step(1);
		}

		screenUpdate.interrupt();
		try {
			screenUpdate.join();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		TestVM window = new TestVM();

		System.out.println("Died");
	}

	private void screenUpdate(VM8086 vm) {
		int[] egaColors = { 0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFAA5500,
				0xFFAAAAAA, 0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55,
				0xFFFFFFFF };

		int videomemStart = 0xB8000;

		int maxX = -1;
		int minX = 99999;
		int maxY = -1;
		int minY = 99999;

		for (int y = 0; y < 25; y++) {
			for (int x = 0; x < 80; x++) {
				int offset = 2 * (x + y * 80);
				char c = (char) (vm.readMemoryBytePhysical(videomemStart + offset) & 0xFF);
				int colorIdx = (char) (vm.readMemoryBytePhysical(videomemStart + 1 + offset) & 0xFF);

				int colorForeground = egaColors[colorIdx & 0xF];
				int colorBackround = egaColors[(colorIdx & 0xF0) >> 4];

				for (int yy = 0; yy < 16; yy++) {
					for (int xx = 0; xx < 9; xx++) {
						int vgaRomOffset = c * 16 + yy;
						int vgaData = VM8086.VGA_ROM_F16[vgaRomOffset];

						int effectiveX = xx + x * 9;
						int effectiveY = yy + y * 16;

						boolean foreground = ((vgaData & (1 << (8 - xx - 1))) != 0);
						int color = foreground ? colorForeground : colorBackround;

						int oldColor = screen.getRGB(effectiveX, effectiveY);
						if (oldColor != color) {
							screen.setRGB(effectiveX, effectiveY, color);

							if (effectiveX > maxX)
								maxX = effectiveX;
							if (effectiveX < minX)
								minX = effectiveX;

							if (effectiveY > maxY)
								maxY = effectiveY;
							if (effectiveY < minY)
								minY = effectiveY;
						}
					}
				}
			}
		}

		if (maxX != -1)
			label.repaint(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}
}
