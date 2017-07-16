package examples;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;

/**
 * Users are encouraged to create their own animation panel instead of using this. It's included only as a reference
 * implementation. Behavior may change between releases.
 *
 * @author Mark Jeronimus
 */
// Created 2015-09-08
public class AnimationPanel extends JFXPanel {
	public AnimationPanel(AnimationFrame... frames) {
		Objects.requireNonNull(frames);
		if (frames.length == 0) {
			throw new IllegalArgumentException("Empty array");
		}

		Dimension size = null;
		for (int i = 0; i < frames.length; i++) {
			AnimationFrame frame = Objects.requireNonNull(frames[i], "Null frame at index " + i);
			BufferedImage  image = Objects.requireNonNull(frame.getImage(), "Null image at index " + i);

			int width  = image.getWidth();
			int height = image.getHeight();

			if (size == null) {
				size = new Dimension(width, height);
			} else if (size.width != width || size.height != height) {
				throw new IllegalArgumentException(
						"Not all image sizes the same, at index " + i + ": " + size + " vs " + width + ", " + height);
			} else if (frame.getDuration() == 0) {
				throw new IllegalArgumentException("Zero duration at index " + i);
			}
		}

		ImageView             view          = new ImageView();
		ObjectProperty<Image> imageProperty = view.imageProperty();

		KeyFrame[] keyFrames     = new KeyFrame[frames.length + 1];
		KeyValue   firstKeyValue = null;
		long       time          = 0;
		for (int i = 0; i < frames.length; i++) {
			BufferedImage bufImg   = frames[i].getImage();
			long          duration = frames[i].getDuration();

			Image    image         = SwingFXUtils.toFXImage(bufImg, null);
			KeyValue imageKeyValue = new KeyValue(imageProperty, image, Interpolator.DISCRETE);
			if (i == 0) {
				firstKeyValue = imageKeyValue;
			}
			keyFrames[i] = new KeyFrame(Duration.millis(time), imageKeyValue);

			time += duration;
		}

		keyFrames[frames.length] = new KeyFrame(Duration.millis(time), firstKeyValue);

		Timeline timeline = new Timeline(keyFrames);
		timeline.setCycleCount(Animation.INDEFINITE);
		timeline.play();

		setScene(new Scene(new Group(view)));
		setPreferredSize(size);

		// experiments:
		setOpaque(false);
		setBackground(Color.RED);
	}
}
