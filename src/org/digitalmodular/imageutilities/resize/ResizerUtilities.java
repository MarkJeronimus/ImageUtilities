package org.digitalmodular.imageutilities.resize;

import org.digitalmodular.imageutilities.SizeInt;
import org.digitalmodular.imageutilities.resize.filter.CubicResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.LinearResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;

/**
 * @author Mark Jeronimus
 */
// Created 2017-07-18 Extracted from ImageUtilities
public enum ResizerUtilities {
	;

	public enum ScalingTarget {
		STRETCH,
		INSIDE,
		OUTSIDE,
		WIDTH_TOUCH,
		HEIGHT_TOUCH,
		SAME_DIAGONAL,
		SAME_CIRCUMFERENCE,
		SAME_AREA
	}

	public enum ScalingCondition {
		ALWAYS,
		NEVER,
		ONLY_IF_LARGER,
		ONLY_IF_SMALLER,
	}

	public static SizeInt getScalingSize(SizeInt imageSize, SizeInt targetSize) {
		return getScalingSize(imageSize, targetSize, ScalingTarget.INSIDE, ScalingCondition.ALWAYS);
	}

	public static SizeInt getScalingSize(SizeInt imageSize, SizeInt targetSize, ScalingTarget scalingTarget) {
		return getScalingSize(imageSize, targetSize, scalingTarget, ScalingCondition.ALWAYS);
	}

	public static SizeInt getScalingSize(SizeInt imageSize,
	                                     SizeInt targetSize,
	                                     ScalingTarget scalingTarget,
	                                     ScalingCondition scalingCondition) {
		int wCross = imageSize.getWidth() * targetSize.getHeight();
		int hCross = imageSize.getHeight() * targetSize.getWidth();

		int width;
		int height;
		switch (scalingTarget) {
			case STRETCH:
				width = imageSize.getWidth();
				height = imageSize.getHeight();
				break;
			case INSIDE:
				if (wCross > hCross) {
					width = targetSize.getWidth();
					height = intDivRound(hCross, imageSize.getWidth());
				} else {
					width = intDivRound(wCross, imageSize.getHeight());
					height = targetSize.getHeight();
				}
				break;
			case OUTSIDE:
				if (wCross < hCross) {
					width = targetSize.getWidth();
					height = intDivRound(hCross, imageSize.getWidth());
				} else {
					width = intDivRound(wCross, imageSize.getHeight());
					height = targetSize.getHeight();
				}
				break;
			case WIDTH_TOUCH:
				width = targetSize.getWidth();
				height = intDivRound(hCross, imageSize.getWidth());
				break;
			case HEIGHT_TOUCH:
				width = intDivRound(wCross, imageSize.getHeight());
				height = targetSize.getHeight();
				break;
			case SAME_DIAGONAL:
				double vDia = Math.hypot(targetSize.getWidth(), targetSize.getHeight());
				double iDia = Math.hypot(imageSize.getWidth(), imageSize.getHeight());
				width = (int)Math.rint(imageSize.getWidth() * vDia / iDia);
				height = (int)Math.rint(imageSize.getHeight() * vDia / iDia);
				break;
			case SAME_CIRCUMFERENCE:
				int vSum = targetSize.getWidth() + targetSize.getHeight();
				int iSum = imageSize.getWidth() + imageSize.getHeight();
				width = intDivRound(imageSize.getWidth() * vSum, iSum);
				height = intDivRound(imageSize.getHeight() * vSum, iSum);
				break;
			case SAME_AREA:
				int vArea = targetSize.getWidth() * targetSize.getHeight();
				int iArea = imageSize.getWidth() * imageSize.getHeight();
				width = intDivRound(imageSize.getWidth() * vArea, iArea);
				height = intDivRound(imageSize.getHeight() * vArea, iArea);
				break;
			default:
				throw new IllegalArgumentException("Unknown scalingHint: " + scalingTarget);
		}

		switch (scalingCondition) {
			case ALWAYS:
				break;
			case NEVER:
				width = imageSize.getWidth();
				height = imageSize.getHeight();
				break;
			case ONLY_IF_LARGER:
				if (width >= imageSize.getWidth() && height >= imageSize.getHeight()) {
					width = imageSize.getWidth();
					height = imageSize.getHeight();
				}
				break;
			case ONLY_IF_SMALLER:
				if (width <= imageSize.getWidth() && height <= imageSize.getHeight()) {
					width = imageSize.getWidth();
					height = imageSize.getHeight();
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown scalingCondition: " + scalingCondition);
		}

		return new SizeInt(width, height);
	}

	/**
	 * @return Same as {@code (int)Math.round((double)numerator / denominator)} but without intermediate floating point
	 * arithmetic.
	 */
	private static int intDivRound(int numerator, int denominator) {
		return denominator == 0 ? 0 : (numerator + denominator / 2) / denominator;
	}

	/**
	 * Selects the fastest {@link ResamplingCurve} (i.e. with the smallest radius) that can resize an image while
	 * keeping the best quality. If the image is being stretched, only the axis with the greatest <em>factor</em>
	 * (deviation from 1:1) is taken into account.
	 * <ul>
	 * <li>{@link Lanczos3ResamplingCurve} (radius = 3) for strong scaling factors (factor 4 and over),</li>
	 * <li>{@link CubicResamplingCurve} (radius = 2) for medium scale factors (factor 2 up to 4),</li>
	 * <li>{@link LinearResamplingCurve} (radius = 1) for small scale factors (up to factor 2),</li>
	 * </ul>
	 */
	public static ResamplingCurve bestResamplingCurve(SizeInt imageSize, SizeInt newSize) {
		double factor = Math.max(Math.max(newSize.getWidth() / (double)imageSize.getWidth(),
		                                  newSize.getHeight() / (double)imageSize.getHeight()),
		                         Math.max(imageSize.getWidth() / (double)newSize.getWidth(),
		                                  imageSize.getHeight() / (double)newSize.getHeight()));

		if (factor >= 4)
			return Lanczos3ResamplingCurve.INSTANCE;
		else if (factor >= 2)
			return CubicResamplingCurve.INSTANCE;
		else
			return LinearResamplingCurve.INSTANCE;
	}
}
