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
 * Cubic (usually incorrectly called Bicubic) resampling filter. Radius = 2. Under/overshoot present. Using the
 * default constructor generates a cardinal cubic spline with {@code a = -0.5}. For more details, see
 * <a href="http://entropymine.com/imageworsener/bicubic">entropymine.com/imageworsener/bicubic</a>.
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public class CubicResamplingCurve implements ResamplingCurve {
	/** Equal to Catmull-Rom */
	public static final CubicResamplingCurve INSTANCE           = new CubicResamplingCurve(-0.5f);
	public static final CubicResamplingCurve INSTANCE_PHOTOSHOP = new CubicResamplingCurve(-0.75f);
	public static final CubicResamplingCurve INSTANCE_SHARPER   = new CubicResamplingCurve(-1);

	final protected double a;

	public CubicResamplingCurve() {
		a = -0.5;
	}

	public CubicResamplingCurve(double a) {
		this.a = a;
	}

	@Override
	public double getRadius() {
		return 2.0f;
	}

	@Override
	public String getName() {
		return "Cubic";
	}

	@Override
	public final double apply(double x) {
		if (x < 0) {
			x = -x;
		}
		if (x >= 2) {
			return 0;
		}

		double xx = x * x;
		if (x < 1) {
			return (a + 2) * xx * x - (a + 3) * xx + 1;
		} else {
			return a * xx * x - 5 * a * xx + 8 * a * x - 4 * a;
		}
	}
}
