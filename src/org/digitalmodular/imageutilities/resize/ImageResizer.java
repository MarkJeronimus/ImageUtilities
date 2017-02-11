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

import java.awt.image.BufferedImage;

import org.digitalmodular.imageutilities.util.PointDouble;
import org.digitalmodular.imageutilities.util.SizeDouble;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2016-05-06
public interface ImageResizer {
	enum EdgeMode {
		/**
		 * Replicate edge pixels (-1 = 0, -2 = 0, etc.)
		 */
		CLAMP,
//		/**
//		 * Replicate edge pixels (-1 = w, -2 = w-1, etc.)
//		 */
//		WRAP,
//		/**
//		 * Mirror back at the edge (-1 = 1, -2 = 2, etc.)
//		 */
//		BOUNCE,
//		/**
//		 * Simulate transparent pixels beyond the edge (only applicable if the image to resize is transparent,
//		 * otherwise an {@link IllegalArgumentException} will be thrown by
//		 * {@link AbstractImageResizer#resize(Object) resize()}).
//		 */
//		TRANSPARENT
	}

	SizeInt getOutputSize();

	void setOutputSize(SizeInt outputSize);

	SizeDouble getOutputScale();

	void setOutputScale(SizeDouble outputScale);

	PointDouble getOutputOffset();

	void setOffset(PointDouble outputOffset);

	boolean isIgnoreSRGB();

	void setIgnoreSRGB(boolean ignoreSRGB);

	boolean isDontPreAlpha();

	void setDontPreAlpha(boolean dontPreAlpha);

	EdgeMode getEdgeMode();

	void setEdgeMode(EdgeMode edgeMode);

	/**
	 * Resizes the image to the dimensions previously set by {@link #setOutputSize(SizeInt) setOutputSize()}.
	 * If image size already equals the output size, it's returned unchanged.
	 * <p>
	 * The cancellation policy is to interrupt this thread. This will interrupt all workers and return as soon
	 * as possible by throwing an {@link InterruptedException}.
	 *
	 * @throws InterruptedException when the thread has been interrupted
	 */
	BufferedImage resize(BufferedImage image) throws InterruptedException;
}
