/*
 * This file is part of ImageUtilities.
 *
 * Copyleft 2016 Mark Jeronimus. All Rights Reversed.
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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import static java.util.Objects.requireNonNull;
import org.digitalmodular.imageutilities.util.SizeInt;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Mark Jeronimus
 */
// Created 2009-04-28
// Changed 2015-08-15 added functions for AbstractImageResizer
// Changed 2015-09-08 added functions animations
public enum ImageUtilities {
	;

	public enum ScalingTarget {
		STRETCH,
		INSIDE,
		OUTSIDE,
		WIDTH_TOUCH,
		HEIGHT_TOUCH,
		SAME_DIAGONAL,
		SAME_CIRCUMFERENCE,
		SAME_AREA
	}

	public enum ScalingCondition {
		ALWAYS,
		NEVER,
		ONLY_IF_LARGER,
		ONLY_IF_SMALLER,
		ONLY_INTEGER_ZOOM
	}

	public static class AnimationFrame {
		private final BufferedImage image;
		private final long          duration;

		public AnimationFrame(BufferedImage image, long duration) {
			this.image = requireNonNull(image);
			this.duration = duration;
		}

		public BufferedImage getImage()                     { return image; }

		public AnimationFrame setImage(BufferedImage image) { return new AnimationFrame(image, duration); }

		public long getDuration()                           { return duration; }

		public AnimationFrame setDuration(long duration)    { return new AnimationFrame(image, duration); }

		public SizeInt getSize() {
			return new SizeInt(image.getWidth(), image.getHeight());
		}
	}

	public static JComponent createImageComponent(BufferedImage image) {
		requireNonNull(image);

		return new JLabel(new ImageIcon(image));
	}

	public static JComponent createImageComponent(AnimationFrame... frames) {
		requireNonNull(frames);

		if (frames.length == 0) {
			throw new IllegalArgumentException("Empty array");
		} else if (frames.length == 1) {
			return createImageComponent(frames[0].getImage());
		} else {
			return new AnimationPanel(frames);
		}
	}

	public static Image loadGIF(File file) throws IOException {
		try (ImageInputStream inputStream = ImageIO.createImageInputStream(file)) {
			// Prepare a decoder
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(inputStream);

			int numImages = reader.getNumImages(true);

			// Find out if GIF is bugged
			boolean foundBug = false;
			findBug:
			for (int i = 0; i < numImages; i++) {
				IIOMetadata metadata = reader.getImageMetadata(i);
				Node        tree     = metadata.getAsTree(metadata.getNativeMetadataFormatName());

				// Find delay, which is at: <root><GraphicControlExtension delayTime=##></root>
				NodeList children = tree.getChildNodes();
				for (int j = 0; j < children.getLength(); j++) {
					Node node = children.item(j);
					if ("GraphicControlExtension".equals(node.getNodeName())) {
						NamedNodeMap attributes = node.getAttributes();
						for (int k = 0; k < attributes.getLength(); k++) {
							Node attribute = attributes.item(k);
							if ("delayTime".equals(attribute.getNodeName())
							    && "0".equals(attribute.getNodeValue())) {
								foundBug = true;
								break findBug;
							}
						}
					}
				}
			}

			Logger.getLogger(ImageUtilities.class.getName()).finer("foundBug: " + foundBug);

			if (!foundBug) {
				// Load non-buggy GIF the normal way
				return Toolkit.getDefaultToolkit().createImage(file.getName());
			}

			// Prepare stream to encode fixed GIF to
			ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
			try (ImageOutputStream outputStream = ImageIO.createImageOutputStream(baoStream)) {
				// Prepare an encoder
				ImageWriter writer = ImageIO.getImageWriter(reader);
				writer.setOutput(outputStream);
				writer.prepareWriteSequence(null);

				for (int i = 0; i < numImages; i++) {
					// Get input image
					BufferedImage frameIn = reader.read(i);

					// Get metadata tree for this input frame
					IIOMetadata metadata = reader.getImageMetadata(i);
					Node        tree     = metadata.getAsTree(metadata.getNativeMetadataFormatName());

					// Find delay, which is at: <root><GraphicControlExtension delayTime=##></root>
					NodeList children = tree.getChildNodes();
					for (int j = 0; j < children.getLength(); j++) {
						Node node = children.item(j);
						if ("GraphicControlExtension".equals(node.getNodeName())) {
							NamedNodeMap attributes = node.getAttributes();
							for (int k = 0; k < attributes.getLength(); k++) {
								Node attribute = attributes.item(k);
								if ("delayTime".equals(attribute.getNodeName())
								    && "0".equals(attribute.getNodeValue())) {
									// Overwrite with the intended delay value
									((IIOMetadataNode) node).setAttribute("delayTime", "10");
									break;
								}
							}
							break;
						}
					}

					// Set metadata tree for this output frame
					metadata = writer.getDefaultImageMetadata(new ImageTypeSpecifier(frameIn), null);
					metadata.setFromTree(metadata.getNativeMetadataFormatName(), tree);

					// Create and encode output image
					IIOImage frameOut = new IIOImage(frameIn, null, metadata);
					writer.writeToSequence(frameOut, writer.getDefaultWriteParam());
				}

				writer.endWriteSequence();
			}

			// Create image using encoded data
			return Toolkit.getDefaultToolkit().createImage(baoStream.toByteArray());
		}
	}

