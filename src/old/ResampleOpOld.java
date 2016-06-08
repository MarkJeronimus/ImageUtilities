/*
 * Copyright 2013, Morten Nobel-Joergensen
 *
 * License: The BSD 3-Clause License
 * http://opensource.org/licenses/BSD-3-Clause
 */
package old;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;

/**
 * Based on work from Java Image Util ( http://schmidt.devlib.org/jiu/ )
 *
 * Note that the filter method is not thread safe
 *
 * @author Morten Nobel-Joergensen
 * @author Heinz Doerr
 */
public class ResampleOpOld extends AdvancedResizeOpOld {
	private final int MAX_CHANNEL_VALUE = 255;

	private int nrChannels;
	private int srcWidth;
	private int srcHeight;
	private int dstWidth;
	private int dstHeight;

	static class SubSamplingData {
		final int[] arrN; // individual - per row or per column - nr of contributions
		final int[] arrPixel;  // 2Dim: [wid or hei][contrib]
		final float[] arrWeight; // 2Dim: [wid or hei][contrib]
		final int numContributors; // the primary index length for the 2Dim arrays : arrPixel and arrWeight

		SubSamplingData(int[] arrN, int[] arrPixel, float[] arrWeight, int numContributors) {
			this.arrN = arrN;
			this.arrPixel = arrPixel;
			this.arrWeight = arrWeight;
			this.numContributors = numContributors;
		}

		public int getNumContributors() {
			return numContributors;
		}

		public int[] getArrN() {
			return arrN;
		}

		public int[] getArrPixel() {
			return arrPixel;
		}

		public float[] getArrWeight() {
			return arrWeight;
		}
	}

	private SubSamplingData horizontalSubsamplingData;
	private SubSamplingData verticalSubsamplingData;

	private int processedItems;
	private float totalItems;

	int numberOfThreads = Runtime.getRuntime().availableProcessors();

	private final AtomicInteger multipleInvocationLock = new AtomicInteger();

	private ResamplingCurve filter = Lanczos3ResamplingCurve.INSTANCE;

	public ResamplingCurve getFilter() {
		return filter;
	}

	public void setFilter(ResamplingCurve filter) {
		this.filter = filter;
	}

	public Dimension getDstSize() {
		return new Dimension(dstWidth, dstHeight);
	}

	public void setDstSize(Dimension dstSize) {
		dstWidth = dstSize.width;
		dstHeight = dstSize.height;
		if (dstWidth < 1 || dstHeight < 1) {
			throw new IllegalArgumentException("dstSize has no area:" + dstSize);
		}
	}

	public int getNumberOfThreads() {
		return numberOfThreads;
	}

	public void setNumberOfThreads(int numberOfThreads) {
		this.numberOfThreads = numberOfThreads;
	}

