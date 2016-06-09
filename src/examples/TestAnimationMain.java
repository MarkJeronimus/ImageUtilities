package examples;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class TestAnimationMain extends JFrame {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				new TestAnimationMain();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
	}

	private int mouseX;
	private int mouseY;

	public TestAnimationMain() throws IOException, InterruptedException {
		super(TestAnimationMain.class.getSimpleName());

		BufferedImage[]  img    = new BufferedImage[12];
		AnimationFrame[] frames = new AnimationFrame[12];
		img[0] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\01.png"));
		img[1] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\02.png"));
		img[2] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\03.png"));
		img[3] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\04.png"));
		img[4] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\05.png"));
		img[5] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\06.png"));
		img[6] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\07.png"));
		img[7] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\08.png"));
		img[8] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\09.png"));
		img[9] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\10.png"));
		img[10] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\11.png"));
		img[11] = ImageIO.read(new File("d:\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\12.png"));

		ImageResamplerShort resampler = new ImageResamplerShort();
		resampler.setFilter(Lanczos3ResamplingCurve.INSTANCE); // LinearResamplingCurve

		for (int i = 0; i < frames.length; i++) {
			resampler.setOutputSize(new SizeInt(1920, 1080));

			frames[i] = new AnimationFrame(resampler.resize(img[i]), 1000 / 12);

			// TODO find a better way to prevent out-of-heap error
			System.gc();

//			AnimationFrame frame = frames[i];
//			ImageIO.write(frame.getImage(), "PNG", new File("resampled_frame_" + i + ".png"));
		}

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setUndecorated(true);
		setContentPane(ImageUtilities.createImageComponent(frames));
		setBackground(new Color(0, 0, 0, 0));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}
}
