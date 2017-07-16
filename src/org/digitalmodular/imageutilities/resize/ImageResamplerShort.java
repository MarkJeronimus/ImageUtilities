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

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.util.DependentWorkerQueue;
import org.digitalmodular.imageutilities.util.ProgressEvent;

/**
 * Based on work from java-image-scaling
 * (<a href="https://github.com/mortennobel/java-image-scaling">https://github.com/mortennobel/java-image-scaling</a>)
 * <p>
 * Features:<ul>
 * <li>Compatible images: {@link BufferedImage} with G, AG, BGR, or ABGR interleaved byte data,</li>
 * <li>Internal format: 15 bits per component,</li>
 * <li>Intermediate clamping: no, the 16th bit is used for under/overshoot),</li>
 * <li>Linearity: sRGB correction applied when necessary,</li>
 * <li>Alpha: pre-multiplies when necessary,</li>
 * <li>Sub-pixel accurate placement of original image inside resized image,</li>
 * <li>Sub-pixel accurate placement of original image inside resized image,</li>
 * <li>Parallel processing: With smart interleaving. Each resampling step on a chunk of the image starts as soon as
 * the required input chunks become available.</li>
 * </ul>
 * <p>
 * All pages data buffers are in {@code short} format. The values are converted to 15 bits per channel, approximately
 * centered on zero. The 16th bit provides headroom to prevent clamping, to prevent the need for clamping. The exact
 * range spans from -16384 to 16256, which is 32640 levels. This value comes from 255*128, the formula used to linearly
 * convert to and from byte images.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public class ImageResamplerShort extends AbstractImageResampler {
	/** Converts byte to effective range [-16384..16256] */
	protected static final short[] BYTE_SRGB_TO_SHORT  = new short[256];
	/** Converts byte to effective range [0..32640] */
	protected static final short[] BYTE_SRGB_TO_SHORT2 = new short[256];
	/** Converts effective range [-16384..16256] to byte */
	protected static final byte[]  SHORT_TO_BYTE_SRGB  = new byte[65536];
	/** Converts effective range [0..32640] to byte */
	protected static final byte[]  SHORT2_TO_BYTE_SRGB = new byte[65536];

	static {
		for (int b = 0; b < 256; b++) {
			double f = (b & 0xFF) / 255.0;
			BYTE_SRGB_TO_SHORT[b] = (short)(fromSRGB(f) * 32640 - 16384 + 0.5);
			// With offset already implemented
			BYTE_SRGB_TO_SHORT2[b] = (short)(BYTE_SRGB_TO_SHORT[b] + 16384);
		}

		for (int s = -32768; s < 32768; s++) {
			double f = s < -16384 ? 0 : s >= 16256 ? 1 : (s + 16384) / 32640.0;
			SHORT_TO_BYTE_SRGB[s & 0xFFFF] = (byte)(toSRGB(f) * 255 + 0.5);
			// With offset already implemented
			SHORT2_TO_BYTE_SRGB[(s + 16384) & 0xFFFF] = SHORT_TO_BYTE_SRGB[s & 0xFFFF];
		}
	}

	private enum ResamplingOrder {
		NONE,
		X_ONLY,
		Y_ONLY,
		X_FIRST,
		Y_FIRST
	}

	public static double toSRGB(double f) {
		return f < 0.0031308f ? f * 12.92f : Math.pow(f, 1 / 2.4) * 1.055f - 0.055f;
	}

	public static double fromSRGB(double f) {
		return f < 0.04045f ? f / 12.92f : Math.pow((f + 0.055f) / 1.055f, 2.4);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return a resized {@link BufferedImage} or the unmodified input image (
	 */
	@SuppressWarnings("ConstantConditions") // Suppress an IntelliJ bug.
	@Override
	public synchronized BufferedImage resize(BufferedImage image) throws InterruptedException {
		timer.reset();
		timer.start();
		fireProgressUpdated(new ProgressEvent(0, -1));

		calculateDstSizeAndScale(image);

		// Determine the most efficient order of operations
		ResamplingOrder order = determineResampleOrder();

		if (order == ResamplingOrder.NONE)
			return image;

		if (Logger.getGlobal().isLoggable(Level.FINEST))
			Logger.getGlobal().finest("input img: " + ImageUtilities.analyzeImage(image));

		if (Thread.interrupted())
			throw new InterruptedException();

		BufferedImage src = makeImageCompatible(image);

		timer.record("Convert");

		// Create output image with same properties as the input image after pre-conversion
		BufferedImage out = createCompatibleOutputImage(src);

		// Prepare the work buffers
		byte[]  srcPixels  = ((DataBufferByte)src.getRaster().getDataBuffer()).getData();
		short[] srcBuffer  = new short[srcPixels.length];
		short[] workBuffer = makeWorkBuffer(order);
		short[] dstBuffer  = new short[dstWidth * dstHeight * numBands];
		byte[]  dstPixels  = ((DataBufferByte)out.getRaster().getDataBuffer()).getData();

		if (Thread.interrupted())
			throw new InterruptedException();

		timer.record("Allocate");

		// Pre-calculate the sub-sampling(s)
		preCalculateSubSampling(order);

		// Build the queue of parallelizable workers
		List<List<Callable<Void>>> workers =
				makeWorkerLists(order, srcPixels, srcBuffer, workBuffer, dstBuffer, dstPixels);
		DependentWorkerQueue<Void> workerQueue = makeResampleQueue(workers);

		fireProgressUpdated(new ProgressEvent(0, workerQueue.size()));

		runWorkers(workerQueue);

		if (Thread.interrupted())
			throw new InterruptedException();

		timer.record("Resize");
		timer.printResults(dstWidth * dstHeight);
		timer.printTotal();
		fireProgressCompleted(new ProgressEvent(dstWidth * dstHeight, dstWidth * dstHeight));

		// GC this:
		horizontalSamplingData = null;
		verticalSamplingData = null;

		return out;
	}

	private ResamplingOrder determineResampleOrder() {
		boolean doX = srcWidth != dstWidth || offsetX != 0;
		boolean doY = srcHeight != dstHeight || offsetY != 0;

		// Calculate the work effort of each possible sub-process.
		// The +1 tweak comes from the store operation for each resampled pixel.
		long effortX0 = doX ? (long)srcHeight * dstWidth * (calculateNumSamples(filter, scaleWidth) + 1) : 0;
		long effortY0 = doY ? (long)srcWidth * dstHeight * (calculateNumSamples(filter, scaleHeight) + 1) : 0;
		long effortX1 = doX ? (long)dstHeight * dstWidth * (calculateNumSamples(filter, scaleWidth) + 1) : 0;
		long effortY1 = doY ? (long)dstWidth * dstHeight * (calculateNumSamples(filter, scaleHeight) + 1) : 0;

		if (Logger.getGlobal().isLoggable(Level.FINEST))
			Logger.getGlobal().finest("Efforts: " + effortX0 + ' ' + effortY1 +
			                          " <> " + effortY0 + ' ' + effortX1);

		ResamplingOrder order;
		if (!doX && !doY)
			order = ResamplingOrder.NONE;
		else if (!doY)
			order = ResamplingOrder.X_ONLY;
		else if (!doX)
			order = ResamplingOrder.Y_ONLY;
		else if (effortX0 + effortY1 < effortY0 + effortX1)
			order = ResamplingOrder.X_FIRST;
		else
			order = ResamplingOrder.Y_FIRST;

		if (Logger.getGlobal().isLoggable(Level.FINER))
			Logger.getGlobal().finer("Resampling order: " + order);

		return order;
	}

	private BufferedImage createCompatibleOutputImage(BufferedImage src) {
		numBands = src.getRaster().getNumBands();
		int srcColorType = ImageUtilities.getColorSpaceType(src.getColorModel().getColorSpace());
		hasAlpha = src.getColorModel().hasAlpha();
		srcIsSRGB = srcColorType != ColorSpace.CS_LINEAR_RGB;
		srcIsPreAlpha = src.getColorModel().isAlphaPremultiplied();
		// IMPROVE: extra check to see if entire palette (except transparent index) is gray

		BufferedImage img = ImageUtilities.createByteImage(
				dstWidth, dstHeight, numBands,
				srcColorType,
				hasAlpha, srcIsPreAlpha);

		if (Logger.getGlobal().isLoggable(Level.FINEST))
			Logger.getGlobal().finest("output img: " + ImageUtilities.analyzeImage(img));

		return img;
	}

	private short[] makeWorkBuffer(ResamplingOrder resamplingOrder) {
		switch (resamplingOrder) {
			case X_ONLY:
			case Y_ONLY:
				// Only step: no need for a work buffer
				return null;
			case X_FIRST:
				// First step: use only width from dst
				return new short[dstWidth * srcHeight * numBands];
			case Y_FIRST:
				// First step: use only height from dst
				return new short[srcWidth * dstHeight * numBands];
			default:
				throw new AssertionError(resamplingOrder);
		}
	}

	private void preCalculateSubSampling(ResamplingOrder resampleOrder) {
		switch (resampleOrder) {
			case X_ONLY:
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);
				break;
			case Y_ONLY:
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * srcWidth);
				break;
			case X_FIRST:
				// Pre-calculate sub-sampling
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * dstWidth);
				break;
			case Y_FIRST:
				// Pre-calculate sub-sampling
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * srcWidth);
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);
				break;
			default:
				throw new AssertionError(resampleOrder);
		}
	}

	private List<List<Callable<Void>>> makeWorkerLists(ResamplingOrder resamplingOrder,
	                                                   byte[] srcPixels, short[] srcBuffer,
	                                                   short[] workBuffer,
	                                                   short[] dstBuffer, byte[] dstPixels) {
		int numStripes = numThreads == 0 ? AVAILABLE_PROCESSORS : numThreads;

		// Make 4 lists of workers for each of the steps in the process.
		List<Callable<Void>> preConvertWorkers    = new ArrayList<>(numStripes);
		List<Callable<Void>> resampleStep1Workers = new ArrayList<>(numStripes);
		List<Callable<Void>> resampleStep2Workers = new ArrayList<>(numStripes);
		List<Callable<Void>> postConvertWorkers   = new ArrayList<>(numStripes);

		// Divide the rows of the image in approximately equal pieces
		int numLayers = 0;
		for (int i = 0; i < numStripes; i++) {
			int srcBegin = i * srcHeight / numStripes;
			int srcEnd   = (i + 1) * srcHeight / numStripes;
			int dstBegin = i * dstHeight / numStripes;
			int dstEnd   = (i + 1) * dstHeight / numStripes;

			// First step: pre-convert
			preConvertWorkers.add(new PreConvertWorker(srcPixels, srcBuffer, srcBegin, srcEnd));

			// Intermediate steps: X and/or Y resampling
			switch (resamplingOrder) {
				case X_ONLY:
					resampleStep1Workers.add(new HorizontalWorker(srcBuffer, dstBuffer, dstBegin, dstEnd));
					break;
				case Y_ONLY:
					resampleStep1Workers.add(new VerticalWorker(srcBuffer, dstBuffer, dstBegin, dstEnd, srcWidth));
					break;
				case X_FIRST:
					resampleStep1Workers.add(new HorizontalWorker(srcBuffer, workBuffer, srcBegin, srcEnd));
					resampleStep2Workers.add(new VerticalWorker(workBuffer, dstBuffer, dstBegin, dstEnd, dstWidth));
					break;
				case Y_FIRST:
					resampleStep1Workers.add(new VerticalWorker(srcBuffer, workBuffer, dstBegin, dstEnd, srcWidth));
					resampleStep2Workers.add(new HorizontalWorker(workBuffer, dstBuffer, dstBegin, dstEnd));
					break;
				default:
					throw new AssertionError(resamplingOrder);
			}

			// Last step: pre-convert
			postConvertWorkers.add(new PostConvertWorker(dstBuffer, dstPixels, dstBegin, dstEnd));
		}

		return Arrays.asList(preConvertWorkers, resampleStep1Workers, resampleStep2Workers, postConvertWorkers);
	}

	private DependentWorkerQueue<Void> makeResampleQueue(List<List<Callable<Void>>> workers) {
		int numStripes = numThreads == 0 ? AVAILABLE_PROCESSORS : numThreads;

		DependentWorkerQueue<Void> workerQueue = new DependentWorkerQueue<>();

		for (int i = 0; i < workers.size(); i++) {
			List<Callable<Void>> currentLayer = workers.get(i);
			if (i == 0) {
				// Workers in the first layer don't have any dependencies
				currentLayer.forEach(workerQueue::addWorker);
			} else {
				// This layer has dependencies on workers in the previous layer
				List<Callable<Void>> previousLayer = workers.get(i - 1);

				for (int j = 0; j < numStripes; j++) {
					Callable<Void> worker = currentLayer.get(j);

					// Only VerticalWorker needs data of bordering stripes
					int width = 1;// worker instanceof VerticalWorker? 1 : 0;

					// Create a list of dependencies for this worker
					List<Callable<Void>> dependencies = new ArrayList<>(3);
					for (int k = j - width; k <= j + width; k++) {
						if (k >= 0 && k < previousLayer.size()) {
							dependencies.add(previousLayer.get(k));
						}
					}

					workerQueue.addWorker(worker, dependencies);
				}
			}
		}

		return workerQueue;
	}

	private class PreConvertWorker implements Callable<Void> {
		private final byte[]  inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;

		public PreConvertWorker(byte[] inPixels, short[] outPixels,
		                        int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin * srcWidth * numBands;
			this.end = end * srcWidth * numBands;
		}

		@Override
		public Void call() throws Exception {
			// Linearize or not?
			if (!srcIsSRGB || ignoreSRGB) {
				if (!hasAlpha || srcIsPreAlpha || dontPreAlpha) {
					// convert
					preConvert(inPixels, outPixels, begin, end);
				} else {
					// convert, pre-multiply
					preConvertAlpha(inPixels, outPixels, begin, end);
				}
			} else {
				if (!hasAlpha) {
					// convert, linearize
					preConvertSRGB(inPixels, outPixels, begin, end);
				} else if (srcIsPreAlpha || dontPreAlpha) {
					// convert, linearize colors only
					preConvertSRGBAlpha(inPixels, outPixels, begin, end);
				} else {
					// convert, linearize colors only, pre-multiply alpha
					preConvertSRGBAlphaPremultiply(inPixels, outPixels, begin, end);
				}
			}
			return null;
		}

		private void preConvert(byte[] inPixels, short[] outPixels,
		                        int begin, int end) {
			int q = begin;
			for (int p = begin; p < end; p++) {
				outPixels[q++] = (short)(((inPixels[p] & 0xFF) << 7) - 16384);
			}
		}

		private void preConvertAlpha(byte[] inPixels, short[] outPixels, int begin, int end) {
			int q = begin;
			switch (numBands) {
				case 2:
					for (int p = begin; p < end; ) {
						byte  a          = inPixels[p++];
						float alphaScale = (a & 0xFF) * 128 / 255f;

						// Alpha channel is already linear
						outPixels[q++] = (short)(((a & 0xFF) << 7) - 16384);
						// Pre-multiply by alpha channel
						outPixels[q++] = (short)((inPixels[p++] & 0xFF) * alphaScale - 16384);
					}
					break;
				case 4:
					for (int p = begin; p < end; ) {
						byte  a          = inPixels[p++];
						float alphaScale = (a & 0xFF) * 128 / 255f;

						// Alpha channel is already linear
						outPixels[q++] = (short)(((a & 0xFF) << 7) - 16384);
						// Pre-multiply by alpha channel
						outPixels[q++] = (short)((inPixels[p++] & 0xFF) * alphaScale - 16384);
						outPixels[q++] = (short)((inPixels[p++] & 0xFF) * alphaScale - 16384);
						outPixels[q++] = (short)((inPixels[p++] & 0xFF) * alphaScale - 16384);
					}
			}
		}

		private void preConvertSRGB(byte[] inPixels, short[] outPixels, int begin, int end) {
			int q = begin;
			for (int p = begin; p < end; ) {
				outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
			}
		}

		private void preConvertSRGBAlpha(byte[] inPixels, short[] outPixels,
		                                 int begin, int end) {
			int q = begin;
			switch (numBands) {
				case 2:
					for (int p = begin; p < end; ) {
						// Alpha channel is already linear
						outPixels[q++] = (short)(((inPixels[p++] & 0xFF) << 7) - 16384);
						outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
					}
					break;
				case 4:
					for (int p = begin; p < end; ) {
						// Alpha channel is already linear
						outPixels[q++] = (short)(((inPixels[p++] & 0xFF) << 7) - 16384);
						outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
						outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
						outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
					}
			}
		}

		private void preConvertSRGBAlphaPremultiply(byte[] inPixels, short[] outPixels, int begin, int end) {
			int q = begin;
			switch (numBands) {
				case 2:
					for (int p = begin; p < end; ) {
						byte  a     = inPixels[p++];
						float alpha = (a & 0xFF) / 255f;

						// Alpha channel is already linear
						outPixels[q++] = (short)(((a & 0xFF) << 7) - 16384);
						// Pre-multiply by alpha channel
						outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha - 16384);
					}
					break;
				case 4:
					for (int p = begin; p < end; ) {
						byte a     = inPixels[p++];
						int  alpha = a & 0xFF;

						// Alpha channel is already linear
						outPixels[q++] = (short)((alpha << 7) - 16384);
						// Pre-multiply by alpha channel
						outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255
						                         - 16384);
						outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255
						                         - 16384);
						outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255
						                         - 16384);
					}
			}
		}
	}

	private class HorizontalWorker implements Callable<Void> {
		private final short[] inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;

		public HorizontalWorker(short[] inPixels, short[] outPixels, int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public Void call() throws Exception {
			int     numSamples = horizontalSamplingData.numSamples;
			int[]   indices    = horizontalSamplingData.indices;
			float[] weights    = horizontalSamplingData.weights;

			float sample0;
			float sample1;
			float sample2;
			float sample3;

			switch (numBands) {
				case 1:
					for (int y = begin; y < end; y++) {
						int offset = srcWidth * y;
						for (int x = 0; x < dstWidth; x++) {
							sample0 = 0;
							int index = x * numSamples;
							for (int i = numSamples; i > 0; i--) {
								sample0 += inPixels[offset + indices[index]] * weights[index];
								index++;
							}
							outPixels[(x + y * dstWidth)] = (short)sample0;
						}
					}
					break;
				case 2:
					for (int y = begin; y < end; y++) {
						int offset = srcWidth * y * 2;
						for (int x = 0; x < dstWidth; x++) {
							sample0 = 0;
							sample1 = 0;
							int index = x * numSamples;
							for (int i = numSamples; i > 0; i--) {
								int   arrPixel = offset + indices[index];
								float weight   = weights[index];
								index++;

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
							}
							int u = (x + y * dstWidth) * 2;
							outPixels[u] = (short)sample0;
							outPixels[u + 1] = (short)sample1;
						}
					}
					break;
				case 3:
					for (int y = begin; y < end; y++) {
						int offset = srcWidth * y * 3;
						for (int x = 0; x < dstWidth; x++) {
							sample0 = 0;
							sample1 = 0;
							sample2 = 0;
							int index = x * numSamples;
							for (int i = numSamples; i > 0; i--) {
								int   arrPixel = offset + indices[index];
								float weight   = weights[index];
								index++;

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
								sample2 += inPixels[arrPixel + 2] * weight;
							}
							int u = (x + y * dstWidth) * 3;
							outPixels[u] = (short)sample0;
							outPixels[u + 1] = (short)sample1;
							outPixels[u + 2] = (short)sample2;
						}
					}
					break;
				case 4:
					for (int y = begin; y < end; y++) {
						int offset = srcWidth * y * 4;
						for (int x = 0; x < dstWidth; x++) {
							sample0 = 0;
							sample1 = 0;
							sample2 = 0;
							sample3 = 0;
							int index = x * numSamples;
							for (int i = numSamples; i > 0; i--) {
								int   arrPixel = offset + indices[index];
								float weight   = weights[index];
								index++;

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
								sample2 += inPixels[arrPixel + 2] * weight;
								sample3 += inPixels[arrPixel + 3] * weight;
							}
							int u = (x + y * dstWidth) * 4;
							outPixels[u] = (short)sample0;
							outPixels[u + 1] = (short)sample1;
							outPixels[u + 2] = (short)sample2;
							outPixels[u + 3] = (short)sample3;
						}
					}
			}
			return null;
		}
	}

	private class VerticalWorker implements Callable<Void> {
		private final short[] inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;
		private final int     width;

		public VerticalWorker(short[] inPixels, short[] outPixels,
		                      int begin, int end, int width) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin;
			this.end = end;
			this.width = width;
		}

		@Override
		public Void call() throws Exception {
			int       numSamples = verticalSamplingData.numSamples;
			int[][]   indices2D  = verticalSamplingData.indices2D;
			float[][] weights2D  = verticalSamplingData.weights2D;

			float sample0;
			float sample1;
			float sample2;
			float sample3;

			switch (numBands) {
				case 1:
					for (int y = begin; y < end; y++) {
						int     u       = y * width;
						int[]   indices = indices2D[y];
						float[] weights = weights2D[y];
						for (int x = 0; x < width; x++) {
							sample0 = 0;
							for (int i = 0; i < numSamples; i++) {
								sample0 += inPixels[x + indices[i]] * weights[i];
							}
							outPixels[u++] = (short)sample0;
						}
					}
					break;
				case 2:
					for (int y = begin; y < end; y++) {
						int     u       = y * width * 2;
						int[]   indices = indices2D[y];
						float[] weights = weights2D[y];
						for (int x = 0; x < width; x++) {
							sample0 = 0;
							sample1 = 0;
							for (int i = 0; i < numSamples; i++) {
								int   arrPixel = x * 2 + indices[i];
								float weight   = weights[i];

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
							}
							outPixels[u++] = (short)sample0;
							outPixels[u++] = (short)sample1;
						}
					}
					break;
				case 3:
					for (int y = begin; y < end; y++) {
						int     u       = y * width * 3;
						int[]   indices = indices2D[y];
						float[] weights = weights2D[y];
						for (int x = 0; x < width; x++) {
							sample0 = 0;
							sample1 = 0;
							sample2 = 0;
							for (int i = 0; i < numSamples; i++) {
								int   arrPixel = x * 3 + indices[i];
								float weight   = weights[i];

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
								sample2 += inPixels[arrPixel + 2] * weight;
							}
							outPixels[u++] = (short)sample0;
							outPixels[u++] = (short)sample1;
							outPixels[u++] = (short)sample2;
						}
					}
					break;
				case 4:
					for (int y = begin; y < end; y++) {
						int     u       = y * width * 4;
						int[]   indices = indices2D[y];
						float[] weights = weights2D[y];
						for (int x = 0; x < width; x++) {
							sample0 = 0;
							sample1 = 0;
							sample2 = 0;
							sample3 = 0;
							for (int i = 0; i < numSamples; i++) {
								int   arrPixel = x * 4 + indices[i];
								float weight   = weights[i];

								sample0 += inPixels[arrPixel] * weight;
								sample1 += inPixels[arrPixel + 1] * weight;
								sample2 += inPixels[arrPixel + 2] * weight;
								sample3 += inPixels[arrPixel + 3] * weight;
							}
							outPixels[u++] = (short)sample0;
							outPixels[u++] = (short)sample1;
							outPixels[u++] = (short)sample2;
							outPixels[u++] = (short)sample3;
						}
					}
			}
			return null;
		}
	}

	private class PostConvertWorker implements Callable<Void> {
		private final short[] inPixels;
		private final byte[]  outPixels;
		private final int     begin;
		private final int     end;

		public PostConvertWorker(short[] inPixels, byte[] outPixels, int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin * dstWidth * numBands;
			this.end = end * dstWidth * numBands;
		}

		@Override
		public Void call() throws Exception {
			// Un-linearize or not?
			if (!srcIsSRGB || ignoreSRGB) {
				if (!hasAlpha || srcIsPreAlpha || dontPreAlpha) {
					// convert
					postConvert(inPixels, outPixels, begin, end);
				} else {
					// convert, un-pre-multiply
					// FIXME
					postConvertAlpha(inPixels, outPixels, begin, end);
				}
			} else {
				if (!hasAlpha) {
					// convert, un-linearize
					// FIXME Overshoot? (untested)
					postConvertSRGB(inPixels, outPixels, begin, end);
				} else if (srcIsPreAlpha || dontPreAlpha) {
					// convert, un-linearize colors only
					// FIXME
					postConvertSRGBAlpha(inPixels, outPixels, begin, end);
				} else {
					// convert, un-linearize colors only, un-pre-multiply alpha
					// FIXME
					postConvertSRGBAlphaUnMultiply(inPixels, outPixels, begin, end);
				}
			}
			return null;
		}

		private void postConvert(short[] inPixels, byte[] outPixels, int begin, int end) {
			int q = begin;
			int p = begin;
			while (p < end) {
				short f = inPixels[p++];
				outPixels[q++] = f <= -16257 ? 0 : f >= 16256 ? -1 : (byte)((f + 16384) >> 7);
			}
		}

		private void postConvertAlpha(short[] inPixels, byte[] outPixels, int begin, int end) {
			int q = begin;
			int p = begin;
			switch (numBands) {
				case 2:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						float alphaInv = 32640.0f / (a + 16384);
						int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

						outPixels[q++] = (byte)((a + 16384) >> 7); // Alpha channel
						outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : (byte)(r >> 7);
					}
					break;
				case 4:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						float alphaInv = 32640.0f / (a + 16384);
						int   b        = (int)((inPixels[p++] + 16384) * alphaInv);
						int   g        = (int)((inPixels[p++] + 16384) * alphaInv);
						int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

						outPixels[q++] = (byte)((a + 16384) >> 7); // Alpha channel
						outPixels[q++] = b <= 0 ? 0 : b >= 32640 ? -1 : (byte)(b >> 7);
						outPixels[q++] = g <= 0 ? 0 : g >= 32640 ? -1 : (byte)(g >> 7);
						outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : (byte)(r >> 7);
					}
					break;
			}
		}

		private void postConvertSRGB(short[] inPixels, byte[] outPixels, int begin, int end) {
			int q = begin;
			int p = begin;
			while (p < end) {
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
			}
		}

		private void postConvertSRGBAlpha(short[] inPixels, byte[] outPixels, int begin, int end) {
			int q = begin;
			int p = begin;
			switch (numBands) {
				case 2:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						outPixels[q++] = (byte)((a + 16384) >> 7); // Don't sRGB the alpha channel
						outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
					}
					break;
				case 4:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						outPixels[q++] = (byte)((a + 16384) >> 7); // Don't sRGB the alpha channel
						outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
						outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
						outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
					}
					break;
			}
		}

		private void postConvertSRGBAlphaUnMultiply(short[] inPixels, byte[] outPixels, int begin, int end) {
			int q = begin;
			int p = begin;
			switch (numBands) {
				case 2:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						float alphaInv = 32640.0f / (a + 16384);
						int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

						outPixels[q++] = (byte)((a + 16384) >> 7); // Don't sRGB the alpha channel
						outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[r];
					}
					break;
				case 4:
					while (p < end) {
						short a = inPixels[p++];
						a = a <= -16384 ? -16384 : a >= 16256 ? 16256 : a;

						float alphaInv = 32640.0f / (a + 16384);
						int   b        = (int)((inPixels[p++] + 16384) * alphaInv);
						int   g        = (int)((inPixels[p++] + 16384) * alphaInv);
						int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

						outPixels[q++] = (byte)((a + 16384) >> 7); // Don't sRGB the alpha channel
						outPixels[q++] = b <= 0 ? 0 : b >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[b];
						outPixels[q++] = g <= 0 ? 0 : g >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[g];
						outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[r];
					}
					break;
			}
		}
	}
}