	public static AnimationFrame[] loadGIFAsFrames(File file) throws IOException {
		try (ImageInputStream inputStream = ImageIO.createImageInputStream(file)) {
			// Prepare a decoder
			ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
			reader.setInput(inputStream);

			int              numImages = reader.getNumImages(true);
			AnimationFrame[] frames    = new AnimationFrame[numImages];

			BufferedImage image = null;

			for (int i = 0; i < numImages; i++) {
				IIOMetadata     metadata = reader.getImageMetadata(i);
				IIOMetadataNode tree     = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());

				Logger.getGlobal().info(dumpTree(new StringBuilder(), tree, 0).toString());

				// Default attributes in case they're not found.
				int     imageLeftPosition    = 0;
				int     imageTopPosition     = 0;
				String  disposalMethod       = "doNotDispose"; // other options?
				boolean transparentColorFlag = false;
				int     delayTime            = 10;

				// Find all attributes of interest.
				NodeList children = tree.getChildNodes();
				for (int j = 0; j < children.getLength(); j++) {
					Node node = children.item(j);
					if ("ImageDescriptor".equals(node.getNodeName())) {
						NamedNodeMap attributes = node.getAttributes();
						for (int k = 0; k < attributes.getLength(); k++) {
							Node attribute = attributes.item(k);
							if ("imageLeftPosition".equals(attribute.getNodeName()))
								imageLeftPosition = Integer.parseInt(attribute.getNodeValue());
							if ("imageTopPosition".equals(attribute.getNodeName()))
								imageTopPosition = Integer.parseInt(attribute.getNodeValue());
						}
					} else if ("GraphicControlExtension".equals(node.getNodeName())) {
						NamedNodeMap attributes = node.getAttributes();
						for (int k = 0; k < attributes.getLength(); k++) {
							Node attribute = attributes.item(k);
							if ("disposalMethod".equals(attribute.getNodeName()))
								disposalMethod = attribute.getNodeValue();
							if ("transparentColorFlag".equals(attribute.getNodeName()))
								transparentColorFlag = Boolean.parseBoolean(attribute.getNodeValue());
							if ("delayTime".equals(attribute.getNodeName()))
								delayTime = Integer.parseInt(attribute.getNodeValue());
						}
					}
				}

				// Fix invalid delayTime
				if (delayTime == 0) delayTime = 10;

				BufferedImage frame = reader.read(i, null);

				switch (disposalMethod) {
					case "doNotDispose":
						// Leave image as is.
						break;
					default:
						throw new UnsupportedOperationException(disposalMethod);
				}

				if (image == null) {
					int type = transparentColorFlag ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
					image = new BufferedImage(frame.getWidth(), frame.getHeight(), type);
				}

				if (image != frame) {
					// Compose the source frame on top of the accumulated frame
					Graphics g = image.getGraphics();
					try {
						g.drawImage(frame, imageLeftPosition, imageTopPosition, null);
					} finally {
						g.dispose();
					}

					// Copy the accumulated image to make an animation frame.
					frame = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

					g = frame.getGraphics();
					try {
						g.drawImage(image, 0, 0, null);
					} finally {
						g.dispose();
					}
				}

				frames[i] = new AnimationFrame(frame, delayTime * 10);
			}

			return frames;
		}
	}

	private static StringBuilder dumpTree(StringBuilder sb, Node parent, int depth) {
		String       nodeName   = parent.getNodeName();
		String       nodeValue  = parent.getNodeValue();
		NamedNodeMap attributes = parent.getAttributes();
		NodeList     children   = parent.getChildNodes();

		for (int i = 0; i < depth; i++) sb.append("  ");
		sb.append('<').append(nodeName);
		if (nodeValue != null) sb.append('=').append(nodeValue);

		for (int i = 0; i < attributes.getLength(); i++) {
			Node   attribute      = attributes.item(i);
			String attributeName  = attribute.getNodeName();
			String attributeValue = attribute.getNodeValue();
			sb.append(' ').append(attributeName).append("=\"").append(attributeValue).append('"');
		}

		if (children.getLength() == 0) sb.append('/');

		sb.append(">\n");

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			dumpTree(sb, child, depth + 1);
		}

		if (children.getLength() != 0) {
			for (int i = 0; i < depth; i++) sb.append("  ");
			sb.append("</").append(nodeName).append(">\n");
		}

		return sb;
	}

	public static String analyzeImage(Image img) {
		if (img instanceof BufferedImage) {
			BufferedImage image       = (BufferedImage) img;
			int           srcDataType = image.getRaster().getDataBuffer().getDataType();
			int srcColorType = ImageUtilities
					.getColorSpaceType(image.getColorModel().getColorSpace());
			boolean srcIsSRGB               = srcColorType != ColorSpace.CS_LINEAR_RGB;
			int     numComponents           = image.getColorModel().getColorSpace().getNumComponents();
			boolean hasAlpha                = image.getColorModel().hasAlpha();
			boolean srcIsAlphaPremultiplied = image.getColorModel().isAlphaPremultiplied();
			return imageTypeName(image.getType())
			       + " / " + dataTypeName(srcDataType)
			       + " / " + (srcIsSRGB ? "sRGB" : "linear")
			       + " / " + numComponents + " components"
			       + " / " + (hasAlpha ? "alpha" : "opaque")
			       + (srcIsAlphaPremultiplied ? " premultiplied" : "");
		} else {
			return img.getClass().getSimpleName();
		}
	}

	public static String dataTypeName(int type) {
		switch (type) {
			case DataBuffer.TYPE_BYTE:
				return "TYPE_BYTE";
			case DataBuffer.TYPE_USHORT:
				return "TYPE_USHORT";
			case DataBuffer.TYPE_SHORT:
				return "TYPE_SHORT";
			case DataBuffer.TYPE_INT:
				return "TYPE_INT";
			case DataBuffer.TYPE_FLOAT:
				return "TYPE_FLOAT";
			case DataBuffer.TYPE_DOUBLE:
				return "TYPE_DOUBLE";
			case DataBuffer.TYPE_UNDEFINED:
				return "TYPE_UNDEFINED";
			default:
				return Integer.toString(type);
		}
	}

	public static String colorSpaceName(int type) {
		switch (type) {
			case ColorSpace.CS_sRGB:
				return "CS_sRGB";
			case ColorSpace.CS_LINEAR_RGB:
				return "CS_LINEAR_RGB";
			case ColorSpace.CS_CIEXYZ:
				return "CS_CIEXYZ";
			case ColorSpace.CS_PYCC:
				return "CS_PYCC";
			case ColorSpace.CS_GRAY:
				return "CS_GRAY";
			default:
				return Integer.toString(type);
		}
	}

	public static String imageTypeName(int type) {
		switch (type) {
			case BufferedImage.TYPE_3BYTE_BGR:
				return "TYPE_3BYTE_BGR";
			case BufferedImage.TYPE_4BYTE_ABGR:
				return "TYPE_4BYTE_ABGR";
			case BufferedImage.TYPE_4BYTE_ABGR_PRE:
				return "TYPE_4BYTE_ABGR_PRE";
			case BufferedImage.TYPE_BYTE_BINARY:
				return "TYPE_BYTE_BINARY";
			case BufferedImage.TYPE_BYTE_GRAY:
				return "TYPE_BYTE_GRAY";
			case BufferedImage.TYPE_BYTE_INDEXED:
				return "TYPE_BYTE_INDEXED";
			case BufferedImage.TYPE_CUSTOM:
				return "TYPE_CUSTOM";
			case BufferedImage.TYPE_INT_ARGB:
				return "TYPE_INT_ARGB";
			case BufferedImage.TYPE_INT_ARGB_PRE:
				return "TYPE_INT_ARGB_PRE";
			case BufferedImage.TYPE_INT_BGR:
				return "TYPE_INT_BGR";
			case BufferedImage.TYPE_INT_RGB:
				return "TYPE_INT_RGB";
			case BufferedImage.TYPE_USHORT_555_RGB:
				return "TYPE_USHORT_555_RGB";
			case BufferedImage.TYPE_USHORT_565_RGB:
				return "TYPE_USHORT_565_RGB";
			case BufferedImage.TYPE_USHORT_GRAY:
				return "TYPE_USHORT_GRAY";
			default:
				return Integer.toString(type);
		}
	}

	public static int getColorSpaceType(ColorSpace cs) {
		// In order of likelyhood, to prevent unnecessary instantiations inside ColorSpace.
		if (cs == ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB)) {
			return ColorSpace.CS_LINEAR_RGB;
		} else if (cs == ColorSpace.getInstance(ColorSpace.CS_GRAY)) {
			return ColorSpace.CS_GRAY;
		} else if (cs == ColorSpace.getInstance(ColorSpace.CS_sRGB)) {
			return ColorSpace.CS_sRGB;
		} else if (cs == ColorSpace.getInstance(ColorSpace.CS_CIEXYZ)) {
			return ColorSpace.CS_CIEXYZ;
		} else if (cs == ColorSpace.getInstance(ColorSpace.CS_PYCC)) {
			return ColorSpace.CS_PYCC;
		} else {
			return -1;
		}
	}

	public static int getBufferedImageType(int numComponents, boolean withAlpha) {
		switch (numComponents) {
			case 1:
				if (!withAlpha) {
					return BufferedImage.TYPE_BYTE_GRAY;
				}
				break;
			case 3:
				return withAlpha ? BufferedImage.TYPE_4BYTE_ABGR : BufferedImage.TYPE_3BYTE_BGR;
		}
		throw new UnsupportedOperationException(
				"numComponents: " + numComponents + ", withAlpha: " + withAlpha);
	}

	public static SizeInt getScalingSize(SizeInt imageSize, SizeInt targetSize) {
		return getScalingSize(imageSize, targetSize, ScalingTarget.INSIDE, ScalingCondition.ALWAYS);
	}

	public static SizeInt getScalingSize(SizeInt imageSize, SizeInt targetSize, ScalingTarget scalingTarget) {
		return getScalingSize(imageSize, targetSize, scalingTarget, ScalingCondition.ALWAYS);
	}

	public static SizeInt getScalingSize(SizeInt imageSize, SizeInt targetSize, ScalingTarget scalingTarget,
	                                     ScalingCondition scalingCondition) {
		int wCross = imageSize.getWidth() * targetSize.getHeight();
		int hCross = imageSize.getHeight() * targetSize.getWidth();

		int width;
		int height;
		switch (scalingTarget) {
			case STRETCH:
				width = imageSize.getWidth();
				height = imageSize.getHeight();
				break;
			case INSIDE:
				if (wCross > hCross) {
					width = targetSize.getWidth();
					height = intDivRound(hCross, imageSize.getWidth());
				} else {
					width = intDivRound(wCross, imageSize.getHeight());
					height = targetSize.getHeight();
				}
				break;
			case OUTSIDE:
				if (wCross < hCross) {
					width = targetSize.getWidth();
					height = intDivRound(hCross, imageSize.getWidth());
				} else {
					width = intDivRound(wCross, imageSize.getHeight());
					height = targetSize.getHeight();
				}
				break;
			case WIDTH_TOUCH:
				width = targetSize.getWidth();
				height = intDivRound(hCross, imageSize.getWidth());
				break;
			case HEIGHT_TOUCH:
				width = intDivRound(wCross, imageSize.getHeight());
				height = targetSize.getHeight();
				break;
			case SAME_DIAGONAL:
				double vDia = Math.hypot(targetSize.getWidth(), targetSize.getHeight());
				double iDia = Math.hypot(imageSize.getWidth(), imageSize.getHeight());
				width = (int) Math.rint(imageSize.getWidth() * vDia / iDia);
				height = (int) Math.rint(imageSize.getHeight() * vDia / iDia);
				break;
			case SAME_CIRCUMFERENCE:
				int vSum = targetSize.getWidth() + targetSize.getHeight();
				int iSum = imageSize.getWidth() + imageSize.getHeight();
				width = intDivRound(imageSize.getWidth() * vSum, iSum);
				height = intDivRound(imageSize.getHeight() * vSum, iSum);
				break;
			case SAME_AREA:
				int vArea = targetSize.getWidth() * targetSize.getHeight();
				int iArea = imageSize.getWidth() * imageSize.getHeight();
				width = intDivRound(imageSize.getWidth() * vArea, iArea);
				height = intDivRound(imageSize.getHeight() * vArea, iArea);
				break;
			default:
				throw new IllegalArgumentException("Unknown scalingHint: " + scalingTarget);
		}

		switch (scalingCondition) {
			case ALWAYS:
				break;
			case NEVER:
				width = imageSize.getWidth();
				height = imageSize.getHeight();
				break;
			case ONLY_IF_LARGER:
				if (width >= imageSize.getWidth() && height >= imageSize.getHeight()) {
					width = imageSize.getWidth();
					height = imageSize.getHeight();
				}
				break;
			case ONLY_IF_SMALLER:
			case ONLY_INTEGER_ZOOM:
				if (width <= imageSize.getWidth() && height <= imageSize.getHeight()) {
					width = imageSize.getWidth();
					height = imageSize.getHeight();
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown scalingCondition: " + scalingCondition);
		}

		return new SizeInt(width, height);
	}

	public static int intDivRound(int numerator, int denominator) {
		return denominator == 0 ? 0 : (numerator + denominator / 2) / denominator;
	}

	public static BufferedImage toBufferedImage(Image image) {
		int width  = image.getWidth(null);
		int height = image.getHeight(null);
		boolean transparent = image instanceof Transparency
		                      && ((Transparency) image).getTransparency() != Transparency.OPAQUE;

		BufferedImage out = new BufferedImage(width, height, transparent
		                                                     ? BufferedImage.TYPE_INT_ARGB
		                                                     : BufferedImage.TYPE_INT_RGB);

		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();

		return out;
	}

	public static BufferedImage toBufferedImage(int width, int[] data, boolean transparent) {
		int height = data.length / width;

		BufferedImage out = new BufferedImage(width, height, transparent
		                                                     ? BufferedImage.TYPE_INT_ARGB
		                                                     : BufferedImage.TYPE_INT_RGB);
		out.setAccelerationPriority(0);

		int[] buffer = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
		System.arraycopy(data, 0, buffer, 0, buffer.length);

		return out;
	}

	public static BufferedImage createByteImage(int width, int height, int pixelStride,
	                                            int colorType,
	                                            boolean hasAlpha, boolean isAlphaPre) {
		ColorModel outModel = new ComponentColorModel(
				ColorSpace.getInstance(colorType),
				hasAlpha, isAlphaPre,
				hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE,
				DataBuffer.TYPE_BYTE);

		int[] bandOffsets = new int[pixelStride];
		for (int i = 0; i < pixelStride; i++) {
			bandOffsets[i] = pixelStride - i - 1;
		}
		WritableRaster outRaster = Raster.createInterleavedRaster(
				DataBuffer.TYPE_BYTE, width, height, width * pixelStride, pixelStride,
				bandOffsets, null);

		return new BufferedImage(outModel, outRaster, isAlphaPre, null);
	}
}
