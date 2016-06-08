/*
 * This file is part of ImageUtilities.
 *
 * Copyleft 2014 Mark Jeronimus. All Rights Reversed.
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
import java.util.concurrent.ExecutionException;
import org.digitalmodular.imageutilities.ImageUtilities;

//@formatter:off // Eclipse formatter changes link semantics by breaking on a dash (thinks it's a hyphen)

/**
 * Based on work from java-image-scaling
 * (<a href="https://github.com/mortennobel/java-image-scaling">https://github.com/mortennobel/java-image-scaling</a>)
 * <p>
 * Properties:<ul>
 * <li>Compatible images: {@link BufferedImage} with G, AG, BGR, or ABGR interleaved byte data</li>
 * <li>Internal format: 15 bits per band</li>
 * <li>Intermediate clamping: no</li>
 * <li>Linearity: sRGB correction when necessary.</li>
 * <li>Parallel processing: yes, with checkpoints. Each partial resampling step starts when the previous step
 * entirely finishes.</li>
 * <li></li></ul>
 * <p>
 * All pages data buffers are in {@code short} format. The values are converted to 15 bits per channel,
 * approximately centered on zero. The 16th bit allows for overflows, to prevent the need for clamping. The
 * exact range is -16384 to 16256, which is 32640 steps. This value comes from 255*128, the formula used to
 * linearly convert to and from byte images.
 *
 * @author Mark Jeronimus
 * @since 1.0
 */
//@formatter:on
// Date 2015-08-14
public class ImageResamplerShort2 extends AbstractImageResampler {
	protected static final short[] BYTE_SRGB_TO_SHORT  = new short[256];
	protected static final short[] BYTE_SRGB_TO_SHORT2 = new short[256];
	protected static final byte[]  SHORT_TO_BYTE_SRGB  = new byte[65536];
	protected static final byte[]  SHORT2_TO_BYTE_SRGB = new byte[65536];

	static {
		for (int b = 0; b < 256; b++) {
			double f = (b & 0xFF) / 255.0;
			BYTE_SRGB_TO_SHORT[b] = (short) (fromSRGB(f) * 32640 - 16384);
			// With offset already implemented
			BYTE_SRGB_TO_SHORT2[b] = (short) (BYTE_SRGB_TO_SHORT[b] + 16384);
		}

		for (int s = -32768; s < 32768; s++) {
			double f = s < -16384 ? 0 : s >= 16256 ? 1 : (s + 16384) / 32640.0;
			SHORT_TO_BYTE_SRGB[s & 0xFFFF] = (byte) (toSRGB(f) * 255);
			// With offset already implemented
			SHORT2_TO_BYTE_SRGB[(s + 16384) & 0xFFFF] = (byte) (toSRGB(f) * 255);
		}
	}

	public static final double toSRGB(double f) {
		return f < 0.0031308f ? f * 12.92f : Math.pow(f, 1 / 2.4) * 1.055f - 0.055f;
	}

