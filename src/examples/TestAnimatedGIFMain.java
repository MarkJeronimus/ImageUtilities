package examples;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
import org.digitalmodular.imageutilities.resize.filter.CubicResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.Lanczos3ResamplingCurve;
import org.digitalmodular.imageutilities.resize.filter.Lanczos8ResamplingCurve;
import org.digitalmodular.imageutilities.util.PointDouble;
import org.digitalmodular.imageutilities.util.SizeDouble;
import org.digitalmodular.imageutilities.util.SizeInt;

/**
 * @author Mark Jeronimus
 */
// Created 2015-08-13
public class TestAnimatedGIFMain extends JFrame {
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try {
				new TestAnimatedGIFMain();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
	}

	private int mouseX;
	private int mouseY;

	public TestAnimatedGIFMain() throws IOException, InterruptedException {
		super(TestAnimatedGIFMain.class.getSimpleName());

		AnimationFrame[] frames = ImageUtilities.loadGIFAsFrames(new File("testimages/Clouds_at_Tarfala.gif"));

		ImageResamplerShort resampler = new ImageResamplerShort();
//		resampler.setNumThreads(1);
//		resampler.setDontPreAlpha(true);
//		resampler.setIgnoreSRGB(true);

		SizeInt targetSize = new SizeInt(1000, 750);
		SizeInt imageSize  = frames[0].getSize();
		SizeInt newSize    = ImageUtilities.getScalingSize(imageSize, targetSize);

		resampler.setFilter(Lanczos3ResamplingCurve.INSTANCE);
//		resampler.autoSelectFilter(imageSize, newSize);
		resampler.setOutputSize(newSize);
//		resampler.setOutputScale(new SizeDouble(2, 10));
//		resampler.setOffset(new PointDouble(0.5, 0.5));

		frames = resampler.resize(frames);

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setUndecorated(true);
		setContentPane(new AnimationPanel(frames));
		setBackground(new Color(0, 0, 0, 0));
		setExtendedState(MAXIMIZED_BOTH);
		setLocationRelativeTo(null);
		setVisible(true);
	}
}
