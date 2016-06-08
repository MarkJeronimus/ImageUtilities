/*
 * Copyright 2013, Morten Nobel-Joergensen
 *
 * License: The BSD 3-Clause License
 * http://opensource.org/licenses/BSD-3-Clause
 */
package old;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Morten Nobel-Joergensen
 */
public abstract class AdvancedResizeOpOld implements BufferedImageOp {
	private final List<ProgressListenerOld> listeners = new ArrayList<>();

	protected void fireProgressChanged(float fraction) {
		for (ProgressListenerOld progressListener : listeners) {
			progressListener.notifyProgress(fraction);
		}
	}

	public final void addProgressListener(ProgressListenerOld progressListener) {
		listeners.add(progressListener);
	}

	public final boolean removeProgressListener(ProgressListenerOld progressListener) {
		return listeners.remove(progressListener);
	}

	@Override
	public final Rectangle2D getBounds2D(BufferedImage src) {
		return new Rectangle(0, 0, src.getWidth(), src.getHeight());
	}

	@Override
	public final BufferedImage createCompatibleDestImage(BufferedImage src,
	                                                     ColorModel destCM) {
		if (destCM == null) {
			destCM = src.getColorModel();
		}
		return new BufferedImage(destCM,
		        destCM.createCompatibleWritableRaster(
		                src.getWidth(), src.getHeight()),
		        destCM.isAlphaPremultiplied(), null);
	}

	@Override
	public final Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
		return (Point2D)srcPt.clone();
	}

	@Override
	public final RenderingHints getRenderingHints() {
		return null;
	}
}
