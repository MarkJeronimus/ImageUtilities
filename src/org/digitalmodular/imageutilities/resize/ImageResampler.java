package org.digitalmodular.imageutilities.resize;

import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;

/**
 * @author Mark Jeronimus
 */
// Created 2016-05-09
public interface ImageResampler extends ImageResizer {
	int getNumThreads();
	/**
	 * Set the maximum number of threads to utilize. Default is {@code 0}, which automatically uses the same number of
	 * threads as {@code Runtime.getRuntime().availableProcessors()};
	 *
	 * @param numThreads
	 */
	void setNumThreads(int numThreads);

	ResamplingCurve getFilter();
	/**
	 * Set the interpolation filter to use. Default is {@link Lanczos3ResamplingCurve#INSTANCE}.
	 *
	 * @param filter
	 */
	void setFilter(ResamplingCurve filter);
}
