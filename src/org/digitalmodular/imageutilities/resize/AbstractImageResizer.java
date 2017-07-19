/*
 * This file is part of ImageUtilities.
 *
 * Copyleft 2016 Mark Jeronimus. All Rights Reversed.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ImageUtilities. If not, see <http://www.gnu.org/licenses/>.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.digitalmodular.imageutilities.resize;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static java.lang.Double.isInfinite;
import static java.lang.Double.isNaN;
import static java.util.Objects.requireNonNull;

import org.digitalmodular.imageutilities.AnimationFrame;
import org.digitalmodular.imageutilities.PointDouble;
import org.digitalmodular.imageutilities.ProgressEvent;
import org.digitalmodular.imageutilities.ProgressListener;
import org.digitalmodular.imageutilities.SizeDouble;
import org.digitalmodular.imageutilities.SizeInt;

/**
 * Superclass for all algorithms that can resize an image.
 *
 * @param <I> The type of image that can be resized by this algorithm
 * @author Mark Jeronimus
 */
// Created 2015-08-15
@SuppressWarnings({"OverloadedVarargsMethod", "ProtectedField"})
public abstract class AbstractImageResizer<I> implements ImageResizer {
	// User data
	protected SizeInt     outputSize        = null;
	protected SizeDouble  outputScaleFactor = null;
	protected PointDouble outputOffset      = null;

	protected boolean  ignoreSRGB   = false;
	protected boolean  dontPreAlpha = false;
	protected EdgeMode edgeMode     = EdgeMode.CLAMP;

	protected final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();

	// Working data
	protected int     srcWidth          = 0;
	protected int     srcHeight         = 0;
	protected int     dstWidth          = 0;
	protected int     dstHeight         = 0;
	protected double  widthScaleFactor  = 0;
	protected double  heightScaleFactor = 0;
	protected double  offsetX           = 0;
	protected double  offsetY           = 0;
	protected int     numChannels       = 0;
	protected boolean hasAlpha          = false;
	protected boolean srcIsSRGB         = false;
	protected boolean srcIsPreAlpha     = false;

	@Override
	public SizeInt getOutputSize() {
		return outputSize;
	}

	@Override
	public void setOutputSize(SizeInt outputSize) {
		if (outputSize != null) {
			if (outputSize.getWidth() == 0 || outputSize.getHeight() == 0) {
				throw new IllegalArgumentException("Dimension: " + outputSize);
			} else if (outputSize.getWidth() < 0 || outputSize.getHeight() < 0) {
				throw new IllegalArgumentException("Negative values won't flip the image: " + outputSize);
			}
		}

		this.outputSize = outputSize;
	}

	@Override
	public SizeDouble getOutputScaleFactor() { return outputScaleFactor; }

	@Override
	public void setOutputScaleFactor(SizeDouble outputScaleFactor) {
		if (outputScaleFactor != null) {
			if (isNaN(outputScaleFactor.getWidth()) || isInfinite(outputScaleFactor.getWidth())
			    || isNaN(outputScaleFactor.getHeight()) || isInfinite(outputScaleFactor.getHeight())) {
				throw new IllegalArgumentException("outputScaleFactor is degenerate: " + outputScaleFactor);
			} else if (outputScaleFactor.getWidth() <= 0 || outputScaleFactor.getHeight() <= 0) {
				throw new IllegalArgumentException("Negative values won't flip the image: " + outputScaleFactor);
			}
		}

		this.outputScaleFactor = outputScaleFactor;
	}

	@Override
	public PointDouble getOutputOffset() { return outputOffset; }

	@Override
	public void setOffset(PointDouble outputOffset) {
		if (outputOffset != null) {
			if (isNaN(outputOffset.getX()) || isInfinite(outputOffset.getX())
			    || isNaN(outputOffset.getY()) || isInfinite(outputOffset.getY())) {
				throw new IllegalArgumentException("offset is degenerate: " + outputOffset);
			}
		}

		this.outputOffset = outputOffset;
	}

	@Override
	public boolean isIgnoreSRGB() { return ignoreSRGB; }

