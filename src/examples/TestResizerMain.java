package examples;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.awt.Frame.MAXIMIZED_BOTH;
import static java.util.logging.Level.FINEST;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.digitalmodular.imageutilities.AnimationFrame;
import org.digitalmodular.imageutilities.PointDouble;
import org.digitalmodular.imageutilities.SizeDouble;
import org.digitalmodular.imageutilities.SizeInt;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.ResizerUtilities;
import org.digitalmodular.imageutilities.resize.filter.Lanczos8ResamplingCurve;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class TestResizerMain {
	public static void main(String... args) throws IOException, InterruptedException {
		setLoggerLevel(FINEST);

		String filename = "cpuStripesTest.png";

		BufferedImage image = load("testImages/" + filename);

		BufferedImage resized = resize(image);

		show(resized);

		save(resized, "testImages/out/" + filename);
	}

	private static void setLoggerLevel(Level level) {
		System.setProperty("java.util.logging.SimpleFormatter.format",
		                   "[%1$tY%1$tm%1$tdT%1$tH%1$tM%1$tS.%1$tL %4$s] %2$s: %5$s%6$s%n");

		Logger.getGlobal().setLevel(level);
		Logger.getGlobal().getParent().removeHandler(Logger.getGlobal().getParent().getHandlers()[0]);
		Logger.getGlobal().getParent().addHandler(new ConsoleHandler());
		Logger.getGlobal().getParent().getHandlers()[0].setLevel(Level.ALL);
	}

	private static BufferedImage load(String filename) throws IOException {
		File file = new File(filename);

		Logger.getGlobal().info("Loading: " + file.getCanonicalPath());

		return ImageIO.read(file);
	}

	private static BufferedImage resize(BufferedImage image) throws InterruptedException {
		SizeInt newSize = ResizerUtilities.getScalingSize(new SizeInt(image), new SizeInt(1024, 1024));

		ImageResamplerShort resampler = new ImageResamplerShort();
		resampler.setNumThreads(8);
//		resampler.setIgnoreSRGB(true);
//		resampler.setDontPreAlpha(true);
//		resampler.setEdgeMode(EdgeMode.CLAMP);
		resampler.setFilter(Lanczos8ResamplingCurve.INSTANCE);
//		resampler.setOutputSize(newSize);
		resampler.setOutputScaleFactor(new SizeDouble(1, 63.5));
		resampler.setOffset(new PointDouble(0, 0));

		Logger.getGlobal().info("Resizing");

		BufferedImage resized = resampler.resize(image);

		return resized;
	}

	private static void save(RenderedImage resized, String filename) throws IOException {
		File file = new File(filename);

		Logger.getGlobal().info("Writing: " + file.getCanonicalPath());

		ImageIO.write(resized, "PNG", file);
	}

	private static void show(BufferedImage frames) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame();
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setUndecorated(true);
			frame.setContentPane(new AnimationPanel(new AnimationFrame[]{new AnimationFrame(frames, 1)}));
			frame.setBackground(new Color(0, 0, 0, 0));
			frame.setExtendedState(MAXIMIZED_BOTH);
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);
		});
	}
}
