/*
 * This file has no licence. Replace this class with your own,
 * with a library class (e.g. Point2D from javafx), or keep
 * using it as-is.
 */
package org.digitalmodular.imageutilities.util;

public class PointDouble {
	private final double x;
	private final double y;

	public PointDouble(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() { return x; }

	public double getY() { return y; }

	@Override
	public String toString() {
		return "(" + x + ", " + y + ')';
	}
}
