package org.digitalmodular.imageutilities.resize;

import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;

/**
 * @author Mark Jeronimus
 */
// Created 2017-07-18 Extracted from AbstractImageResampler
public enum SamplingDataCalculator {
	;

	/**
	 * SamplingData describes how a single row or column of an image can be resized. It specifies for each
	 * output sample which input samples contribute (using relative indices), and how much (using normalized
	 * weights). Each output sample always depends on a fixed number of input samples, specified by
	 * {@link #numSamples}. Although in practice the input sample indices are always contiguous, and the middle
	 * one or one of the two middle ones equal the output sample index, these are not enforced.
	 */
	@SuppressWarnings("ReturnOfCollectionOrArrayField")
	public static final class SamplingData {
		private final int       numSamples;
		private final int[]     indicesX;
		private final int[][]   indicesY;
		private final float[]   weightsX;
		private final float[][] weightsY;

		private SamplingData(int numSamples, int[] indicesX, int[][] indicesY, float[] weightsX, float[][] weightsY) {
			this.numSamples = numSamples;
			this.indicesX = indicesX;
			this.indicesY = indicesY;
			this.weightsX = weightsX;
			this.weightsY = weightsY;
		}

		/**
		 * The number of input samples per output sample.
		 */
		public int getNumSamples() { return numSamples; }

		/**
		 * The input sample indices. A linear array of {@link #numSamples} indices for each output sample.
		 */
		public int[] getIndicesX() { return indicesX; }

		/**
		 * The input sample indices. A linear array of {@link #numSamples} indices for each output sample.
		 */
		public int[][] getIndicesY() { return indicesY; }

		/**
		 * The input sample weights. A linear array of {@link #numSamples} weights for each output sample.
		 */
		public float[] getWeightsX() { return weightsX; }

		/**
		 * The input sample weights. A linear array of {@link #numSamples} weights for each output sample.
		 */
		public float[][] getWeightsY() { return weightsY; }
	}

	public static SamplingData createSubSampling(ResamplingCurve filter,
	                                             int srcSize, int dstSize,
	                                             double scale, double offset,
	                                             int pixelStride) {
		int numSamples = calculateNumSamples(filter, scale);

		// 'X' arrays have an optimized structure for the horizontal resamplers, Y arrays for the vertical resamplers.

		int[]     indicesX = new int[dstSize * numSamples];
		int[][]   indicesY = new int[dstSize][numSamples];
		float[]   weightsX = new float[dstSize * numSamples];
		float[][] weightsY = new float[dstSize][numSamples];

		// Translation between source and destination image CENTERS, in source scalespace
		// TODO elaborate this formula
		double centerOffset = ((srcSize - 1) - (dstSize - 1 + offset * 2) / scale) / 2;
//		double centerOffset = ((srcSize - 1) - (dstSize / scale - 1 / scale + offset * 2 / scale)) / 2;
//		double centerOffset = ((srcSize - 1) - dstSize / scale + 1 / scale - offset * 2 / scale) / 2;
//		double centerOffset = (srcSize - 1) - dstSize / scale / 2 + 1 / 2 / scale - offset * 2 / scale / 2;
//		double centerOffset = (srcSize - 1) / 2 - dstSize / 2 / scale + 1 / 2 / scale - offset / scale;
//		double centerOffset = (srcSize - 1) / 2 - (dstSize / 2 - 1 / 2 + offset) / scale;
//		double centerOffset = (srcSize - 1) / 2 - ((dstSize - 1) / 2 + offset) / scale;

		double samplingRadius = getSamplingRadius(filter, scale);

		int k = 0;
		for (int i = 0; i < dstSize; i++) {
			int    subIndex = k;
			double center   = i / scale + centerOffset;

			int left  = (int)Math.ceil(center - samplingRadius);
			int right = left + numSamples;

			for (int j = left; j < right; j++) {
				float weight;
				if (scale < 1)
					weight = (float)filter.apply((j - center) * scale);
				else
					weight = (float)filter.apply(j - center);

				int n = j < 0 ? 0 : j >= srcSize ? srcSize - 1 : j;

				indicesX[k] = n * pixelStride;
				indicesY[i][j - left] = n * pixelStride;
				weightsX[k] = weight;
				weightsY[i][j - left] = weight;
				k++;
			}

			// Normalize weights
			double sum = 0;
			for (int j = 0; j < numSamples; j++)
				sum += weightsY[i][j];

			if (sum != 0) {
				for (int j = 0; j < numSamples; j++) {
					weightsX[subIndex + j] /= sum;
					weightsY[i][j] /= sum;
				}
			}
		}
		return new SamplingData(numSamples, indicesX, indicesY, weightsX, weightsY);
	}

	/**
	 * Calculates the minimum number of samples required to cover the resampling curve.
	 *
	 * @param scale the scaling factor, {@code < 1} means shrinking.
	 */
	public static int calculateNumSamples(ResamplingCurve filter, double scale) {
		double samplingRadius = getSamplingRadius(filter, scale);

		return (int)Math.ceil(samplingRadius * 2);
	}

	/**
	 * Calculates the sampling radius of the resampling curve, in source scalespace.
	 *
	 * @param scale the scaling factor, {@code < 1} means shrinking.
	 */
	private static double getSamplingRadius(ResamplingCurve filter, double scale) {
		double curveRadius = filter.getRadius();
		return scale < 1 ? curveRadius / scale : curveRadius;
	}
}