	@Override
	public BufferedImage filter(BufferedImage srcImg, BufferedImage dest) {
		if (dstWidth < 3 || dstHeight < 3) {
			throw new RuntimeException("Error doing rescale. Target size was " + dstWidth + "x" + dstHeight
			                           + " but must be at least 3x3.");
		}

		assert multipleInvocationLock.incrementAndGet() == 1 : "Multiple concurrent invocations detected";

		if (srcImg.getType() == BufferedImage.TYPE_BYTE_BINARY ||
		    srcImg.getType() == BufferedImage.TYPE_BYTE_INDEXED ||
		    srcImg.getType() == BufferedImage.TYPE_CUSTOM) {
			srcImg = ImageUtilsOld.convert(srcImg,
			        srcImg.getColorModel().hasAlpha()? BufferedImage.TYPE_4BYTE_ABGR
			                : BufferedImage.TYPE_3BYTE_BGR);
		}

		nrChannels = srcImg.getRaster().getNumBands();
		assert nrChannels > 0;
		srcWidth = srcImg.getWidth();
		srcHeight = srcImg.getHeight();

		byte[][] workPixels = new byte[srcHeight][dstWidth * nrChannels];

		processedItems = 0;
		totalItems = srcHeight + dstWidth;

		// Pre-calculate sub-sampling
		horizontalSubsamplingData = createSubSampling(filter, srcWidth, dstWidth);
		verticalSubsamplingData = createSubSampling(filter, srcHeight, dstHeight);

		final BufferedImage scrImgCopy = srcImg;
		final byte[][] workPixelsCopy = workPixels;
		Thread[] threads = new Thread[numberOfThreads - 1];
		for (int i = 1; i < numberOfThreads; i++) {
			final int finalI = i;
			threads[i - 1] = new Thread(
			        () -> horizontallyFromSrcToWork(scrImgCopy, workPixelsCopy, finalI, numberOfThreads));
			threads[i - 1].start();
		}
		horizontallyFromSrcToWork(scrImgCopy, workPixelsCopy, 0, numberOfThreads);
		waitForAllThreads(threads);

		byte[] outPixels = new byte[dstWidth * dstHeight * nrChannels];
		// --------------------------------------------------
		// Apply filter to sample vertically from Work to Dst
		// --------------------------------------------------
		final byte[] outPixelsCopy = outPixels;
		for (int i = 1; i < numberOfThreads; i++) {
			final int finalI = i;
			threads[i - 1] = new Thread(
			        () -> verticalFromWorkToDst(workPixelsCopy, outPixelsCopy, finalI, numberOfThreads));
			threads[i - 1].start();
		}
		verticalFromWorkToDst(workPixelsCopy, outPixelsCopy, 0, numberOfThreads);
		waitForAllThreads(threads);

		// noinspection UnusedAssignment
		workPixels = null; // free memory
		BufferedImage out = new BufferedImage(dstWidth, dstHeight, getResultBufferedImageType(srcImg));

		ImageUtilsOld.setBGRPixels(outPixels, out, 0, 0, dstWidth, dstHeight);

		assert multipleInvocationLock.decrementAndGet() == 0 : "Multiple concurrent invocations detected";

		return out;
	}

