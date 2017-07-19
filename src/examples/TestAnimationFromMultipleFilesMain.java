package examples;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import static java.awt.Frame.MAXIMIZED_BOTH;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.digitalmodular.imageutilities.AnimationFrame;
import org.digitalmodular.imageutilities.SizeInt;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.ResizerUtilities;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class TestAnimationFromMultipleFilesMain {
	public static void main(String[] args) throws IOException, InterruptedException {
		AnimationFrame[] frames = load();

		AnimationFrame[] resized = resize(frames);

		show(resized);
	}

	private static AnimationFrame[] load() throws IOException {
		String path = "\\\\zom-b-pc\\E\\Timelapses\\Nasa Blue Marble 2004 10800 030ppdeg\\%02d.png";

		AnimationFrame[] frames = new AnimationFrame[12];
		for (int i = 0; i < frames.length; i++) {
			BufferedImage image = ImageIO.read(new File(String.format(path, i + 1)));
			frames[i] = new AnimationFrame(image, 120);
		}

		return frames;
	}

	private static AnimationFrame[] resize(AnimationFrame[] frames) throws InterruptedException {
		SizeInt targetSize = new SizeInt(1920, 1080);
		SizeInt imageSize  = frames[0].getSize();
		SizeInt newSize    = ResizerUtilities.getScalingSize(imageSize, targetSize);

		ImageResamplerShort resampler = new ImageResamplerShort();
//		resampler.setNumThreads(1);
//		resampler.setIgnoreSRGB(true);
//		resampler.setDontPreAlpha(true);
//		resampler.setEdgeMode(EdgeMode.CLAMP);
//		resampler.setFilter(Lanczos8ResamplingCurve.INSTANCE);
		resampler.setOutputSize(newSize);
//		resampler.setOutputScaleFactor(new SizeDouble(50, 100));
//		resampler.setOffset(new PointDouble(0.5, 0.5));

		frames = resampler.resize(frames);
		return frames;
	}

	private static void show(AnimationFrame[] frames) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setUndecorated(true);
			frame.setContentPane(new AnimationPanel(frames));
			frame.setBackground(new Color(0, 0, 0, 0));
			frame.setExtendedState(MAXIMIZED_BOTH);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}
