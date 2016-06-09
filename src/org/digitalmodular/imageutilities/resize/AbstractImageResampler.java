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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.resize.filter.CubicResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.LinearResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;
import org.digitalmodular.imageutilities.util.DependentWorkerQueue;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * Superclass for all algorithms that can resize an image using high-quality resampling filters and parallel processing.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-22
abstract class AbstractImageResampler extends AbstractImageResizer<BufferedImage> implements ImageResampler {

	protected static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

	protected final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
	protected final ThreadPoolExecutor      executor  = new ThreadPoolExecutor(
			AVAILABLE_PROCESSORS, AVAILABLE_PROCESSORS, 60L, TimeUnit.MILLISECONDS, workQueue);
	protected final CompletionService<Void> service   = new ExecutorCompletionService<>(executor);

	protected ResamplingCurve filter     = Lanczos3ResamplingCurve.INSTANCE;
	protected int             numThreads = 0;

	protected transient SamplingData horizontalSamplingData;
	protected transient SamplingData verticalSamplingData;

	public AbstractImageResampler() {
		executor.allowCoreThreadTimeOut(true);
	}

	@Override
	public ResamplingCurve getFilter() {
		return filter;
	}

	@Override
	public void setFilter(ResamplingCurve filter) {
		this.filter = Objects.requireNonNull(filter);
	}

	public void autoSelectFilter(SizeInt imageSize, SizeInt newSize) {
		double factor = Math.max(Math.max(newSize.getWidth() / (double) imageSize.getWidth(),
		                                  newSize.getHeight() / (double) imageSize.getHeight()),
		                         Math.max(imageSize.getWidth() / (double) newSize.getWidth(),
		                                  imageSize.getHeight() / (double) newSize.getHeight()));

		if (factor >= 4)
			setFilter(Lanczos3ResamplingCurve.INSTANCE);
		else if (factor >= 2)
			setFilter(CubicResamplingCurve.INSTANCE);
		else if (factor >= 1)
			setFilter(LinearResamplingCurve.INSTANCE);
	}

	@Override
	public int getNumThreads() {
		return numThreads;
	}

	@Override
	public void setNumThreads(int numThreads) {
		if (numThreads < 0) throw new IllegalArgumentException("numThreads can't be negative: " + numThreads);
		this.numThreads = numThreads;
	}

	@Override
	public boolean imageIsCompatible(Image image) {
		if (!(image instanceof BufferedImage)) {
			return false;
		}

		BufferedImage img = (BufferedImage) image;

		// Known compatible types
		int srcType = img.getType();
		if (srcType == BufferedImage.TYPE_3BYTE_BGR
		    || srcType == BufferedImage.TYPE_4BYTE_ABGR
		    || srcType == BufferedImage.TYPE_4BYTE_ABGR_PRE
		    || srcType == BufferedImage.TYPE_BYTE_GRAY) {
			return true;
			// } else if (srcType == BufferedImage.TYPE_BYTE_BINARY
			// || srcType == BufferedImage.TYPE_BYTE_INDEXED) {
			// return false;
		}

		// Check if the image is gray+alpha, which quite surprisingly is a standard format without a TYPE_
		int srcDataType = img.getRaster().getDataBuffer().getDataType();

		return srcType == BufferedImage.TYPE_CUSTOM
		       && srcDataType == DataBuffer.TYPE_BYTE
		       && hasAlpha;
	}

