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
package org.digitalmodular.imageutilities.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mark Jeronimus
 */
// Created 2015-09-08
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PerformanceTimer {
	private static final String FORMAT = "%7.2f";

	private final List<Long>   durations    = new ArrayList<>(32);
	private final List<String> descriptions = new ArrayList<>(32);

	private static boolean printingEnabled = false;

	private long startTime                = 0;
	private long lastTime                 = 0;
	private int  longestDescriptionLength = 0;

	public static boolean isPrintingEnabled() {
		return printingEnabled;
	}

	public static void setPrintingEnabled(boolean printingEnabled) {
		PerformanceTimer.printingEnabled = printingEnabled;
	}

	public void reset() {
		durations.clear();
		descriptions.clear();
	}

	public void start() {
		startTime = System.nanoTime();
		lastTime = startTime;
		longestDescriptionLength = 0;
	}

	public void record(String description) {
		long time = System.nanoTime();
		durations.add(time - lastTime);
		descriptions.add(description);
		longestDescriptionLength = Math.max(longestDescriptionLength, description.length());
		lastTime = time;
	}

	public void printResults() {
		if (!printingEnabled)
			return;

		String formatString = "%-" + longestDescriptionLength + "s " + FORMAT + '\n';
		for (int i = 0; i < durations.size(); i++) {
			long   duration    = durations.get(i);
			String description = descriptions.get(i);
			System.out.printf(formatString, description, duration / 1.0e6);
		}
	}

	/**
	 * Prints the durations of each record and the amount of work performed per second in each step.
	 */
	public void printResults(double workload) {
		if (!printingEnabled)
			return;

		String formatString = "%-" + longestDescriptionLength + "s " + FORMAT + " (%,9.2f)\n";
		for (int i = 0; i < durations.size(); i++) {
			long   duration    = durations.get(i);
			String description = descriptions.get(i);
			System.out.printf(formatString, description, duration / 1.0e6, workload * 1.0e3 / duration);
		}
	}

	public void printTotal() {
		if (!printingEnabled)
			return;

		String formatString = "%-" + longestDescriptionLength + "s " + FORMAT + '\n';
		long   duration     = lastTime - startTime;
		String description  = "Total";
		System.out.printf(formatString, description, duration / 1.0e6);
	}

	/**
	 * Prints the total duration and the amount of work performed per second.
	 */
	public void printTotal(long workload) {
		if (!printingEnabled)
			return;

		String formatString = "%-" + longestDescriptionLength + "s " + FORMAT + " (%,9.2f)\n";
		for (int i = 0; i < durations.size(); i++) {
			long   duration    = lastTime - startTime;
			String description = "Total";
			System.out.printf(formatString, description, duration / 1.0e6, workload * 1.0e3 / duration);
		}
	}
}
