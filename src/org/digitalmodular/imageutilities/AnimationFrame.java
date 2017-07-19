package org.digitalmodular.imageutilities;

import java.awt.image.BufferedImage;
import static java.util.Objects.requireNonNull;

/**
 * @author Mark Jeronimus
 */
// Created 2017-07-18 Extracted from ImageUtilities
public class AnimationFrame {
	private final BufferedImage image;
	private final int           duration;

	public AnimationFrame(BufferedImage image, int duration) {
		this.image = requireNonNull(image);
		this.duration = duration;

		if (duration < 1)
			throw new IllegalArgumentException("'duration' must be at least 1: " + duration);
	}

	public BufferedImage getImage() { return image; }

	public int getDuration()        { return duration; }

	public SizeInt getSize() {
		return new SizeInt(image.getWidth(), image.getHeight());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (!(o instanceof AnimationFrame))
			return false;

		AnimationFrame other = (AnimationFrame)o;

		return getDuration() == other.getDuration() &&
		       getImage().equals(other.getImage());
	}

	@Override
	public int hashCode() {
		int result = getImage().hashCode();
		result = 31 * result + getDuration();
		return result;
	}
}
