package examples;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.Objects.requireNonNull;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * Users are encouraged to create their own animation panel instead of using this. It's included only as a reference
 * implementation. Behavior may change between releases without notice.
 *
 * @author Mark Jeronimus
 */
// Created 2015-09-08
public class AnimationPanel extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {
	private final Timer frameTimer = new Timer(1000, ignored -> repaint());

	private AnimationFrame[] animation = null;

	private int  zoomFactor      = 1;
	private int  offsetX         = 0;
	private int  offsetY         = 0;
	private int  mouseX          = 0;
	private int  mouseY          = 0;
	private int  frame           = 0;
	private long lastRepaintTime = 0;

	public AnimationPanel() {
		super(new BorderLayout());
		setOpaque(false);

		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
	}

	public AnimationPanel(AnimationFrame[] frames) {
		this();
		setImage(frames);
	}

	public void setImage(AnimationFrame[] animation) {
		if (!SwingUtilities.isEventDispatchThread())
			throw new IllegalStateException("This may only be executed on the EDT");
		requireNonNull(animation, "animation");
		if (animation.length == 0)
			throw new IllegalArgumentException("animation without frames");

		if (!Arrays.equals(this.animation, animation)) {
			this.animation = animation;

			frame = 0;
			lastRepaintTime = 0;
			frameTimer.stop();

			SizeInt size = animation[0].getSize();

			setPreferredSize(new Dimension(size.getWidth(), size.getHeight()));
		}

		repaint();
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (animation == null)
			return;

		int nextFrame = (frame + 1) % animation.length;
		try {
			if (animation.length > 1) {
				long time                 = System.nanoTime();
				int  timeSinceLastRepaint = lastRepaintTime == 0 ? 0 : (int)((time - lastRepaintTime) / 1_000_000);
				int  delay                = Math.max(1, animation[nextFrame].getDuration() - timeSinceLastRepaint);
				lastRepaintTime = time;

				frameTimer.stop();
				frameTimer.setInitialDelay(delay);
				frameTimer.setDelay(delay);
				frameTimer.start();
			}

			BufferedImage image = animation[frame].getImage();
			SizeInt       size  = animation[frame].getSize();

			AffineTransform transform = getTransform(size);
			((Graphics2D)g).transform(transform);

			g.drawImage(image, 0, 0, null);
			g.setColor(Color.GRAY);
			g.drawString(image.getWidth() + "×" + image.getHeight() + ", frame:" + frame, 0, -3);
			g.drawRect(-1, -1, size.getWidth() + 1, size.getHeight() + 1);
		} finally {
			frame = nextFrame;
		}
	}

	private AffineTransform getTransform(SizeInt imageSize) {
		int width  = getWidth();
		int height = getHeight();

		SizeInt targetSize  = new SizeInt(width, height);
		SizeInt displaySize = zoom(imageSize);

		float sx = (float)displaySize.getWidth() / imageSize.getWidth();
		float sy = (float)displaySize.getHeight() / imageSize.getHeight();

		int tx = width / 2;
		int ty = height / 2;

		tx -= offsetX;
		ty -= offsetY;

		tx -= displaySize.getWidth() / 2;
		ty -= displaySize.getHeight() / 2;

		return new AffineTransform(sx, 0, 0, sy, tx, ty);
	}

	private SizeInt zoom(SizeInt imageSize) {
		return new SizeInt(imageSize.getWidth() * zoomFactor,
		                   imageSize.getHeight() * zoomFactor);
	}

	@Override
	public void mouseClicked(MouseEvent e) { }

	@Override
	public void mousePressed(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	@Override
	public void mouseReleased(MouseEvent e) { }

	@Override
	public void mouseEntered(MouseEvent e) { }

	@Override
	public void mouseExited(MouseEvent e) { }

	@Override
	public void mouseDragged(MouseEvent e) {
		offsetX -= e.getX() - mouseX;
		offsetY -= e.getY() - mouseY;
		mouseX = e.getX();
		mouseY = e.getY();

		repaint();
	}

	@Override
	public void mouseMoved(MouseEvent e) { }

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		int newZoom = Math.max(1, Math.min(16, zoomFactor - e.getWheelRotation()));
		if (newZoom == zoomFactor)
			return;

		try {
			assert animation != null && animation[0] != null;
			SizeInt imageSize = animation[0].getSize();

			AffineTransform transform        = getTransform(imageSize);
			AffineTransform inverseTransform = transform.createInverse();
			Point           mousePoint       = e.getPoint();
			Point2D         imgPoint         = inverseTransform.transform(mousePoint, null);

			mousePoint.x -= getWidth() / 2;
			mousePoint.y -= getHeight() / 2;
			imgPoint.setLocation((imgPoint.getX() - imageSize.getWidth() / 2.0) * newZoom,
			                     (imgPoint.getY() - imageSize.getHeight() / 2.0) * newZoom);

			offsetX = (int)imgPoint.getX() - mousePoint.x;
			offsetY = (int)imgPoint.getY() - mousePoint.y;
		} catch (NoninvertibleTransformException ex) {
			Logger.getGlobal().log(Level.INFO, ex.getMessage(), ex);
		}

		zoomFactor = newZoom;

		repaint();
	}
}
