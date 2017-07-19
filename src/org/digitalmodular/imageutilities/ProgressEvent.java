/*
 * This file has no licence. Replace this class with your own,
 * with a library class, or keep using it as-is.
 */
package org.digitalmodular.imageutilities;

public class ProgressEvent {
	private final long progress;
	private final long total;

	public ProgressEvent(long progress, long total) {
		this.progress = progress;
		this.total = total;
	}

	public long getProgress() { return progress; }

	public long getTotal()    { return total; }
}