	@Override
	public synchronized BufferedImage makeImageCompatible(Image image) {
		if (imageIsCompatible(image)) {
			return (BufferedImage) image;
		}

		if (image instanceof BufferedImage) {
			BufferedImage img        = (BufferedImage) image;
			int           width      = img.getWidth();
			int           height     = img.getHeight();
			int           colorType  = ImageUtilities.getColorSpaceType(img.getColorModel().getColorSpace());
			boolean       hasAlpha   = img.getColorModel().hasAlpha();
			boolean       isAlphaPre = img.getColorModel().isAlphaPremultiplied();

			// Can't query raster because we're possibly changing the number of bands
			int numComponents = img.getColorModel().getColorSpace().getNumComponents();
			int numBands      = numComponents + (hasAlpha ? 1 : 0);

			// Convert to something more manageable.
			BufferedImage temp = ImageUtilities.createByteImage(
					width, height, numBands,
					colorType,
					hasAlpha, isAlphaPre);

			ColorConvertOp op = new ColorConvertOp(temp.getColorModel().getColorSpace(), null);
			img = op.filter(img, temp);

			Logger.getGlobal().finest("pre-converted img: " + ImageUtilities.analyzeImage(img));

			return img;
		}

		// Fallback method.
		BufferedImage img = new BufferedImage(srcWidth, srcHeight, BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D    g   = img.createGraphics();
		try {
			g.drawImage(image, 0, 0, null);
		} finally {
			g.dispose();
		}
		return img;
	}

	/**
	 * Calculates the minimum number of samples required to cover the resampling curve.
	 *
	 * @param filter
	 * @param scale  the scaling factor, {@code < 1} means shrinking.
	 * @return
	 */
	protected static int calculateNumSamples(ResamplingCurve filter, double scale) {
		double samplingRadius = getSamplingRadius(filter, scale);

		return (int) Math.ceil(samplingRadius * 2);
	}

	protected static SamplingData createSubSampling(ResamplingCurve filter, int srcSize, int dstSize,
	                                                double scale, double offset, int pixelStride) {
		int numSamples = calculateNumSamples(filter, scale);

		int[]     indices   = new int[dstSize * numSamples];
		int[][]   indices2D = new int[dstSize][numSamples];
		float[]   weights   = new float[dstSize * numSamples];
		float[][] weights2D = new float[dstSize][numSamples];

		// Translation between source and destination image CENTERS, in source scalespace
		double centerOffset = ((srcSize - 1) - (dstSize - 1 + offset * 2) / scale) / 2;

		double samplingRadius = getSamplingRadius(filter, scale);

		int k = 0;
		for (int i = 0; i < dstSize; i++) {
			int    subIndex = k;
			double center   = i / scale + centerOffset;

			int left  = (int) Math.ceil(center - samplingRadius);
			int right = left + numSamples;

			for (int j = left; j < right; j++) {
				float weight;
				if (scale < 1)
					weight = (float) filter.apply((j - center) * scale);
				else
					weight = (float) filter.apply(j - center);

				int n = j < 0 ? 0 : j >= srcSize ? srcSize - 1 : j;

				indices[k] = n * pixelStride;
				indices2D[i][j - left] = n * pixelStride;
				weights[k] = weight;
				weights2D[i][j - left] = weight;
				k++;
			}

			// Normalize weights
			double sum = 0;
			for (int j = 0; j < numSamples; j++)
				sum += weights2D[i][j];

			if (sum != 0) {
				for (int j = 0; j < numSamples; j++) {
					weights[subIndex + j] /= sum;
					weights2D[i][j] /= sum;
				}
			}
		}
		return new SamplingData(numSamples, indices, indices2D, weights, weights2D);
	}

	protected void runWorkers(DependentWorkerQueue<Void> workers) throws InterruptedException {
		int numThreads = this.numThreads == 0 ? AVAILABLE_PROCESSORS : this.numThreads;

		// Keep track of which workers there are in the service
		Set<Future<Void>> runningWorkers = new HashSet<>();

		// Run first few
		for (int i = 0; i < numThreads && workers.hasEligibleWorkers(); i++) {
			Callable<Void> worker = workers.takeEligibleworker(); // Doesn't block
			runningWorkers.add(service.submit(worker));
		}

		while (!runningWorkers.isEmpty()) {
			try {
				// Wait for next completed worker
				Future<Void> future = service.take(); // Blocks
				try {
					future.get(); // Doesn't block anymore, but required to make it throw exceptions.
				} finally {
					runningWorkers.remove(future);
				}

				// If there are eligible workers workers, start one
				if (!workers.isEmpty()) {
					Callable<Void> worker = workers.takeEligibleworker(); // Blocks
					runningWorkers.add(service.submit(worker));
				}
			} catch (InterruptedException ex) {
				runningWorkers.forEach(future -> future.cancel(true));
				Thread.currentThread().interrupt();
				throw ex;
			} catch (ExecutionException ex) {
				Throwable th = ex.getCause();
				// Check if it is one of the unchecked throwables
				if (th instanceof RuntimeException) {
					throw (RuntimeException) th;
				} else if (th instanceof Error) {
					throw (Error) th;
				} else {
					throw new AssertionError("Unhandled checked exception", th);
				}
			}
		}
	}

	/**
	 * SamplingData describes how a single row or column of an image can be resized. It specifies for each
	 * output sample which input samples contribute (using relative indices), and how much (using normalized
	 * weights). Each output sample always depends on a fixed number of input samples, specified by
	 * {@link #numSamples}. Although in practice the input sample indices are always contiguous, and the middle
	 * one or one of the two middle ones equal the output sample index, these are not enforced.
	 */
	protected static class SamplingData {
		/**
		 * The number of input samples per output sample.
		 */
		final int       numSamples;
		/**
		 * The input sample indices. A linear array of {@link #numSamples} indices for each output sample.
		 */
		final int[]     indices;
		/**
		 * @see #indices
		 */
		final int[][]   indices2D;
		/**
		 * The input sample weights. A linear array of {@link #numSamples} weights for each output sample.
		 */
		final float[]   weights;
		/**
		 * @see #weights
		 */
		final float[][] weights2D;

		SamplingData(int numSamples, int[] indices, int[][] indices2D, float[] weights, float[][] weights2D) {
			this.numSamples = numSamples;
			this.indices = indices;
			this.indices2D = indices2D;
			this.weights = weights;
			this.weights2D = weights2D;
		}
	}

	/**
	 * Calculates the sampling radius of the resampling curve, in source scalespace.
	 *
	 * @param filter
	 * @param scale  the scaling factor, {@code < 1} means shrinking.
	 * @return
	 */
	private static double getSamplingRadius(ResamplingCurve filter, double scale) {
		double curveRadius = filter.getRadius();
		return scale < 1 ? curveRadius / scale : curveRadius;
	}
}
