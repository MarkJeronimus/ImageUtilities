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
package org.digitalmodular.imageutilities.resize.filter;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public interface ResamplingCurve {
	/**
	 * Returns a short, friendly name of the curve, such as one that you would use in a ComboBox.
	 *
	 * @return the name, such as <tt>"Nearest Neighbor"</tt>
	 */
	String getName();

	/**
	 * Returns the maximum number of fractional pixels in each direction that are needed to calculated the weight
	 * values
	 * for the resampled pixel.
	 * <p>
	 * The curve will be defined within the closed range [-radius, radius] and undefined outside. For example, linear
	 * interpolation needs up to one whole pixel ahead and one pixel behind, so {@code radius = 1.0}.
	 *
	 * @return the radius in fractional pixels
	 */
	double getRadius();

	/**
	 * Calculates and returns the value of the curve at the specified fractional pixel position.
	 * <p>
	 * The absolute value of {@code value} should not exceed {@link #getRadius()}. The value at position {@code 0.0}
	 * should be {@code 1.0} to prevent brightening or darkening the image. If at other integer positions (other
	 * than {@code x = 0.0}) the values is not {@code 0.0}, the image will be blurred.
	 *
	 * @param x the fractional pixel position
	 * @return the value of the curve at position {@code x}
	 */
	double apply(double x);
}
