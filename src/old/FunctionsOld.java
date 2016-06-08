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
package old;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Mark Jeronimus
 * @since 1.0
 * @version 1.2.1
 */
// date 2007-01-05
public abstract class FunctionsOld {
	// public static final String VERSION = "1.2.1";
	//
	// public static final Pattern SCANNED_FILE_REGEX = Pattern.compile("^(.*) \\{([0-9A-Za-z$#+\\-]{6,11})(_("
	// + DesktopSize.getRegex() + "))?\\}\\.([A-Za-z]{3,4})$");
	// public static final Pattern FILE_REGEX = Pattern.compile("^(.*)\\.([A-Za-z]{3,4})$");

	public static void findAllFiles(File searchPath, boolean recursive, Pattern filter, boolean matchIf,
	                                List<File> fileList) {
		// Get the directory listing.
		File[] files = searchPath.listFiles();

		// Arrays.sort(files, Collections.reverseOrder());

		// Scan files.
		for (File file : files) {
			if (file.isFile()) {
				// Use filter if not null.
				if (filter == null || filter.matcher(file.getName()).matches() ^ matchIf) {
					fileList.add(file);
				}
			}
		}

		// Scan directories, if recursive.
		if (recursive) {
			for (File file : files) {
				if (file.isDirectory() && !file.getName().startsWith(".")) {
					FunctionsOld.findAllFiles(file, true, filter, matchIf, fileList);
				}
			}
		}
	}

	// public static void findAllFileEntries(File searchPath, boolean recursive, FileEntryDatabase database,
	// List<DiscriminatableFileEntry> fileEntryList) throws InvalidParameterException, IOException {
	// // Get the directory listing.
	// File[] files = searchPath.listFiles();
	//
	// // Scan files.
	// for (File file : files) {
	// if (file.isFile()) {
	// Matcher m = Functions.SCANNED_FILE_REGEX.matcher(file.getName());
	// if (m.matches()) {
	// int code = Base64.toCode(m.group(2));
	//
	// DiscriminatableFileEntry entry;
	// try {
	// entry = database.get(code);
	// }
	// catch (IllegalArgumentException ex) {
	// throw new InvalidParameterException("Image with unknown index exists. Reclassify the file tree.\n"
	// + file.getName());
	// }
	//
	// if (entry.file != null) {
	// throw new InvalidParameterException("Multiple images with the same index exists. Review the file tree.\n"
	// + file.getName());
	// }
	//
	// entry.file = file;
	// entry.code = code;
	// entry.nonMatches = new HashSet<>();
	//
	// fileEntryList.add(entry);
	// }
	// }
	// }
	//
	// // Scan directories, if recursive.
	// if (recursive) {
	// for (File file : files) {
	// if (file.isDirectory()) {
	// Functions.findAllFileEntries(file, true, database, fileEntryList);
	// }
	// }
	// }
	// }
}
