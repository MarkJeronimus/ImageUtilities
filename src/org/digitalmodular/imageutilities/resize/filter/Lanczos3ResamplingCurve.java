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
 * Lanczos filter with 3 lobes. Radius = 3. Under/overshoot .
 *
 * @author Mark Jeronimus
 */
// Created 2015-08-14
public class Lanczos3ResamplingCurve implements ResamplingCurve {
	public static final Lanczos3ResamplingCurve INSTANCE = new Lanczos3ResamplingCurve();

	@Override
	public String getName() {
		return "Lanczos3";
	}

	@Override
	public double getRadius() {
		return 3;
	}

	@Override
	public double apply(double value) {
		if (value < 0) {
			value = -value;
		}
		if (value >= 3) {
			return 0;
		} else if (value == 0) {
			return 1;
		}

		value *= Math.PI;
		return sinc(value) * sinc(value / 3);
	}

	protected static double sinc(double value) {
		return value == 0 ? 1 : Math.sin(value) / value;
	}
}
