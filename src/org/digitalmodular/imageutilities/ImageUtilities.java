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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * @author Mark Jeronimus
 */
// Created 2009-04-28
// Changed 2015-08-15 added functions for AbstractImageResizer
// Changed 2015-09-08 added functions for animations
// Changed 2017-07-18 Extracted GIF loading to GIFLoader, and pulled up AnimationFrame
public enum ImageUtilities {
	;

	public static AnimationFrame[] readImage(File file) throws IOException {
		String fileName = file.getName();
		if (fileName.length() >= 5 && fileName.toUpperCase().endsWith(".GIF"))
			return GIFLoader.read(file);
		else {
			BufferedImage image = ImageIO.read(file);
			return image == null ? null : new AnimationFrame[]{new AnimationFrame(image, 1)};
		}
	}

	public static String analyzeImage(Image img) {
		if (!(img instanceof BufferedImage))
			return img.getClass().getSimpleName();

		BufferedImage image                   = (BufferedImage)img;
		int           srcDataType             = image.getRaster().getDataBuffer().getDataType();
		int           srcColorType            = getColorSpaceType(image.getColorModel().getColorSpace());
		int           numComponents           = image.getColorModel().getColorSpace().getNumComponents();
		boolean       hasAlpha                = image.getColorModel().hasAlpha();
		boolean       srcIsAlphaPremultiplied = image.getColorModel().isAlphaPremultiplied();
		boolean       srcIsSRGB               = srcColorType != ColorSpace.CS_LINEAR_RGB;

		return imageTypeName(image.getType())
		       + " / " + dataTypeName(srcDataType)
		       + " / " + numComponents + "ch"
		       + " / " + (hasAlpha ? "alpha" : "opaque")
		       + (srcIsAlphaPremultiplied ? " premultiplied" : "")
		       + " / " + (srcIsSRGB ? "sRGB" : "linearRGB");
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
		// In order of likelihood, to prevent unnecessary instantiations inside ColorSpace.
		if (ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB).equals(cs)) {
			return ColorSpace.CS_LINEAR_RGB;
		} else if (ColorSpace.getInstance(ColorSpace.CS_GRAY).equals(cs)) {
			return ColorSpace.CS_GRAY;
		} else if (ColorSpace.getInstance(ColorSpace.CS_sRGB).equals(cs)) {
			return ColorSpace.CS_sRGB;
		} else if (ColorSpace.getInstance(ColorSpace.CS_CIEXYZ).equals(cs)) {
			return ColorSpace.CS_CIEXYZ;
		} else if (ColorSpace.getInstance(ColorSpace.CS_PYCC).equals(cs)) {
			return ColorSpace.CS_PYCC;
		} else {
			return -1;
		}
	}

	public static BufferedImage createByteImage(int width, int height, int pixelStride,
	                                            int colorType,
	                                            boolean hasAlpha, boolean isAlphaPre) {
		ColorModel outModel = new ComponentColorModel(
				ColorSpace.getInstance(colorType),
				hasAlpha, isAlphaPre,
				hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE,
				DataBuffer.TYPE_BYTE);

		int[] channelOffsets = new int[pixelStride];
		for (int i = 0; i < pixelStride; i++) {
			channelOffsets[i] = pixelStride - i - 1;
		}
		WritableRaster outRaster = Raster.createInterleavedRaster(
				DataBuffer.TYPE_BYTE, width, height, width * pixelStride, pixelStride,
				channelOffsets, null);

		return new BufferedImage(outModel, outRaster, isAlphaPre, null);
	}

	public static int getBufferedImageType(int numComponents, boolean withAlpha) {
		if (numComponents == 3)
			return withAlpha ? BufferedImage.TYPE_4BYTE_ABGR : BufferedImage.TYPE_3BYTE_BGR;
		else if (numComponents == 1 && !withAlpha)
			return BufferedImage.TYPE_BYTE_GRAY;

		throw new UnsupportedOperationException("numComponents: " + numComponents + ", withAlpha: " + withAlpha);
	}

	public static BufferedImage toBufferedImage(Image image) {
		int width  = image.getWidth(null);
		int height = image.getHeight(null);
		boolean transparent = image instanceof Transparency
		                      && ((Transparency)image).getTransparency() != Transparency.OPAQUE;

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

		int[] buffer = ((DataBufferInt)out.getRaster().getDataBuffer()).getData();
		System.arraycopy(data, 0, buffer, 0, buffer.length);

		return out;
	}
}
