/*
 * This file has no licence. Replace this class with your own,
 * with a library class (e.g. Size2D from javafx), or keep
 * using it as-is.
 */
package org.digitalmodular.imageutilities.util;

import java.awt.image.BufferedImage;

public class SizeInt {
	private final int width;
	private final int height;

	public SizeInt(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public SizeInt(BufferedImage img) {
		width = img.getWidth();
		height = img.getHeight();
	}

	public int getWidth()  { return width; }

	public int getHeight() { return height; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof SizeInt)) return false;

		SizeInt sizeInt = (SizeInt)o;

		return getWidth() == sizeInt.getWidth() &&
		       getHeight() == sizeInt.getHeight();
	}

	@Override
	public int hashCode() {
		int hash = 0x4C1DF00D;
		hash *= 0x01000193;
		hash ^= getWidth();
		hash *= 0x01000193;
		hash ^= getHeight();
		return hash;
	}

	@Override
	public String toString() {
		return "(" + width + ", " + height + ')';
	}
}