	public static final double fromSRGB(double f) {
		return f < 0.04045f ? f / 12.92f : Math.pow((f + 0.055f) / 1.055f, 2.4);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @return a resized {@link BufferedImage} or the unmodified input image (
	 */
	@Override
	public synchronized BufferedImage resize(BufferedImage image) throws InterruptedException {
		long t0;
		long t1;
		long t2;
		long t3;
		long t4;
		t0 = System.nanoTime();
		numThreads = AVAILABLE_PROCESSORS;
		executor.setCorePoolSize(numThreads);

		calculateDstSizeAndScale(image);

		boolean doH             = srcWidth != dstWidth || offsetX != 0;
		boolean doV             = srcHeight != dstHeight || offsetY != 0;
		boolean horizontalFirst = true;

		// Calculate the work done in either case and choose the fastest sequence.
		// The +1 comes from the store operation.
		long effortH0 = (long) srcHeight * dstWidth * (calculateNumSamples(filter, scaleWidth) + 1);
		long effortV0 = (long) srcWidth * dstHeight * (calculateNumSamples(filter, scaleHeight) + 1);
		long effortH1 = (long) dstHeight * dstWidth * (calculateNumSamples(filter, scaleWidth) + 1);
		long effortV1 = (long) dstWidth * dstHeight * (calculateNumSamples(filter, scaleHeight) + 1);

		if (!doH && !doV) {
// System.out.println("Effort matrix: 0 0 / 0 0");
// System.out.println("Resizing order: no resize");
// return image;
		} else if (!doV) {
// System.out.println("Effort matrix: " + effortH0 + " 0 / 0 " + effortH1);
			horizontalFirst = true;
// System.out.println("Resizing order: Horzontal only");
		} else if (!doH) {
// System.out.println("Effort matrix: 0 " + effortV0 + " / " + effortV1 + " " + 0);
			horizontalFirst = false;
// System.out.println("Resizing order: Vertical only");
		} else {
// System.out.println("Effort matrix: "
// + effortH0 + " " + effortV0 + " / "
// + effortV1 + " " + effortH1);
			horizontalFirst = effortH0 + effortV1 < effortV0 + effortH1;
// System.out.println("Resizing order: " + (horizontalFirst? "Horzontal" : "Vertical") + " first");
		}

// System.out.print("input img: ");
// ImageUtilities.analyzeImage(image);

		BufferedImage src;
		if (imageIsCompatible(image)) {
			src = image;
		} else {
			src = makeImageCompatible(image);
		}

		if (Thread.interrupted()) {
			throw new InterruptedException();
		}

		numBands = src.getRaster().getNumBands();
		int srcColorType = ImageUtilities.getColorSpaceType(src.getColorModel().getColorSpace());
		hasAlpha = src.getColorModel().hasAlpha();
		srcIsSRGB = srcColorType != ColorSpace.CS_LINEAR_RGB;
		srcIsPreAlpha = src.getColorModel().isAlphaPremultiplied();
		// IMPROVE: extra check to see if entire palette (except transparent index) is gray

		// Convert input image to short
		final byte[] inPixels  = ((DataBufferByte) src.getRaster().getDataBuffer()).getData();
		short[]      srcPixels = new short[inPixels.length];

		processPreConvert(inPixels, srcPixels);

		int           dstArea = dstWidth * dstHeight * numBands;
		final short[] dstPixels;

		if (!doH && !doV) {
			dstPixels = srcPixels;
			t1 = System.nanoTime();
			t2 = t1;
			t3 = t1;
		} else {
			dstPixels = new short[dstArea];
			if (!doV) {
				// Pre-calculate sub-sampling
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);

				t1 = System.nanoTime();
				processHorizontal(srcPixels, dstPixels, srcHeight);
				t2 = System.nanoTime();
				t3 = t2;
			} else if (!doH) {
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * srcWidth);

				t1 = System.nanoTime();
				t2 = t1;
				processVertical(srcPixels, dstPixels, srcWidth);
				t3 = System.nanoTime();
			} else if (horizontalFirst) {
				// Pre-calculate sub-sampling
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * dstWidth);

				int           workArea   = dstWidth * srcHeight * numBands;
				final short[] workPixels = new short[workArea];
// System.out.println(srcPixels.length / 10000 / numBands + "\t"
// + workArea / 10000 / numBands + "\t" + dstArea / 10000 / numBands
// + "\t(" + (srcWidth * dstHeight * numBands) / 10000 / numBands
// + ")");

				t1 = System.nanoTime();
				processHorizontal(srcPixels, workPixels, srcHeight);
				t2 = System.nanoTime();
				processVertical(workPixels, dstPixels, dstWidth);
				t3 = System.nanoTime();
			} else {
				// Pre-calculate sub-sampling
				verticalSamplingData = createSubSampling(
						filter, srcHeight, dstHeight, scaleHeight, offsetY, numBands * srcWidth);
				horizontalSamplingData = createSubSampling(
						filter, srcWidth, dstWidth, scaleWidth, offsetX, numBands);

				int           workArea   = srcWidth * dstHeight * numBands;
				final short[] workPixels = new short[workArea];
// System.out.println(srcPixels.length / 10000 / numBands + "\t"
// + workArea / 10000 / numBands + "\t" + dstArea / 10000 / numBands
// + "\t(" + (dstWidth * srcHeight * numBands) / 10000 / numBands
// + ")");

				t1 = System.nanoTime();
				processVertical(srcPixels, workPixels, srcWidth);
				t2 = System.nanoTime();
				processHorizontal(workPixels, dstPixels, dstHeight);
				t3 = System.nanoTime();
			}
		}

		// Create output image with same properties as the input image after pre-conversion
		BufferedImage out = ImageUtilities.createByteImage(
				dstWidth, dstHeight, numBands,
				srcColorType,
				hasAlpha, srcIsPreAlpha);

// System.out.print("output img: ");
// ImageUtilities.analyzeImage(out);

		// Convert destination image back to bytes (and non-linear color space)
		final byte[] outPixels = ((DataBufferByte) out.getRaster().getDataBuffer()).getData();
		processPostConvert(dstPixels, outPixels);