	private static void waitForAllThreads(Thread[] threads) {
		try {
			for (Thread t : threads) {
				t.join(Long.MAX_VALUE);
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(ex);
		}
	}

	static SubSamplingData createSubSampling(ResamplingCurve filter, int srcSize, int dstSize) {
		float scale = (float)dstSize / (float)srcSize;
		int[] arrN = new int[dstSize];
		int numContributors;
		float[] arrWeight;
		int[] arrPixel;

		final float fwidth = (float)filter.getRadius();

		float centerOffset = 0.5f / scale;

		if (scale < 1.0f) {
			final float width = fwidth / scale;
			numContributors = (int)(width * 2.0f + 2); // Heinz: added 1 to be save with the ceilling
			arrWeight = new float[dstSize * numContributors];
			arrPixel = new int[dstSize * numContributors];

			final float fNormFac = (float)(1f / (Math.ceil(width) / fwidth));
			//
			for (int i = 0; i < dstSize; i++) {
				final int subindex = i * numContributors;
				float center = i / scale + centerOffset;
				int left = (int)Math.floor(center - width);
				int right = (int)Math.ceil(center + width);
				for (int j = left; j <= right; j++) {
					float weight;
					weight = (float)filter.apply((center - j) * fNormFac);

					if (weight == 0.0f) {
						continue;
					}
					int n;
					if (j < 0) {
						n = -j;
					} else if (j >= srcSize) {
						n = srcSize - j + srcSize - 1;
					} else {
						n = j;
					}
					int k = arrN[i];
					// assert k == j-left:String.format("%s = %s %s", k,j,left);
					arrN[i]++;
					if (n < 0 || n >= srcSize) {
						weight = 0.0f;// Flag that cell should not be used
					}
					arrPixel[subindex + k] = n;
					arrWeight[subindex + k] = weight;
				}
				// normalize the filter's weight's so the sum equals to 1.0, very important for avoiding box
				// type of artifacts
				final int max = arrN[i];
				float tot = 0;
				for (int k = 0; k < max; k++) {
					tot += arrWeight[subindex + k];
				}
				if (tot != 0f) { // 0 should never happen except bug in filter
					for (int k = 0; k < max; k++) {
						arrWeight[subindex + k] /= tot;
					}
				}
			}
		} else
		// super-sampling
		// Scales from smaller to bigger height
		{
			numContributors = (int)(fwidth * 2.0f + 1);
			arrWeight = new float[dstSize * numContributors];
			arrPixel = new int[dstSize * numContributors];
			//
			for (int i = 0; i < dstSize; i++) {
				final int subindex = i * numContributors;
				float center = i / scale + centerOffset;
				int left = (int)Math.floor(center - fwidth);
				int right = (int)Math.ceil(center + fwidth);
				for (int j = left; j <= right; j++) {
					float weight = (float)filter.apply(center - j);
					if (weight == 0.0f) {
						continue;
					}
					int n;
					if (j < 0) {
						n = -j;
					} else if (j >= srcSize) {
						n = srcSize - j + srcSize - 1;
					} else {
						n = j;
					}
					int k = arrN[i];
					arrN[i]++;
					if (n < 0 || n >= srcSize) {
						weight = 0.0f;// Flag that cell should not be used
					}
					arrPixel[subindex + k] = n;
					arrWeight[subindex + k] = weight;
				}
				// normalize the filter's weight's so the sum equals to 1.0, very important for avoiding box
				// type of artifacts
				final int max = arrN[i];
				float tot = 0;
				for (int k = 0; k < max; k++) {
					tot += arrWeight[subindex + k];
				}
				assert tot != 0 : "should never happen except bug in filter";
				if (tot != 0f) {
					for (int k = 0; k < max; k++) {
						arrWeight[subindex + k] /= tot;
					}
				}
			}
		}
		return new SubSamplingData(arrN, arrPixel, arrWeight, numContributors);
	}

	void verticalFromWorkToDst(byte[][] workPixels, byte[] outPixels, int start, int delta) {
		if (nrChannels == 1) {
			verticalFromWorkToDstGray(workPixels, outPixels, start, numberOfThreads);
			return;
		}
		boolean useChannel3 = nrChannels > 3;
		for (int x = start; x < dstWidth; x += delta) {
			final int xLocation = x * nrChannels;
			for (int y = dstHeight - 1; y >= 0; y--) {
				final int yTimesNumContributors = y * verticalSubsamplingData.numContributors;
				final int max = verticalSubsamplingData.arrN[y];
				final int sampleLocation = (y * dstWidth + x) * nrChannels;

				float sample0 = 0.0f;
				float sample1 = 0.0f;
				float sample2 = 0.0f;
				float sample3 = 0.0f;
				int index = yTimesNumContributors;
				for (int j = max - 1; j >= 0; j--) {
					int valueLocation = verticalSubsamplingData.arrPixel[index];
					float arrWeight = verticalSubsamplingData.arrWeight[index];
					sample0 += (workPixels[valueLocation][xLocation] & 0xff) * arrWeight;
					sample1 += (workPixels[valueLocation][xLocation + 1] & 0xff) * arrWeight;
					sample2 += (workPixels[valueLocation][xLocation + 2] & 0xff) * arrWeight;
					if (useChannel3) {
						sample3 += (workPixels[valueLocation][xLocation + 3] & 0xff) * arrWeight;
					}

					index++;
				}

				outPixels[sampleLocation] = toByte(sample0);
				outPixels[sampleLocation + 1] = toByte(sample1);
				outPixels[sampleLocation + 2] = toByte(sample2);
				if (useChannel3) {
					outPixels[sampleLocation + 3] = toByte(sample3);
				}

			}
			processedItems++;
			if (start == 0) { // only update progress listener from main thread
				setProgress();
			}
		}
	}

	private void verticalFromWorkToDstGray(byte[][] workPixels, byte[] outPixels, int start, int delta) {
		for (int x = start; x < dstWidth; x += delta) {
			final int xLocation = x;
			for (int y = dstHeight - 1; y >= 0; y--) {
				final int yTimesNumContributors = y * verticalSubsamplingData.numContributors;
				final int max = verticalSubsamplingData.arrN[y];
				final int sampleLocation = (y * dstWidth + x);

				float sample0 = 0.0f;
				int index = yTimesNumContributors;
				for (int j = max - 1; j >= 0; j--) {
					int valueLocation = verticalSubsamplingData.arrPixel[index];
					float arrWeight = verticalSubsamplingData.arrWeight[index];
					sample0 += (workPixels[valueLocation][xLocation] & 0xff) * arrWeight;

					index++;
				}

				outPixels[sampleLocation] = toByte(sample0);
			}
			processedItems++;
			if (start == 0) { // only update progress listener from main thread
				setProgress();
			}
		}
	}

	/**
	 * Apply filter to sample horizontally from Src to Work
	 *
	 * @param srcImg
	 * @param workPixels
	 * @param start
	 * @param delta
	 */
	void horizontallyFromSrcToWork(BufferedImage srcImg, byte[][] workPixels, int start, int delta) {
		if (nrChannels == 1) {
			horizontallyFromSrcToWorkGray(srcImg, workPixels, start, delta);
			return;
		}
		// Used if we work on int based bitmaps, later used to keep channel values
		final int[] tempPixels = new int[srcWidth];
		// create reusable row to minimize memory overhead
		final byte[] srcPixels = new byte[srcWidth * nrChannels];
		final boolean useChannel3 = nrChannels > 3;

		for (int k = start; k < srcHeight; k = k + delta) {
			ImageUtilsOld.getPixelsBGR(srcImg, k, srcWidth, srcPixels, tempPixels);

			for (int i = dstWidth - 1; i >= 0; i--) {
				int sampleLocation = i * nrChannels;
				final int max = horizontalSubsamplingData.arrN[i];

				float sample0 = 0.0f;
				float sample1 = 0.0f;
				float sample2 = 0.0f;
				float sample3 = 0.0f;
				int index = i * horizontalSubsamplingData.numContributors;
				for (int j = max - 1; j >= 0; j--) {
					float arrWeight = horizontalSubsamplingData.arrWeight[index];
					int pixelIndex = horizontalSubsamplingData.arrPixel[index] * nrChannels;

					sample0 += (srcPixels[pixelIndex] & 0xff) * arrWeight;
					sample1 += (srcPixels[pixelIndex + 1] & 0xff) * arrWeight;
					sample2 += (srcPixels[pixelIndex + 2] & 0xff) * arrWeight;
					if (useChannel3) {
						sample3 += (srcPixels[pixelIndex + 3] & 0xff) * arrWeight;
					}
					index++;
				}

				workPixels[k][sampleLocation] = toByte(sample0);
				workPixels[k][sampleLocation + 1] = toByte(sample1);
				workPixels[k][sampleLocation + 2] = toByte(sample2);
				if (useChannel3) {
					workPixels[k][sampleLocation + 3] = toByte(sample3);
				}
			}
			processedItems++;
			if (start == 0) { // only update progress listener from main thread
				setProgress();
			}
		}
	}

	/**
	 * Apply filter to sample horizontally from Src to Work
	 *
	 * @param srcImg
	 * @param workPixels
	 * @param start
	 * @param delta
	 */
	private void horizontallyFromSrcToWorkGray(BufferedImage srcImg, byte[][] workPixels, int start,
	                                           int delta) {
		// Used if we work on int based bitmaps, later used to keep channel values
		final int[] tempPixels = new int[srcWidth];
		// create reusable row to minimize memory overhead
		final byte[] srcPixels = new byte[srcWidth];

		for (int k = start; k < srcHeight; k = k + delta) {
			ImageUtilsOld.getPixelsBGR(srcImg, k, srcWidth, srcPixels, tempPixels);

			for (int i = dstWidth - 1; i >= 0; i--) {
				int sampleLocation = i;
				final int max = horizontalSubsamplingData.arrN[i];

				float sample0 = 0.0f;
				int index = i * horizontalSubsamplingData.numContributors;
				for (int j = max - 1; j >= 0; j--) {
					float arrWeight = horizontalSubsamplingData.arrWeight[index];
					int pixelIndex = horizontalSubsamplingData.arrPixel[index];

					sample0 += (srcPixels[pixelIndex] & 0xff) * arrWeight;
					index++;
				}

				workPixels[k][sampleLocation] = toByte(sample0);
			}
			processedItems++;
			if (start == 0) { // only update progress listener from main thread
				setProgress();
			}
		}
	}

	private byte toByte(float f) {
		if (f < 0) {
			return 0;
		}
		if (f > MAX_CHANNEL_VALUE) {
			return (byte)MAX_CHANNEL_VALUE;
		}
		return (byte)(f + 0.5f); // add 0.5 same as Math.round
	}

	private void setProgress() {
		fireProgressChanged(processedItems / totalItems);
	}

	protected int getResultBufferedImageType(BufferedImage srcImg) {
		return nrChannels == 3? BufferedImage.TYPE_3BYTE_BGR
		        : (nrChannels == 4? BufferedImage.TYPE_4BYTE_ABGR
		                : (srcImg.getSampleModel().getDataType() == DataBuffer.TYPE_USHORT
		                        ? BufferedImage.TYPE_USHORT_GRAY : BufferedImage.TYPE_BYTE_GRAY));
	}
}
