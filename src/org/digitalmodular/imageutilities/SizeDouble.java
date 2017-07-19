/*
 * This file has no licence. Replace this class with your own,
 * with a library class (e.g. Double2D from javafx), or keep
 * using it as-is.
 */
package org.digitalmodular.imageutilities;

public class SizeDouble {
	private final double width;
	private final double height;

	public SizeDouble(double width, double height) {
		this.width = width;
		this.height = height;
	}

	public double getHeight() { return height; }

	public double getWidth()  { return width; }

	@Override
	public String toString() {
		return "(" + width + ", " + height + ')';
	}
}