		t4 = System.nanoTime();
		long effortS = srcWidth * srcHeight;
		long effortH = horizontalFirst ? effortH0 : effortH1;
		long effortV = horizontalFirst ? effortV1 : effortV0;
		long effortD = dstWidth * dstHeight;
		long effortA = effortS + effortD;
// System.out.printf("preconvert %7.2f (%,8.2f mpps)\n", (t1 - t0) / 1e6, effortS * 1e3 / (t1 - t0));
// System.out.printf("horizontal %7.2f (%,8.2f mpps)\n", (t2 - t1) / 1e6, effortH * 1e3 / (t2 - t1));
// System.out.printf("vertical %7.2f (%,8.2f mpps)\n", (t3 - t2) / 1e6, effortV * 1e3 / (t3 - t2));
// System.out.printf("postconvert %7.2f (%,8.2f mpps)\n", (t4 - t3) / 1e6, effortD * 1e3 / (t4 - t3));
// System.out.printf("#resize %7.2f (%,8.2f mpps)\n", (t4 - t0) / 1e6, effortA * 1e3 / (t4 - t0));
		System.out.println((t4 - t0 + 500000) / 1000000);

		// GC this:
		horizontalSamplingData = null;
		verticalSamplingData = null;

		return out;
	}

	private void processPreConvert(final byte[] inPixels, final short[] outPixels)
			throws InterruptedException {
		for (int i = 0; i < numThreads; i++) {
			// Divide before multiply to be sure boundaries are on whole pixels
			final int begin = i * srcHeight * srcWidth / numThreads * numBands;
			final int end   = (i + 1) * srcHeight * srcWidth / numThreads * numBands;
			service.submit(() -> {
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
						preConvertSRGBAlphapremultiply(inPixels, outPixels, begin, end);
					}
				}
				return null;
			});
		}

		waitForWorkers();
	}

	private void processPostConvert(final short[] inPixels, final byte[] outPixels)
			throws InterruptedException {
		for (int i = 0; i < numThreads; i++) {
			// Divide before multiply to be sure boundaries are on whole pixels
			final int begin = i * dstHeight * dstWidth / numThreads * numBands;
			final int end   = (i + 1) * dstHeight * dstWidth / numThreads * numBands;
			service.submit(() -> {
				// Un-linearize or not?
				if (!srcIsSRGB || ignoreSRGB) {
					if (!hasAlpha || srcIsPreAlpha || dontPreAlpha) {
						// convert
						postConvert(inPixels, outPixels, begin, end);
					} else {
						// convert, un-pre-multiply
						postConvertAlpha(inPixels, outPixels, begin, end);
					}
				} else {
					if (!hasAlpha) {
						// convert, un-linearize
						postConvertSRGB(inPixels, outPixels, begin, end);
					} else if (srcIsPreAlpha) {
						// convert, un-linearize colors only
						postConvertSRGBAlpha(inPixels, outPixels, begin, end);
					} else {
						// convert, un-linearize colors only, un-pre-multiply alpha
						postConvertSRGBAlphaUnMultiply(inPixels, outPixels, begin, end);
					}
				}
				return null;
			});
		}

		waitForWorkers();
	}

	private void processHorizontal(final short[] inPixels, final short[] outPixels,
	                               final int height) throws InterruptedException {
		for (int i = 0; i < numThreads; i++) {
			final int begin = i * height / numThreads;
			final int end   = (i + 1) * height / numThreads;
			service.submit(() -> {
				resizeHorizontally(inPixels, outPixels, begin, end);
				return null;
			});
		}

		waitForWorkers();
	}

	private void processVertical(final short[] inPixels, final short[] outPixels,
	                             final int width) throws InterruptedException {
		for (int i = 0; i < numThreads; i++) {
			final int begin = i * dstHeight / numThreads;
			final int end   = (i + 1) * dstHeight / numThreads;
			service.submit(() -> {
				resizeVertically(inPixels, outPixels, begin, end, width);
				return null;
			});
		}

		waitForWorkers();
	}

	protected void waitForWorkers() throws InterruptedException {
		for (int i = 0; i < numThreads; i++) {
			try {
				service.take().get();
			} catch (InterruptedException e1) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
				throw e1;
			} catch (ExecutionException ex) {
				Throwable t = ex.getCause();
				// Check if it is one of the unchecked throwables
				if (t instanceof RuntimeException) {
					throw (RuntimeException) t;
				} else if (t instanceof Error) {
					throw (Error) t;
				} else {
					throw new AssertionError("Unhandled checked exception", t);
				}
			}
		}
	}

	private static void preConvert(final byte[] inPixels, final short[] outPixels,
	                               final int begin, final int end) {
		int q = begin;
		for (int p = begin; p < end; p++) {
			outPixels[q++] = (short) (((inPixels[p] & 0xFF) << 7) - 16384);
		}
	}

	private void preConvertAlpha(final byte[] inPixels, final short[] outPixels,
	                             final int begin, final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					final byte  a          = inPixels[p++];
					final float alphaScale = (a & 0xFF) * 128 / 255f;

					// Alpha channel is already linear
					outPixels[q++] = (short) (((a & 0xFF) << 7) - 16384);
					// pre-multiply by alpha channel
					outPixels[q++] = (short) ((inPixels[p++] & 0xFF) * alphaScale - 16384);
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					final byte  a          = inPixels[p++];
					final float alphaScale = (a & 0xFF) * 128 / 255f;

					// Alpha channel is already linear
					outPixels[q++] = (short) (((a & 0xFF) << 7) - 16384);
					// pre-multiply by alpha channel
					outPixels[q++] = (short) ((inPixels[p++] & 0xFF) * alphaScale - 16384);
					outPixels[q++] = (short) ((inPixels[p++] & 0xFF) * alphaScale - 16384);
					outPixels[q++] = (short) ((inPixels[p++] & 0xFF) * alphaScale - 16384);
				}
		}
	}

	private static void preConvertSRGB(final byte[] inPixels, final short[] outPixels,
	                                   final int begin, final int end) {
		int q = begin;
		for (int p = begin; p < end; ) {
			outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
		}
	}

	private void preConvertSRGBAlpha(final byte[] inPixels, final short[] outPixels,
	                                 final int begin, final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					// Alpha channel is already linear
					outPixels[q++] = (short) (((inPixels[p++] & 0xFF) << 7) - 16384);
					outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					// Alpha channel is already linear
					outPixels[q++] = (short) (((inPixels[p++] & 0xFF) << 7) - 16384);
					outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
					outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
					outPixels[q++] = BYTE_SRGB_TO_SHORT[inPixels[p++] & 0xFF];
				}
		}
	}

	private void preConvertSRGBAlphapremultiply(final byte[] inPixels, final short[] outPixels,
	                                            final int begin, final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					final byte  a     = inPixels[p++];
					final float alpha = (a & 0xFF) / 255f;

					// Alpha channel is already linear
					outPixels[q++] = (short) (((a & 0xFF) << 7) - 16384);
					// pre-multiply by alpha channel
					outPixels[q++] = (short) (BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha - 16384);
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					final byte a     = inPixels[p++];
					final int  alpha = a & 0xFF;

					// Alpha channel is already linear
					outPixels[q++] = (short) ((alpha << 7) - 16384);
					// pre-multiply by alpha channel
					outPixels[q++] = (short) (BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255 - 16384);
					outPixels[q++] = (short) (BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255 - 16384);
					outPixels[q++] = (short) (BYTE_SRGB_TO_SHORT2[inPixels[p++] & 0xFF] * alpha / 255 - 16384);
				}
		}
	}

	private static void postConvert(final short[] inPixels, final byte[] outPixels,
	                                final int begin, final int end) {
		int q = begin;
		for (int p = begin; p < end; ) {
			final short f = inPixels[p++];
			outPixels[q++] = f <= -16257 ? 0 : f >= 16256 ? -1 : (byte) ((f + 16384) >> 7);
		}
	}

	private void postConvertAlpha(final short[] inPixels, final byte[] outPixels, final int begin,
	                              final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					// Alpha of 0 means all color information is lost. Keep black.
					final float alphaInv = a <= -16384 || a >= 16256 ? 1 : 32640f / (a + 16384);

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Alpha channel
					outPixels[q++] = (byte) ((int) ((inPixels[p++] + 16384) * alphaInv) >> 7);
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					// Alpha of 0 means all color information is lost. Keep black.
					final float alphaInv = a <= -16384 || a >= 16256 ? 1 : 32640f / (a + 16384);

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Alpha channel
					outPixels[q++] = (byte) ((int) ((inPixels[p++] + 16384) * alphaInv) >> 7);
					outPixels[q++] = (byte) ((int) ((inPixels[p++] + 16384) * alphaInv) >> 7);
					outPixels[q++] = (byte) ((int) ((inPixels[p++] + 16384) * alphaInv) >> 7);
				}
		}
	}

	private static void postConvertSRGB(final short[] inPixels, final byte[] outPixels,
	                                    final int begin, final int end) {
		int q = begin;
		for (int p = begin; p < end; ) {
			outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
		}
	}

	private void postConvertSRGBAlpha(final short[] inPixels, final byte[] outPixels,
	                                  final int begin, final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Don't sRGB the alpha channel
					outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Don't sRGB the alpha channel
					outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
					outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
					outPixels[q++] = SHORT_TO_BYTE_SRGB[inPixels[p++] & 0xFFFF];
				}
		}
	}

	private void postConvertSRGBAlphaUnMultiply(final short[] inPixels, final byte[] outPixels,
	                                            final int begin, final int end) {
		int q = begin;
		switch (numBands) {
			case 2:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					// Alpha of 0 means all color information is lost. Keep black.
					final long alphaInv = a <= -16384 || a >= 16256 ? 65536L : 65536L * 32640 / (a + 16384);

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Don't sRGB the alpha channel
					outPixels[q++] = SHORT2_TO_BYTE_SRGB[(short) ((inPixels[p++] + 16384) * alphaInv >> 16)
					                                     & 0xFFFF];
				}
				break;
			case 4:
				for (int p = begin; p < end; ) {
					final short a = inPixels[p++];

					// Alpha of 0 means all color information is lost. Keep black.
					final long alphaInv = a <= -16384 || a >= 16256 ? 65536L : 65536L * 32640 / (a + 16384);

					outPixels[q++] = (byte) ((a + 16384) >> 7); // Don't sRGB the alpha channel
					outPixels[q++] = SHORT2_TO_BYTE_SRGB[(short) ((inPixels[p++] + 16384) * alphaInv >> 16)
					                                     & 0xFFFF];
					outPixels[q++] = SHORT2_TO_BYTE_SRGB[(short) ((inPixels[p++] + 16384) * alphaInv >> 16)
					                                     & 0xFFFF];
					outPixels[q++] = SHORT2_TO_BYTE_SRGB[(short) ((inPixels[p++] + 16384) * alphaInv >> 16)
					                                     & 0xFFFF];
				}
				break;
		}
	}

	private void resizeHorizontally(final short[] inPixels, final short[] outPixels,
	                                final int begin, final int end) {
		final int     numSamples = horizontalSamplingData.numSamples;
		final int[]   indices    = horizontalSamplingData.indices;
		final float[] weights    = horizontalSamplingData.weights;

		float sample0;
		float sample1;
		float sample2;
		float sample3;

		switch (numBands) {
			case 1:
				for (int y = begin; y < end; y++) {
					final int offset = srcWidth * y;
					for (int x = 0; x < dstWidth; x++) {
						sample0 = 0;
						int index = x * numSamples;
						for (int i = numSamples; i > 0; i--) {
							sample0 += inPixels[offset + indices[index]] * weights[index];
							index++;
						}
						outPixels[(x + y * dstWidth)] = (short) sample0;
					}
				}
				break;
			case 2:
				for (int y = begin; y < end; y++) {
					final int offset = srcWidth * y * 2;
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
						outPixels[u] = (short) sample0;
						outPixels[u + 1] = (short) sample1;
					}
				}
				break;
			case 3:
				for (int y = begin; y < end; y++) {
					final int offset = srcWidth * y * 3;
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
						outPixels[u] = (short) sample0;
						outPixels[u + 1] = (short) sample1;
						outPixels[u + 2] = (short) sample2;
					}
				}
				break;
			case 4:
				for (int y = begin; y < end; y++) {
					final int offset = srcWidth * y * 4;
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
						outPixels[u] = (short) sample0;
						outPixels[u + 1] = (short) sample1;
						outPixels[u + 2] = (short) sample2;
						outPixels[u + 3] = (short) sample3;
					}
				}
		}
	}

	private void resizeVertically(final short[] inPixels, final short[] outPixels,
	                              final int begin, final int end, final int width) {
		final int       numSamples = verticalSamplingData.numSamples;
		final int[][]   indices2D  = verticalSamplingData.indices2D;
		final float[][] weights2D  = verticalSamplingData.weights2D;

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
						outPixels[u++] = (short) sample0;
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
						outPixels[u++] = (short) sample0;
						outPixels[u++] = (short) sample1;
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
						outPixels[u++] = (short) sample0;
						outPixels[u++] = (short) sample1;
						outPixels[u++] = (short) sample2;
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
						outPixels[u++] = (short) sample0;
						outPixels[u++] = (short) sample1;
						outPixels[u++] = (short) sample2;
						outPixels[u++] = (short) sample3;
					}
				}
		}
	}
}
