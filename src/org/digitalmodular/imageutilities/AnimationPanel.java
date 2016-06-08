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
package org.digitalmodular.imageutilities;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Objects;

import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;

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

/**
 * @author Mark Jeronimus
 */
// date 2015-09-08
public class AnimationPanel extends JFXPanel {
	public AnimationPanel(AnimationFrame... frames) {
		super();

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
