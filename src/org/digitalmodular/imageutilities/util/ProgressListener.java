/*
 * This file has no licence. Replace this class with your own,
 * with a library class, or keep using it as-is.
 */
package org.digitalmodular.imageutilities.util;

public interface ProgressListener {
	void progressUpdated(ProgressEvent e);

	void progressCompleted(ProgressEvent e);
}
