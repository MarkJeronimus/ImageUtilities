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
 * Cubic Hermite spline resampling curve. Radius = 1. Smooth but no overshoot. Compromise between
 * {@link LinearResamplingCurve} which is not smooth and {@link CubicResamplingCurve} which overshoots. The behavior
 * is similar to a 1-dimensional B-Spline with all control points on the same height as the vertices. That is, a
 * smooth ramp of pixel intensities turns in a sort of smoothed staircase.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public class HermiteResamplingCurve implements ResamplingCurve {
	public static final HermiteResamplingCurve INSTANCE = new HermiteResamplingCurve();

	@Override
	public String getName() { return "Hermite"; }

	@Override
	public double getRadius() { return 1; }

	@Override
	public double apply(double value) {
		if (value < 0)
			value = -value;

		if (value >= 1)
			return 0;

		return (2 * value - 3) * value * value + 1;
	}
}
