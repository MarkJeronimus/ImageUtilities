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
 * Cubic (usually incorrectly called Bicubic) resampling filter. Radius = 2. Overshoot present. Using the
 * default constructor generates a cardinal cubic spline with sharpness{@code -0.5} (in literature often quoted as
 * the "a" parameter. This is a 1-parameter version because it fixes x-axis intercepts at {@code -1} and {@code +1}, to
 * prevent artificial blurring or sharpening. For more details, see
 * <a href="http://entropymine.com/imageworsener/bicubic">entropymine.com/imageworsener/bicubic</a>.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public class CubicResamplingCurve implements ResamplingCurve {
	/** Sharpness is {@code -0.5f}. Otherwise known as Catmull-Rom. */
	public static final CubicResamplingCurve INSTANCE           = new CubicResamplingCurve(-0.5f);
	/** Sharpness is {@code -0.75}, precisely in between {@link #INSTANCE} and {@link #INSTANCE_SHARPER}. */
	public static final CubicResamplingCurve INSTANCE_PHOTOSHOP = new CubicResamplingCurve(-0.75f);
	/** Sharpness is {@code -1.0f}. */
	public static final CubicResamplingCurve INSTANCE_SHARPER   = new CubicResamplingCurve(-1.0f);

	private final double sharpness;

	public CubicResamplingCurve() {
		sharpness = -0.5;
	}

	public CubicResamplingCurve(double sharpness) {
		this.sharpness = sharpness;
	}

	@Override
	public String getName() { return "Cubic"; }

	@Override
	public double getRadius() { return 2.0f; }

	@Override
	public final double apply(double value) {
		if (value < 0)
			value = -value;

		if (value >= 2)
			return 0;

		double xx = value * value;
		if (value < 1)
			return (sharpness + 2) * xx * value - (sharpness + 3) * xx + 1;
		else
			return sharpness * xx * value - 5 * sharpness * xx + 8 * sharpness * value - 4 * sharpness;
	}
}
