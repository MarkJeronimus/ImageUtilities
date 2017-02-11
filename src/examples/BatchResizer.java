package examples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class BatchResizer {
	public static void main(String[] args) {
		resizeFolder("d:\\desktops\\a", "d:\\Desktops\\a", new SizeInt(9999, 1920));
	}

	private static ImageResamplerShort resampler = new ImageResamplerShort();

	public static void resizeFolder(String srcPath, String dstPath, SizeInt targetSize) {
		for (File file : new File(srcPath).listFiles()) {
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

			final SizeInt size = ImageUtilities
					.getScalingSize(new SizeInt(img.getWidth(), img.getHeight()), targetSize);

			Logger.getGlobal().fine("Size: " + size);

			resampler.setOutputSize(size);

			img = resampler.resize(img);

			ImageIO.write(img, "PNG", dstFile);
		} catch (Exception ex) {
			System.out.print(ex.getMessage());
		} finally {
			System.out.println();
		}
	}
}
