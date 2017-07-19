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
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.Objects.requireNonNull;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.internal.DependentWorkerQueue;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.ResamplingCurve;

/**
 * Superclass for all algorithms that can resize an image using high-quality resampling filters and parallel processing.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-22
// Changed 2017-07-18 Extracted some code to SamplingDataCalculator
abstract class AbstractImageResampler extends AbstractImageResizer<BufferedImage> implements ImageResampler {
	protected static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

	private final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
	private final ThreadPoolExecutor      executor  = new ThreadPoolExecutor(
			AVAILABLE_PROCESSORS, AVAILABLE_PROCESSORS, 60L, TimeUnit.MILLISECONDS, workQueue);
	private final CompletionService<Void> service   = new ExecutorCompletionService<>(executor);

	protected ResamplingCurve filter     = Lanczos3ResamplingCurve.INSTANCE;
	protected int             numThreads = 0;

	protected AbstractImageResampler() {
		executor.allowCoreThreadTimeOut(true);
	}

	@Override
	public ResamplingCurve getFilter() { return filter; }

	@Override
	public void setFilter(ResamplingCurve filter) { this.filter = requireNonNull(filter); }

	@Override
	public int getNumThreads() { return numThreads; }

	@Override
	public void setNumThreads(int numThreads) {
		if (numThreads < 0)
			throw new IllegalArgumentException("numThreads can't be negative: " + numThreads);
		this.numThreads = numThreads;
	}

	@Override
	public boolean imageIsCompatible(Image image) {
		if (!(image instanceof BufferedImage))
			return false;

		BufferedImage bufferedImage = (BufferedImage)image;

		// Known compatible types
		int type = bufferedImage.getType();
		if (type == BufferedImage.TYPE_3BYTE_BGR
		    || type == BufferedImage.TYPE_4BYTE_ABGR
		    || type == BufferedImage.TYPE_4BYTE_ABGR_PRE
		    || type == BufferedImage.TYPE_BYTE_GRAY) {
			return true;
			// IMPROVE: compatible indexed image possible?
			// } else if (type == BufferedImage.TYPE_BYTE_BINARY
			// || type == BufferedImage.TYPE_BYTE_INDEXED) {
			// return false;
		}

		// Check if the image is gray+alpha, which surprisingly is a standard format without a corresponding TYPE_
		int dataType = bufferedImage.getRaster().getDataBuffer().getDataType();

		return type == BufferedImage.TYPE_CUSTOM
		       && dataType == DataBuffer.TYPE_BYTE
		       && hasAlpha;
	}

	@Override
	public synchronized BufferedImage makeImageCompatible(Image image) {
		if (imageIsCompatible(image))
			return (BufferedImage)image;

		if (image instanceof BufferedImage) {
			BufferedImage img        = (BufferedImage)image;
			int           width      = img.getWidth();
			int           height     = img.getHeight();
			int           colorType  = ImageUtilities.getColorSpaceType(img.getColorModel().getColorSpace());
			boolean       hasAlpha   = img.getColorModel().hasAlpha();
			boolean       isAlphaPre = img.getColorModel().isAlphaPremultiplied();

			// Can't query raster because we're possibly changing the number of channels
			int numComponents = img.getColorModel().getColorSpace().getNumComponents();
			int numChannels   = numComponents + (hasAlpha ? 1 : 0);

			// Convert to something more manageable.
			BufferedImage temp = ImageUtilities.createByteImage(
					width, height, numChannels,
					colorType,
					hasAlpha, isAlphaPre);

			BufferedImageOp op = new ColorConvertOp(temp.getColorModel().getColorSpace(), null);
			img = op.filter(img, temp);

			if (Logger.getGlobal().isLoggable(Level.FINEST))
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

	protected void runWorkers(DependentWorkerQueue<Void> workers) throws InterruptedException {
		int maxWorkers = getNumThreads() == 0 ? AVAILABLE_PROCESSORS : getNumThreads();

		// Keep track of which workers there are in the service
		Set<Future<Void>> runningWorkers = new HashSet<>(maxWorkers);

		// Run first few
		for (int i = 0; i < maxWorkers && workers.hasEligibleWorkers(); i++) {
			Callable<Void> worker = workers.takeEligibleWorker(); // Doesn't block
			runningWorkers.add(service.submit(worker));
		}

		while (!runningWorkers.isEmpty()) {
			try {
				// Wait for next completed worker
				Future<Void> future = service.take(); // Blocks
				try {
					future.get(); // Doesn't block anymore, but required to obtain the exceptions.
				} finally {
					runningWorkers.remove(future);
				}

				// If there are eligible workers workers, start one
				if (!workers.isEmpty()) {
					Callable<Void> worker = workers.takeEligibleWorker(); // Blocks
					runningWorkers.add(service.submit(worker));
				}
			} catch (CancellationException ignored) {
			} catch (InterruptedException ex) {
				runningWorkers.forEach(future -> future.cancel(true));
				Thread.currentThread().interrupt();
				throw ex;
			} catch (ExecutionException ex) {
				Throwable th = ex.getCause();
				// Check if it is one of the unchecked throwables
				if (th instanceof RuntimeException) {
					throw (RuntimeException)th;
				} else if (th instanceof Error) {
					//noinspection ProhibitedExceptionThrown
					throw (Error)th;
				} else {
					throw new AssertionError("Unhandled checked exception", th);
				}
			}
		}
	}
}
