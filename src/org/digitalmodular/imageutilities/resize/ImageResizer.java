package org.digitalmodular.imageutilities.resize;

import java.awt.image.BufferedImage;
import org.digitalmodular.imageutilities.util.PointDouble;
import org.digitalmodular.imageutilities.util.SizeDouble;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2016-05-06
public interface ImageResizer {
	enum EdgeMode {
		/**
		 * Replicate edge pixels (-1 = 0, -2 = 0, etc.)
		 */
		CLAMP,
//		/**
//		 * Replicate edge pixels (-1 = w, -2 = w-1, etc.)
//		 */
//		WRAP,
//		/**
//		 * Mirror back at the edge (-1 = 1, -2 = 2, etc.)
//		 */
//		BOUNCE,
//		/**
//		 * Simulate transparent pixels beyond the edge (only applicable if the image to resize is transparent,
//		 * otherwise an {@link IllegalArgumentException} will be thrown by
//		 * {@link AbstractImageResizer#resize(Object) resize()}).
//		 */
//		TRANSPARENT
	}

	SizeInt getOutputSize();
	void setOutputSize(SizeInt outputSize);

	SizeDouble getOutputScale();
	void setOutputScale(SizeDouble outputScale);

	PointDouble getOutputOffset();
	void setOffset(PointDouble outputOffset);

	boolean isIgnoreSRGB();
	void setIgnoreSRGB(boolean ignoreSRGB);

	boolean isDontPreAlpha();
	void setDontPreAlpha(boolean dontPreAlpha);

	EdgeMode getEdgeMode();
	void setEdgeMode(EdgeMode edgeMode);

	/**
	 * Resizes the image to the dimensions previously set by {@link #setOutputSize(SizeInt) setOutputSize()}.
	 * If image size already equals the output size, it's returned unchanged.
	 * <p>
	 * The cancellation policy is to interrupt this thread. This will interrupt all workers and return as soon
	 * as possible by throwing an {@link InterruptedException}.
	 *
	 * @param image
	 * @return
	 * @throws InterruptedException when the thread has been interrupted
	 */
	BufferedImage resize(BufferedImage image) throws InterruptedException;
}
