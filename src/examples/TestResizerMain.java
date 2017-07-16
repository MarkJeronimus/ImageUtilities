package examples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public enum TestResizerMain {
	;

	public static void main(String... args) throws IOException, InterruptedException {
		BufferedImage image   = load("testImages/2160p.png");
		BufferedImage resized = resizeImage(image);
		save(resized, "1080p.png");
	}

	private static BufferedImage load(String filename) throws IOException {
		File file = new File(filename);

		Logger.getGlobal().info("Loading: " + file.getCanonicalPath());

		return ImageIO.read(file);
	}

	private static BufferedImage resizeImage(BufferedImage image) throws InterruptedException {
		ImageResamplerShort resampler = new ImageResamplerShort();

		resampler.setFilter(Lanczos3ResamplingCurve.INSTANCE);
		// resampler.setNumThreads(1);
		// resampler.setIgnoreSRGB(true);
		// resampler.setDontPreAlpha(true);

		resampler.setOutputSize(ImageUtilities.getScalingSize(new SizeInt(image), new SizeInt(1920, 1080)));

		Logger.getGlobal().info("Resizing");

		BufferedImage resized = resampler.resize(image);

		return resized;
	}

	private static void save(BufferedImage resized, String filename) throws IOException {
		File file = new File(filename);

		Logger.getGlobal().info("Writing: " + file.getCanonicalPath());

		ImageIO.write(resized, "PNG", file);
	}
}
