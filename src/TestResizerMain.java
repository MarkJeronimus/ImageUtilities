import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// date 2015-08-13
public class TestResizerMain extends JFrame implements MouseMotionListener {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				new TestResizerMain();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
	}

	private static ImageResamplerShort resampler = new ImageResamplerShort();

	static {
		resampler.setFilter(Lanczos3ResamplingCurve.INSTANCE);
		// resampler.setNumThreads(1);
		// resampler.setIgnoreSRGB(true);
		// resampler.setDontPreAlpha(true);
	}

	private int mouseX;
	private int mouseY;

	public TestResizerMain() throws IOException, InterruptedException {
		super(TestResizerMain.class.getSimpleName());

		BufferedImage resized = resizeImage("Konachan.com - 68384.jpg");

		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setUndecorated(true);
		setContentPane(ImageUtilities.createImageComponent(resized));
		setBackground(new Color(0, 0, 0, 0));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);

		addMouseMotionListener(this);
	}

	private BufferedImage resizeImage(String filename) throws IOException, InterruptedException {
		File file = new File(filename);
		Logger.getGlobal().info("Reading: " + file.getCanonicalPath());
		BufferedImage image = ImageIO.read(file);

		resampler.setOutputSize(new SizeInt(512, 512));
//		double scaleX = 2732.0 / image.getWidth();
//      double scaleY = 1620.0 / img.getHeight();
//		resampler.setOutputScale(new SizeDouble(scaleX, scaleX));
//		resampler.setOffset(new PointDouble(0, 0));

		Logger.getGlobal().info("Resizing");
		BufferedImage resampled = resampler.resize(image);

		file = new File("resampled.png");
		Logger.getGlobal().info("Writing: " + file.getCanonicalPath());
		ImageIO.write(resampled, "PNG", file);

		Logger.getGlobal().info("Showing");
		return resampled;
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		setLocation(e.getXOnScreen() - mouseX, e.getYOnScreen() - mouseY);
	}
}
