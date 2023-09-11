package pl.pflp.vm8086;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;

public class TestVM extends Frame {

	private BufferedImage screen = new BufferedImage(80 * 9, 25 * 16, BufferedImage.TYPE_INT_ARGB);
	JLabel label;

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
				switch (event.getKeyCode()) {
				case KeyEvent.VK_ESCAPE:
					return 0x01;
				case KeyEvent.VK_1:
					return 0x02;
				case KeyEvent.VK_2:
					return 0x03;
				case KeyEvent.VK_3:
					return 0x04;
				case KeyEvent.VK_4:
					return 0x05;
				case KeyEvent.VK_5:
					return 0x06;
				case KeyEvent.VK_6:
					return 0x07;
				case KeyEvent.VK_7:
					return 0x08;
				case KeyEvent.VK_8:
					return 0x09;
				case KeyEvent.VK_9:
					return 0x0A;
				case KeyEvent.VK_0:
					return 0x0B;
				case KeyEvent.VK_MINUS:
					return 0x0C;
				case KeyEvent.VK_PLUS:
					return 0x0D;
				case KeyEvent.VK_BACK_SPACE:
					return 0x0E;
				case KeyEvent.VK_TAB:
					return 0x0F;
				case KeyEvent.VK_Q:
					return 0x10;
				case KeyEvent.VK_W:
					return 0x11;
				case KeyEvent.VK_E:
					return 0x12;
				case KeyEvent.VK_R:
					return 0x13;
				case KeyEvent.VK_T:
					return 0x14;
				case KeyEvent.VK_Y:
					return 0x15;
				case KeyEvent.VK_U:
					return 0x16;
				case KeyEvent.VK_I:
					return 0x17;
				case KeyEvent.VK_O:
					return 0x18;
				case KeyEvent.VK_P:
					return 0x19;
				case KeyEvent.VK_OPEN_BRACKET:
					return 0x1A;
				case KeyEvent.VK_CLOSE_BRACKET:
					return 0x1B;
				case KeyEvent.VK_ENTER:
					return 0x1C;
				case KeyEvent.VK_CONTROL:
					return 0x1D;
				case KeyEvent.VK_A:
					return 0x1E;
				case KeyEvent.VK_S:
					return 0x1F;
				case KeyEvent.VK_D:
					return 0x20;
				case KeyEvent.VK_F:
					return 0x21;
				case KeyEvent.VK_G:
					return 0x22;
				case KeyEvent.VK_H:
					return 0x23;
				case KeyEvent.VK_J:
					return 0x24;
				case KeyEvent.VK_K:
					return 0x25;
				case KeyEvent.VK_L:
					return 0x26;
				case KeyEvent.VK_SEMICOLON:
					return 0x27;
				case KeyEvent.VK_QUOTE:
					return 0x28;
				case KeyEvent.VK_BACK_QUOTE:
					return 0x29;
				case KeyEvent.VK_SHIFT:
					if (event.getKeyLocation() == KeyEvent.KEY_LOCATION_LEFT)
						return 0x2A;
					return 0x36;
				case KeyEvent.VK_Z:
					return 0x2C;
				case KeyEvent.VK_X:
					return 0x2D;
				case KeyEvent.VK_C:
					return 0x2E;
				case KeyEvent.VK_V:
					return 0x2F;
				case KeyEvent.VK_B:
					return 0x30;
				case KeyEvent.VK_N:
					return 0x31;
				case KeyEvent.VK_M:
					return 0x32;
				case KeyEvent.VK_COMMA:
					return 0x33;
				case KeyEvent.VK_PERIOD:
					return 0x34;
				case KeyEvent.VK_SLASH:
					return 0x35;
				case KeyEvent.VK_SPACE:
					return 0x39;
				case KeyEvent.VK_CAPS_LOCK:
					return 0x3A;
				default:
					return 0x1E;
				}
			}

			@Override
			public void keyTyped(KeyEvent e) {
				// TODO Auto-generated method stub
			}

			@Override
			public void keyReleased(KeyEvent e) {
				keyboard.keyPressed(getPs2Scancode(e) | 0x80);
			}

			@Override
			public void keyPressed(KeyEvent e) {
				keyboard.keyPressed(getPs2Scancode(e));
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

		byte[] bios = new byte[65536];

		File biosFile = new File("C:\\Users\\Marcin\\Desktop\\oc86boot\\bios.bin");
		bios = Files.readAllBytes(biosFile.toPath());

		VM8086 vm8086 = new VM8086(1024 * 1024, bios);
		vm8086.attachPS2Keyboard(keyboard);
		vm8086.attachIdeDevice(0, false, new DummyIdeDrive());

		while (vm8086.isRunning) {
			vm8086.step(1000);
			screenUpdate(vm8086);
		}

	}

	public static void main(String[] args) throws IOException {
		TestVM window = new TestVM();

		System.out.println("Died");
	}

	private void screenUpdate(VM8086 vm) {
		int[] egaColors = { 0xFF000000, 0xFFAA0000, 0xFF00AA00, 0xFFAAAA00, 0xFF0000AA, 0xFFAA00AA, 0xFF0055AA,
				0xFFAAAAAA, 0xFF555555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFF5555FF, 0xFFFF55FF, 0xFF55FFFF,
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

		// this.repaint();
		if (maxX != -1)
			label.repaint(minX, minY, maxX - minX + 1, maxY - minY + 1);
		// label.repaint();

	}
}
