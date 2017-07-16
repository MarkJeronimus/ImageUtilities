package examples;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.digitalmodular.imageutilities.ImageUtilities;
import org.digitalmodular.imageutilities.ImageUtilities.AnimationFrame;
import org.digitalmodular.imageutilities.resize.ImageResamplerShort;
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

		AnimationFrame[] frames = ImageUtilities.loadImage(new File("testimages/Clouds_at_Tarfala.gif"));

		ImageResamplerShort resampler = new ImageResamplerShort();

		SizeInt targetSize = new SizeInt(2000, 1500);
		SizeInt imageSize  = frames[0].getSize();
		SizeInt newSize    = ImageUtilities.getScalingSize(imageSize, targetSize);

//		resampler.setFilter(BoxResamplingCurve.INSTANCE);
		resampler.autoSelectFilter(imageSize, newSize);
		resampler.setOutputSize(newSize);

		frames = resampler.resize(frames);

		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setUndecorated(true);
		setContentPane(new AnimationPanel(frames));
		setBackground(new Color(0, 0, 0, 0));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}
}
