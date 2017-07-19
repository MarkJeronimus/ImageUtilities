package org.digitalmodular.imageutilities;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Mark Jeronimus
 */
// Created 2017-07-18 Extracted from ImageUtilities
public enum GIFLoader {
	;

	public static AnimationFrame[] read(File file) throws IOException {
		try (ImageInputStream inputStream = ImageIO.createImageInputStream(file)) {
			// Prepare a decoder
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(inputStream);

			int              numImages = reader.getNumImages(true);
			AnimationFrame[] frames    = new AnimationFrame[numImages];

			BufferedImage image = null;

			for (int i = 0; i < numImages; i++) {
				IIOMetadata metadata = reader.getImageMetadata(i);
				Element     tree     = (Element)metadata.getAsTree(metadata.getNativeMetadataFormatName());

				// Default attributes in case they're not found.
				int     imageLeftPosition    = 0;
				int     imageTopPosition     = 0;
				String  disposalMethod       = null;
				boolean transparentColorFlag = false;
				int     delayTime            = 10;

				// Find all attributes of interest.
				NodeList imageDescriptor = tree.getElementsByTagName("ImageDescriptor");
				if (imageDescriptor.getLength() > 0) {
					NamedNodeMap attributes = imageDescriptor.item(0).getAttributes();
					for (int k = 0; k < attributes.getLength(); k++) {
						Node attribute = attributes.item(k);
						if ("imageLeftPosition".equals(attribute.getNodeName()))
							imageLeftPosition = Integer.parseInt(attribute.getNodeValue());
						else if ("imageTopPosition".equals(attribute.getNodeName()))
							imageTopPosition = Integer.parseInt(attribute.getNodeValue());
					}
				}

				NodeList graphicControlExtension = tree.getElementsByTagName("GraphicControlExtension");
				if (graphicControlExtension.getLength() > 0) {
					NamedNodeMap attributes = graphicControlExtension.item(0).getAttributes();
					for (int k = 0; k < attributes.getLength(); k++) {
						Node attribute = attributes.item(k);
						if ("disposalMethod".equals(attribute.getNodeName()))
							disposalMethod = attribute.getNodeValue();
						else if ("transparentColorFlag".equals(attribute.getNodeName()))
							transparentColorFlag = Boolean.parseBoolean(attribute.getNodeValue());
						else if ("delayTime".equals(attribute.getNodeName()))
							delayTime = Integer.parseInt(attribute.getNodeValue());
					}
				}

				NodeList globalColorTable      = tree.getElementsByTagName("GlobalColorTable");
				NodeList globalScreeDescriptor = tree.getElementsByTagName("LogicalScreenDescriptor");
				if (globalColorTable.getLength() > 0 || globalScreeDescriptor.getLength() > 0)
					Thread.yield(); // [breakpoint] TODO implement once images with this are found

				// Fix invalid delayTime
				if (delayTime == 0)
					delayTime = 10;

				BufferedImage animationFrame = reader.read(i, null);

				if (image == null) {
					int type = transparentColorFlag ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
					image = new BufferedImage(animationFrame.getWidth(), animationFrame.getHeight(), type);
				}

				// Compose the source frame on top of the accumulated frame
				Graphics g = image.getGraphics();
				try {
					g.drawImage(animationFrame, imageLeftPosition, imageTopPosition, null);
				} finally {
					g.dispose();
				}

				// Copy the accumulated image to make an animation frame.
				animationFrame = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

				g = animationFrame.getGraphics();
				try {
					g.drawImage(image, 0, 0, null);
				} finally {
					g.dispose();
				}

				frames[i] = new AnimationFrame(animationFrame, delayTime * 10);

				if (disposalMethod != null) {
					switch (disposalMethod) {
						case "none":
						case "doNotDispose":
							// Leave image as is.
							break;
						case "restoreToBackgroundColor":
							// TODO implement once GlobalColorTable has been decoded
							Arrays.fill(((DataBufferInt)image.getRaster().getDataBuffer()).getData(), 0x00000000);
							break;
						case "restoreToPrevious":
							Thread.yield(); // [breakpoint] TODO implement once images with this are found
							break;
						default:
							throw new UnsupportedOperationException("disposalMethod: " + disposalMethod);
					}
				}
			}

			return frames;
		}
	}
}
