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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.ProgressEvent;
import org.digitalmodular.imageutilities.internal.DependentWorkerQueue;
import org.digitalmodular.imageutilities.internal.PerformanceTimer;
import static org.digitalmodular.imageutilities.resize.SamplingDataCalculator.SamplingData;
import static org.digitalmodular.imageutilities.resize.SamplingDataCalculator.calculateNumSamples;
import static org.digitalmodular.imageutilities.resize.SamplingDataCalculator.createSubSampling;

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
			BYTE_SRGB_TO_SHORT[b] = (short)Math.floor(fromSRGB(f) * 32640 - 16384 + 0.5);
			// With offset already implemented
			BYTE_SRGB_TO_SHORT2[b] = (short)(BYTE_SRGB_TO_SHORT[b] + 16384);
		}

		for (int s = -32768; s < 32768; s++) {
			double f = s < -16384 ? 0 : s >= 16256 ? 1 : (s + 16384) / 32640.0;
			SHORT_TO_BYTE_SRGB[s & 0xFFFF] = (byte)Math.floor(toSRGB(f) * 255 + 0.5);
			// With offset already implemented
			SHORT2_TO_BYTE_SRGB[(s + 16384) & 0xFFFF] = SHORT_TO_BYTE_SRGB[s & 0xFFFF];
		}
	}

	public static double toSRGB(double f) {
		return f < 0.0031308f ? f * 12.92f : Math.pow(f, 1 / 2.4) * 1.055f - 0.055f;
	}

	public static double fromSRGB(double f) {
		return f < 0.04045f ? f / 12.92f : Math.pow((f + 0.055f) / 1.055f, 2.4);
	}

	private enum ResamplingOrder {
		NONE,
		X_ONLY,
		Y_ONLY,
		X_FIRST,
		Y_FIRST
	}

	private final PerformanceTimer timer = new PerformanceTimer();

	private SamplingData horizontalSamplingData = null;
	private SamplingData verticalSamplingData   = null;

	/**
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

		if (Thread.currentThread().isInterrupted())
			throw new InterruptedException();

		BufferedImage src = makeImageCompatible(image);

		timer.record("Convert");

		// Create output image with same properties as the input image after pre-conversion
		BufferedImage out = createCompatibleOutputImage(src);

		// Prepare the work buffers
		byte[]  srcPixels  = ((DataBufferByte)src.getRaster().getDataBuffer()).getData();
		short[] srcBuffer  = new short[srcPixels.length];
		short[] workBuffer = makeWorkBuffer(order);
		short[] dstBuffer  = new short[dstWidth * dstHeight * numChannels];
		byte[]  dstPixels  = ((DataBufferByte)out.getRaster().getDataBuffer()).getData();

		if (Thread.currentThread().isInterrupted())
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

		if (Thread.currentThread().isInterrupted())
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

		ResamplingOrder order;
		if (!doX && !doY)
			order = ResamplingOrder.NONE;
		else if (!doY)
			order = ResamplingOrder.X_ONLY;
		else if (!doX)
			order = ResamplingOrder.Y_ONLY;
		else {
			// Calculate the work effort of each possible sub-process.
			// The +1 tweak comes from the store operation for each resampled pixel.
			long effortXFirst  = (long)srcHeight * dstWidth * (calculateNumSamples(filter, widthScaleFactor) + 1);
			long effortYSecond = (long)dstWidth * dstHeight * (calculateNumSamples(filter, heightScaleFactor) + 1);
			long effortYFirst  = (long)srcWidth * dstHeight * (calculateNumSamples(filter, heightScaleFactor) + 1);
			long effortXSecond = (long)dstHeight * dstWidth * (calculateNumSamples(filter, widthScaleFactor) + 1);

			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest("Efforts: " + effortXFirst + '+' + effortYSecond +
				                          '=' + (effortXFirst + effortYSecond) +
				                          " <> " + effortYFirst + '+' + effortXSecond +
				                          '=' + (effortYFirst + effortXSecond));

			if (effortXFirst + effortYSecond <= effortYFirst + effortXSecond)
				order = ResamplingOrder.X_FIRST;
			else
				order = ResamplingOrder.Y_FIRST;
		}

		if (Logger.getGlobal().isLoggable(Level.FINEST))
			Logger.getGlobal().finest("Resampling order: " + order);

		return order;
	}

	private BufferedImage createCompatibleOutputImage(BufferedImage src) {
		numChannels = src.getRaster().getNumBands();
		int srcColorType = ImageUtilities.getColorSpaceType(src.getColorModel().getColorSpace());
		hasAlpha = src.getColorModel().hasAlpha();
		srcIsSRGB = srcColorType != ColorSpace.CS_LINEAR_RGB;
		srcIsPreAlpha = src.getColorModel().isAlphaPremultiplied();
		// IMPROVE: extra check to see if entire palette (except transparent index) is gray

		BufferedImage img = ImageUtilities.createByteImage(dstWidth, dstHeight, numChannels,
		                                                   srcColorType, hasAlpha, srcIsPreAlpha);

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
				return new short[dstWidth * srcHeight * numChannels];
			case Y_FIRST:
				// First step: use only height from dst
				return new short[srcWidth * dstHeight * numChannels];
			default:
				throw new AssertionError(resamplingOrder);
		}
	}

	private void preCalculateSubSampling(ResamplingOrder resampleOrder) {
		switch (resampleOrder) {
			case X_ONLY:
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, widthScaleFactor, offsetX, numChannels);
				break;
			case Y_ONLY:
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, heightScaleFactor, offsetY, numChannels * srcWidth);
				break;
			case X_FIRST:
				// Pre-calculate sub-sampling
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, widthScaleFactor, offsetX, numChannels);
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, heightScaleFactor, offsetY, numChannels * dstWidth);
				break;
			case Y_FIRST:
				// Pre-calculate sub-sampling
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, heightScaleFactor, offsetY, numChannels * srcWidth);
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, widthScaleFactor, offsetX, numChannels);
				break;
			default:
				throw new AssertionError(resampleOrder);
		}
	}

	private List<List<Callable<Void>>> makeWorkerLists(ResamplingOrder resamplingOrder,
	                                                   byte[] srcPixels, short[] srcBuffer,
	                                                   short[] workBuffer,
	                                                   short[] dstBuffer, byte[] dstPixels) {
		int numStrips = getNumThreads() == 0 ? AVAILABLE_PROCESSORS : getNumThreads();

		// Make 4 lists of workers for each of the steps in the process.
		List<Callable<Void>> preConvertWorkers  = new ArrayList<>(numStrips);
		List<Callable<Void>> step1Workers       = new ArrayList<>(numStrips);
		List<Callable<Void>> step2Workers       = new ArrayList<>(numStrips);
		List<Callable<Void>> postConvertWorkers = new ArrayList<>(numStrips);

		// Divide the rows of the image in approximately equal pieces
		int numLayers = 0;
		for (int i = 0; i < numStrips; i++) {
			int srcBegin = i * srcHeight / numStrips;
			int srcEnd   = (i + 1) * srcHeight / numStrips;
			int dstBegin = i * dstHeight / numStrips;
			int dstEnd   = (i + 1) * dstHeight / numStrips;

			// First step: pre-convert
			preConvertWorkers.add(new PreConvertWorker(srcPixels, srcBuffer, srcBegin, srcEnd));

			// Intermediate steps: X and/or Y resampling
			switch (resamplingOrder) {
				case X_ONLY:
					step1Workers.add(new HorizontalResampleWorker(srcBuffer, dstBuffer, dstBegin, dstEnd));
					break;
				case Y_ONLY:
					step1Workers.add(new VerticalResampleWorker(srcBuffer, dstBuffer, dstBegin, dstEnd, srcWidth));
					break;
				case X_FIRST:
					step1Workers.add(new HorizontalResampleWorker(srcBuffer, workBuffer, srcBegin, srcEnd));
					step2Workers.add(new VerticalResampleWorker(workBuffer, dstBuffer, dstBegin, dstEnd, dstWidth));
					break;
				case Y_FIRST:
					step1Workers.add(new VerticalResampleWorker(srcBuffer, workBuffer, dstBegin, dstEnd, srcWidth));
					step2Workers.add(new HorizontalResampleWorker(workBuffer, dstBuffer, dstBegin, dstEnd));
					break;
				default:
					throw new AssertionError(resamplingOrder);
			}

			// Last step: pre-convert
			postConvertWorkers.add(new PostConvertWorker(dstBuffer, dstPixels, dstBegin, dstEnd));
		}

		if (step2Workers.isEmpty())
			return Arrays.asList(preConvertWorkers, step1Workers, postConvertWorkers);
		else
			return Arrays.asList(preConvertWorkers, step1Workers, step2Workers, postConvertWorkers);
	}

	private DependentWorkerQueue<Void> makeResampleQueue(List<List<Callable<Void>>> workers) {
		int numStrips = getNumThreads() == 0 ? AVAILABLE_PROCESSORS : getNumThreads();

		DependentWorkerQueue<Void> workerQueue = new DependentWorkerQueue<>();

		for (int i = 0; i < workers.size(); i++) {
			List<Callable<Void>> currentLayer = workers.get(i);
			if (i == 0) {
				// Workers in the first layer don't have any dependencies
				currentLayer.forEach(workerQueue::addWorker);
			} else {
				// This layer has dependencies on workers in the previous layer
				List<Callable<Void>> previousLayer = workers.get(i - 1);

				for (int j = 0; j < numStrips; j++) {
					Callable<Void> worker = currentLayer.get(j);

					// Only VerticalResampleWorker needs data of bordering strips
					int width = numStrips;// FIXME worker instanceof VerticalResampleWorker? 1 : 0;
					// FIXME: just 0 or 1 is not adequate. It fails when strips are narrower than the filter radius.

					// Create a list of dependencies for this worker
					Collection<Callable<Void>> dependencies = new ArrayList<>(3);
					for (int k = j - width; k <= j + width; k++)
						if (k >= 0 && k < previousLayer.size())
							dependencies.add(previousLayer.get(k));

					workerQueue.addWorker(worker, dependencies);
				}
			}
		}

		return workerQueue;
	}

	private final class PreConvertWorker implements Callable<Void> {
		private final byte[]  inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;

		private PreConvertWorker(byte[] inPixels, short[] outPixels, int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin * srcWidth * numChannels;
			this.end = end * srcWidth * numChannels;
		}

		@Override
		public Void call() throws Exception {
			if (!srcIsSRGB || ignoreSRGB) {
				if (!hasAlpha || srcIsPreAlpha || dontPreAlpha) {
					// Just convert. (there's no distinction between with and without alpha)
					preConvert();
				} else {
					// Convert, premultiply
					preConvertAlphaPremultiply();
				}
			} else {
				if (!hasAlpha) {
					// Convert, linearize
					preConvertSRGB();
				} else if (srcIsPreAlpha || dontPreAlpha) {
					// Convert, linearize colors, not alpha
					preConvertSRGBAlpha();
				} else {
					// Convert, linearize colors, premultiply
					preConvertSRGBAlphaPremultiply();
				}
			}

			return null;
		}

		private void preConvertAlphaPremultiply() {
			switch (numChannels) {
				case 2:
					preConvertAlphaPremultiply2Channels();
					break;
				case 4:
					preConvertAlphaPremultiply4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void preConvertSRGBAlpha() {
			switch (numChannels) {
				case 2:
					preConvertSRGBAlpha2Channels();
					break;
				case 4:
					preConvertSRGBAlpha4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void preConvertSRGBAlphaPremultiply() {
			switch (numChannels) {
				case 2:
					preConvertSRGBAlphaPremultiply2Channels();
					break;
				case 4:
					preConvertSRGBAlphaPremultiply4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void preConvert() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end)
				// All channels are linear (alpha channel may be present)
				outPixels[q++] = (short)(((inPixels[p++] & 0xFF) << 7) - 16384);
		}

		private void preConvertAlphaPremultiply2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int gray  = (inPixels[p++] & 0xFF) * 128;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Premultiply by alpha channel
				outPixels[q++] = (short)(gray * alpha / 255 - 16384);
			}
		}

		private void preConvertAlphaPremultiply4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int b     = (inPixels[p++] & 0xFF) * 128;
				int g     = (inPixels[p++] & 0xFF) * 128;
				int r     = (inPixels[p++] & 0xFF) * 128;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Premultiply by alpha channel
				outPixels[q++] = (short)(b * alpha / 255 - 16384);
				outPixels[q++] = (short)(g * alpha / 255 - 16384);
				outPixels[q++] = (short)(r * alpha / 255 - 16384);
			}
		}

		private void preConvertSRGB() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end)
				// All channels are linearized (no alpha channel present)
				outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
		}

		private void preConvertSRGBAlpha2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int gray  = inPixels[p++] & 0xFF;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Linearize other channels
				outPixels[q++] = BYTE_SRGB_TO_SHORT[gray];
			}
		}

		private void preConvertSRGBAlpha4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int b     = inPixels[p++] & 0xFF;
				int g     = inPixels[p++] & 0xFF;
				int r     = inPixels[p++] & 0xFF;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Linearize other channels
				outPixels[q++] = BYTE_SRGB_TO_SHORT[b];
				outPixels[q++] = BYTE_SRGB_TO_SHORT[g];
				outPixels[q++] = BYTE_SRGB_TO_SHORT[r];
			}
		}

		private void preConvertSRGBAlphaPremultiply2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int gray  = inPixels[p++] & 0xFF;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Premultiply by alpha channel and linearize other channels
				outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[gray] * alpha / 255 - 16384);
			}
		}

		private void preConvertSRGBAlphaPremultiply4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			byte[]  inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int alpha = inPixels[p++] & 0xFF;
				int b     = inPixels[p++] & 0xFF;
				int g     = inPixels[p++] & 0xFF;
				int r     = inPixels[p++] & 0xFF;

				// Alpha channel is always linear
				outPixels[q++] = (short)((alpha << 7) - 16384);
				// Premultiply by alpha channel and linearize other channels
				outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[b] * alpha / 255 - 16384);
				outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[g] * alpha / 255 - 16384);
				outPixels[q++] = (short)(BYTE_SRGB_TO_SHORT2[r] * alpha / 255 - 16384);
			}
		}
	}

	private final class HorizontalResampleWorker implements Callable<Void> {
		private final short[] inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;

		private HorizontalResampleWorker(short[] inPixels, short[] outPixels, int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin;
			this.end = end;
		}

		@Override
		public Void call() throws Exception {
			int     numSamples = horizontalSamplingData.getNumSamples();
			int[]   indicesX   = horizontalSamplingData.getIndicesX();
			float[] weightsX   = horizontalSamplingData.getWeightsX();

			switch (numChannels) {
				case 1:
					horizontalResample1Channel(numSamples, indicesX, weightsX);
					break;
				case 2:
					horizontalResample2Channels(numSamples, indicesX, weightsX);
					break;
				case 3:
					horizontalResample3Channels(numSamples, indicesX, weightsX);
					break;
				case 4:
					horizontalResample4Channels(numSamples, indicesX, weightsX);
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
			return null;
		}

		private void horizontalResample1Channel(int numSamples, int[] indicesX, float[] weightsX) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     srcWidth  = ImageResamplerShort.this.srcWidth;
			int     dstWidth  = ImageResamplerShort.this.dstWidth;
			for (int y = begin; y < end; y++) {
				int offset = srcWidth * y;
				for (int x = 0; x < dstWidth; x++) {
					float sample0 = 0;
					int   index   = x * numSamples;
					for (int i = numSamples; i > 0; i--) {
						sample0 += inPixels[offset + indicesX[index]] * weightsX[index];
						index++;
					}
					outPixels[(x + y * dstWidth)] = (short)sample0;
				}
			}
		}

		private void horizontalResample2Channels(int numSamples, int[] indicesX, float[] weightsX) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     srcWidth  = ImageResamplerShort.this.srcWidth;
			int     dstWidth  = ImageResamplerShort.this.dstWidth;
			for (int y = begin; y < end; y++) {
				int offset = srcWidth * y * 2;
				for (int x = 0; x < dstWidth; x++) {
					float sample0 = 0;
					float sample1 = 0;
					int   index   = x * numSamples;
					for (int i = numSamples; i > 0; i--) {
						int   arrPixel = offset + indicesX[index];
						float weight   = weightsX[index];
						index++;

						sample0 += inPixels[arrPixel] * weight;
						sample1 += inPixels[arrPixel + 1] * weight;
					}
					int u = (x + y * dstWidth) * 2;
					outPixels[u] = (short)sample0;
					outPixels[u + 1] = (short)sample1;
				}
			}
		}

		private void horizontalResample3Channels(int numSamples, int[] indicesX, float[] weightsX) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     srcWidth  = ImageResamplerShort.this.srcWidth;
			int     dstWidth  = ImageResamplerShort.this.dstWidth;
			for (int y = begin; y < end; y++) {
				int offset = srcWidth * y * 3;
				for (int x = 0; x < dstWidth; x++) {
					float sample0 = 0;
					float sample1 = 0;
					float sample2 = 0;
					int   index   = x * numSamples;
					for (int i = numSamples; i > 0; i--) {
						int   arrPixel = offset + indicesX[index];
						float weight   = weightsX[index];
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
		}

		private void horizontalResample4Channels(int numSamples, int[] indicesX, float[] weightsX) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     srcWidth  = ImageResamplerShort.this.srcWidth;
			int     dstWidth  = ImageResamplerShort.this.dstWidth;
			for (int y = begin; y < end; y++) {
				int offset = srcWidth * y * 4;
				for (int x = 0; x < dstWidth; x++) {
					float sample0 = 0;
					float sample1 = 0;
					float sample2 = 0;
					float sample3 = 0;
					int   index   = x * numSamples;
					for (int i = numSamples; i > 0; i--) {
						int   arrPixel = offset + indicesX[index];
						float weight   = weightsX[index];
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
	}

	private final class VerticalResampleWorker implements Callable<Void> {
		private final short[] inPixels;
		private final short[] outPixels;
		private final int     begin;
		private final int     end;
		private final int     width;

		private VerticalResampleWorker(short[] inPixels, short[] outPixels, int begin, int end, int width) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin;
			this.end = end;
			this.width = width;
		}

		@Override
		public Void call() throws Exception {
			int       numSamples = verticalSamplingData.getNumSamples();
			int[][]   indicesY   = verticalSamplingData.getIndicesY();
			float[][] weightsY   = verticalSamplingData.getWeightsY();

			switch (numChannels) {
				case 1:
					verticalResample1Channel(numSamples, indicesY, weightsY);
					break;
				case 2:
					verticalResample2Channels(numSamples, indicesY, weightsY);
					break;
				case 3:
					verticalResample3Channels(numSamples, indicesY, weightsY);
					break;
				case 4:
					verticalResample4Channels(numSamples, indicesY, weightsY);
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
			return null;
		}

		private void verticalResample1Channel(int numSamples, int[][] indicesY, float[][] weightsY) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     width     = this.width;
			for (int y = begin; y < end; y++) {
				int     u       = y * width;
				int[]   indices = indicesY[y];
				float[] weights = weightsY[y];
				for (int x = 0; x < width; x++) {
					float sample0 = 0;
					for (int i = 0; i < numSamples; i++) {
						sample0 += inPixels[x + indices[i]] * weights[i];
					}
					outPixels[u++] = (short)sample0;
				}
			}
		}

		private void verticalResample2Channels(int numSamples, int[][] indicesY, float[][] weightsY) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     width     = this.width;
			for (int y = begin; y < end; y++) {
				int     u       = y * width * 2;
				int[]   indices = indicesY[y];
				float[] weights = weightsY[y];
				for (int x = 0; x < width; x++) {
					float sample0 = 0;
					float sample1 = 0;
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
		}

		private void verticalResample3Channels(int numSamples, int[][] indicesY, float[][] weightsY) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     width     = this.width;
			for (int y = begin; y < end; y++) {
				int     u       = y * width * 3;
				int[]   indices = indicesY[y];
				float[] weights = weightsY[y];
				for (int x = 0; x < width; x++) {
					float sample0 = 0;
					float sample1 = 0;
					float sample2 = 0;
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
		}

		private void verticalResample4Channels(int numSamples, int[][] indicesY, float[][] weightsY) {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			short[] outPixels = this.outPixels;
			int     end       = this.end;
			int     width     = this.width;
			for (int y = begin; y < end; y++) {
				int     u       = y * width * 4;
				int[]   indices = indicesY[y];
				float[] weights = weightsY[y];
				for (int x = 0; x < width; x++) {
					float sample0 = 0;
					float sample1 = 0;
					float sample2 = 0;
					float sample3 = 0;
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
	}

	private final class PostConvertWorker implements Callable<Void> {
		private final short[] inPixels;
		private final byte[]  outPixels;
		private final int     begin;
		private final int     end;

		private PostConvertWorker(short[] inPixels, byte[] outPixels, int begin, int end) {
			this.inPixels = inPixels;
			this.outPixels = outPixels;
			this.begin = begin * dstWidth * numChannels;
			this.end = end * dstWidth * numChannels;
		}

		@Override
		public Void call() throws Exception {
			// Un-linearize or not?
			if (!srcIsSRGB || ignoreSRGB) {
				if (!hasAlpha || srcIsPreAlpha || dontPreAlpha) {
					// Just convert. (there's no distinction between with and without alpha)
					postConvert();
				} else {
					// Convert, un-premultiply
					postConvertAlphaUnPremultiply();
				}
			} else {
				if (!hasAlpha) {
					// Convert, un-linearize
					postConvertSRGB();
				} else if (srcIsPreAlpha || dontPreAlpha) {
					// Convert, un-linearize colors, not alpha
					postConvertSRGBAlpha();
				} else {
					// Convert, un-linearize colors, un-premultiply
					postConvertSRGBAlphaUnPremultiply();
				}
			}
			return null;
		}

		private void postConvertAlphaUnPremultiply() {
			switch (numChannels) {
				case 2:
					postConvertAlphaUnPremultiply2Channels();
					break;
				case 4:
					postConvertAlphaUnPremultiply4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void postConvertSRGBAlpha() {
			switch (numChannels) {
				case 2:
					postConvertSRGBAlpha2Channels();
					break;
				case 4:
					postConvertSRGBAlpha4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void postConvertSRGBAlphaUnPremultiply() {
			switch (numChannels) {
				case 2:
					postConvertSRGBAlphaUnPremultiply2Channels();
					break;
				case 4:
					postConvertSRGBAlphaUnPremultiply4Channels();
					break;
				default:
					throw new AssertionError("numChannels: " + numChannels);
			}
		}

		private void postConvert() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				// All channels are linear (alpha channel may be present)
				short s = inPixels[p++];
				outPixels[q++] = s <= -16384 ? 0 : s >= 16256 ? -1 : (byte)((s + 16384) >> 7);
			}
		}

		private void postConvertAlphaUnPremultiply2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Un-premultiply by alpha channel
				float alphaInv = 32640.0f / a;
				int   gray     = (int)((inPixels[p++] + 16384) * alphaInv);

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7); // Alpha channel
				outPixels[q++] = gray <= 0 ? 0 : gray >= 32640 ? -1 : (byte)(gray >> 7);
			}
		}

		private void postConvertAlphaUnPremultiply4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Un-premultiply by alpha channel
				float alphaInv = 32640.0f / a;
				int   b        = (int)((inPixels[p++] + 16384) * alphaInv);
				int   g        = (int)((inPixels[p++] + 16384) * alphaInv);
				int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7);
				outPixels[q++] = b <= 0 ? 0 : b >= 32640 ? -1 : (byte)(b >> 7);
				outPixels[q++] = g <= 0 ? 0 : g >= 32640 ? -1 : (byte)(g >> 7);
				outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : (byte)(r >> 7);
			}
		}

		private void postConvertSRGB() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end)
				// All channels are un-linearized (no alpha channel present)
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
		}

		private void postConvertSRGBAlpha2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7);
				// Un-linearize other channels
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
			}
		}

		private void postConvertSRGBAlpha4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7);
				// Un-linearize other channels
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
				outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
			}
		}

		private void postConvertSRGBAlphaUnPremultiply2Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Un-premultiply by alpha channel
				float alphaInv = 32640.0f / a;
				int   g        = (int)((inPixels[p++] + 16384) * alphaInv);

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7);
				// Un-linearize other channels
				outPixels[q++] = g <= 0 ? 0 : g >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[g];
			}
		}

		private void postConvertSRGBAlphaUnPremultiply4Channels() {
			if (Logger.getGlobal().isLoggable(Level.FINEST))
				Logger.getGlobal().finest(begin + ".." + end);

			short[] inPixels  = this.inPixels;
			byte[]  outPixels = this.outPixels;
			int     q         = begin;
			int     p         = begin;
			int     end       = this.end;
			while (p < end) {
				int a = inPixels[p++];
				a = a <= -16384 ? 0 : a >= 16256 ? 32640 : a + 16384;

				// Un-premultiply by alpha channel
				float alphaInv = 32640.0f / a;
				int   b        = (int)((inPixels[p++] + 16384) * alphaInv);
				int   g        = (int)((inPixels[p++] + 16384) * alphaInv);
				int   r        = (int)((inPixels[p++] + 16384) * alphaInv);

				// Alpha channel is always linear
				outPixels[q++] = (byte)(a >> 7);
				// Un-linearize other channels
				outPixels[q++] = b <= 0 ? 0 : b >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[b];
				outPixels[q++] = g <= 0 ? 0 : g >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[g];
				outPixels[q++] = r <= 0 ? 0 : r >= 32640 ? -1 : SHORT2_TO_BYTE_SRGB[r];
			}
		}
	}
}
