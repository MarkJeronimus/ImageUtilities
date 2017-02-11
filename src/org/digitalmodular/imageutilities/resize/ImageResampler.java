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
	 */
	void setNumThreads(int numThreads);

	ResamplingCurve getFilter();

	/**
	 * Set the interpolation filter to use. Default is {@link Lanczos3ResamplingCurve#INSTANCE}.
	 */
	void setFilter(ResamplingCurve filter);
}
