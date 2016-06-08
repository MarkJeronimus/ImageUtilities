/*
 * This file is part of ImageUtilities.
 *
 * Copyleft 2014 Mark Jeronimus. All Rights Reversed.
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
 * along with NativeAccessHooks. If not, see <http://www.gnu.org/licenses/>.
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
 * A linear curve (also known as triangle or bilinear filter) Radius = 1. No under/overshoot.
 *
 * @author Mark Jeronimus
 * @since 1.0
 */
// date 2015-08-14
public class LinearResamplingCurve implements ResamplingCurve {
	public static final LinearResamplingCurve INSTANCE = new LinearResamplingCurve();

	@Override
	public String getName() {
		return "Triangle";
	}

	@Override
	public double getRadius() {
		return 1;
	}

	@Override
	public final double apply(double x) {
		if (x < 0) {
			x = -x;
		}
		if (x >= 1) {
			return 0;
		}

		return 1 - x;
	}
}
