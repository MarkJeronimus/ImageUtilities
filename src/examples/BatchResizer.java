package examples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.logging.Level.WARNING;
import javax.imageio.ImageIO;

import org.digitalmodular.imageutilities.SizeInt;
import org.digitalmodular.imageutilities.resize.ImageResampler;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.ResizerUtilities;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class BatchResizer {
	private static final ImageResampler RESAMPLER = new ImageResamplerShort();

	public static void main(String[] args) {
//		RESAMPLER.setNumThreads(1);
		RESAMPLER.setFilter(Lanczos3ResamplingCurve.INSTANCE);
//		RESAMPLER.setDontPreAlpha(true);
//		RESAMPLER.setIgnoreSRGB(true);

		resizeFolder("d:\\desktops\\a",
		             "d:\\Desktops\\a",
		             new SizeInt(9999, 1920));
	}

	public static void resizeFolder(String srcPath, String dstPath, SizeInt targetSize) {
		File[] files = new File(srcPath).listFiles();
		if (files == null)
			throw new IllegalArgumentException("Not a directory: " + srcPath);

		for (File file : files) {
			if (file.isFile())
				resizeImage(file, new File(dstPath, file.getName()), targetSize);

			// TODO find a better way to prevent out-of-heap error
			System.gc();
		}
	}

	public static void resizeImage(File srcFile, File dstFile, SizeInt targetSize) {
		Logger.getGlobal().info("Resizing: " + srcFile);
		try {
			BufferedImage img = ImageIO.read(srcFile);

			SizeInt size = ResizerUtilities.getScalingSize(new SizeInt(img.getWidth(), img.getHeight()), targetSize);

			if (Logger.getGlobal().isLoggable(Level.FINE))
				Logger.getGlobal().fine("Size: " + size);

			RESAMPLER.setOutputSize(size);

			img = RESAMPLER.resize(img);

			ImageIO.write(img, "PNG", dstFile);
		} catch (IOException | InterruptedException ex) {
			Logger.getGlobal().log(WARNING, ex.getMessage(), ex);
		}
	}
}