	/**
	 * Set to true when you don't want the algorithm to linearize the image beforehand and restore the sRGB
	 * color-space afterwards. Note that for color images the color-space is stored in the image, but not for
	 * grayscale images. Grayscale images are <i>always</i> assumed to be in sRGB.
	 * <p>
	 * Ignoring the color-space might speed up the resizing algorithm at the cost of accuracy.
	 */
	@Override
	public void setIgnoreSRGB(boolean ignoreSRGB) {
		this.ignoreSRGB = requireNonNull(ignoreSRGB, "ignoreSRGB can't be null");
	}

	@Override
	public boolean isDontPreAlpha() { return dontPreAlpha; }

	/**
	 * Set whether to premultiply and un-multiply the alpha values before and after resizing. Default is {@code
	 * false}.
	 * <p>
	 * Set to true if you don't want the algorithm to premultiply alpha beforehand and un-multiply afterwards.
	 * If the image to resize already has it's alpha pre-multiplied, then this flag has no effect.
	 * <p>
	 * Ignoring the alpha channel might speed up the resizing algorithm at the cost of accuracy.
	 */
	@Override
	public void setDontPreAlpha(boolean dontPreAlpha) { this.dontPreAlpha = dontPreAlpha; }

	@Override
	public EdgeMode getEdgeMode() { return edgeMode; }

	/**
	 * Set how the pixels beyond the edge are read. Default is {@link EdgeMode#CLAMP}.
	 */
	@Override
	public void setEdgeMode(EdgeMode edgeMode) {
		this.edgeMode = edgeMode;
	}

	protected void calculateDstSizeAndScale(RenderedImage image) {
		if (outputSize == null && outputScaleFactor == null) {
			throw new IllegalStateException(
					"Either or both of outputSize and outputScaleFactor need to be set first.");
		}

		srcWidth = image.getWidth();
		srcHeight = image.getHeight();

		if (outputSize != null) {
			dstWidth = outputSize.getWidth();
			dstHeight = outputSize.getHeight();
		} else {
			dstWidth = Math.max(1, (int)Math.ceil(srcWidth * outputScaleFactor.getWidth()));
			dstHeight = Math.max(1, (int)Math.ceil(srcHeight * outputScaleFactor.getHeight()));
		}

		if (outputScaleFactor != null) {
			widthScaleFactor = outputScaleFactor.getWidth();
			heightScaleFactor = outputScaleFactor.getHeight();
		} else {
			widthScaleFactor = dstWidth / (double)srcWidth;
			heightScaleFactor = dstHeight / (double)srcHeight;
		}

		if (outputOffset != null) {
			offsetX = outputOffset.getX();
			offsetY = outputOffset.getY();
		} else {
			offsetX = (dstWidth & 1) == 0 ? 0 : 0.5;
			offsetY = (dstHeight & 1) == 0 ? 0 : 0.5;
		}
	}

	public final void addProgressListener(ProgressListener progressListener) {
		listeners.add(progressListener);
	}

	public final void removeProgressListener(ProgressListener progressListener) {
		listeners.remove(progressListener);
	}

	protected void fireProgressUpdated(ProgressEvent e) {
		for (ProgressListener progressListener : listeners)
			progressListener.progressUpdated(e);
	}

	protected void fireProgressCompleted(ProgressEvent e) {
		for (ProgressListener progressListener : listeners)
			progressListener.progressCompleted(e);
	}

	/**
	 * Returns true if the image is compatible with this resizing algorithm. When this returns false, the image
	 * will be converted by the resizing algorithm before resizing. (You don't need to convert it yourself.)
	 * <p>
	 * For example, most algorithms only operate on bytes. An image with packed integers will be converted to an
	 * image using bytes.
	 *
	 * @see #makeImageCompatible(Image)
	 */
	public abstract boolean imageIsCompatible(Image image);

	/**
	 * Returns an image that is compatible with this resizing algorithm (for a description, see
	 * {@link #imageIsCompatible(Image) imageIsCompatible()}).
	 * <p>
	 * If the image is already compatible, then it's returned unchanged (though type-casted).
	 */
	public abstract I makeImageCompatible(Image image);

	@Override
	public AnimationFrame[] resize(AnimationFrame... animation) throws InterruptedException {
		AnimationFrame[] resized = new AnimationFrame[animation.length];

		for (int i = 0; i < animation.length; i++) {
			BufferedImage image = animation[i].getImage();

			image = resize(image);

			resized[i] = new AnimationFrame(image, animation[i].getDuration());

			// TODO find a better way to prevent out-of-heap error
			System.gc();
		}

		return resized;
	}
}
