/*
 * Copyright 2013, Morten Nobel-Joergensen
 *
 * License: The BSD 3-Clause License
 * http://opensource.org/licenses/BSD-3-Clause
 */
package old;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * @author Heinz Doerr
 * @author Morten Nobel-Joergensen
 */
public class ImageUtilsOld {

	/**
	 *
	 * returns one row (height == 1) of byte packed image data in BGR or AGBR form
	 *
	 * @param img
	 * @param y
	 * @param w
	 * @param array
	 * @param temp must be either null or a array with length of w*h
	 * @return
	 */
	public static byte[] getPixelsBGR(BufferedImage img, int y, int w, byte[] array, int[] temp) {
		final int x = 0;
		final int h = 1;

		assert array.length == temp.length * img.getRaster().getNumBands();
		assert (temp.length == w);

		int imageType = img.getType();
		Raster raster;
		switch (imageType) {
			case BufferedImage.TYPE_3BYTE_BGR:
			case BufferedImage.TYPE_4BYTE_ABGR:
			case BufferedImage.TYPE_4BYTE_ABGR_PRE:
			case BufferedImage.TYPE_BYTE_GRAY:
				raster = img.getRaster();
				raster.getDataElements(x, y, w, h, array);
				break;
			case BufferedImage.TYPE_INT_BGR:
				raster = img.getRaster();
				raster.getDataElements(x, y, w, h, temp);
				ints2bytes(temp, array, 0, 1, 2);  // BGR --> BGR
				break;
			case BufferedImage.TYPE_INT_RGB:
				raster = img.getRaster();
				raster.getDataElements(x, y, w, h, temp);
				ints2bytes(temp, array, 2, 1, 0);  // RGB --> BGR
				break;
			case BufferedImage.TYPE_INT_ARGB:
			case BufferedImage.TYPE_INT_ARGB_PRE:
				raster = img.getRaster();
				raster.getDataElements(x, y, w, h, temp);
				ints2bytes(temp, array, 2, 1, 0, 3);  // ARGB --> ABGR
				break;
			case BufferedImage.TYPE_CUSTOM: // TODO: works for my icon image loader, but else ???
				img.getRGB(x, y, w, h, temp, 0, w);
				ints2bytes(temp, array, 2, 1, 0, 3);  // ARGB --> ABGR
				break;
			default:
				img.getRGB(x, y, w, h, temp, 0, w);
				ints2bytes(temp, array, 2, 1, 0);  // RGB --> BGR
				break;
		}

		return array;
	}

	/**
	 * Converts and copies byte packed BGR or ABGR into the {@code img} buffer, the image type may vary (e.g.
	 * RGB or BGR, int or byte packed) but the number of components (w/o alpha, w alpha, gray) must match
	 *
	 * does not unmanage the image for all (A)RGB and (A)BGR and gray imaged
	 *
	 * @param BGRPixels
	 * @param img
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 */
	public static void setBGRPixels(byte[] BGRPixels, BufferedImage img, int x, int y, int w, int h) {
		int imageType = img.getType();
		WritableRaster raster = img.getRaster();
		if (imageType == BufferedImage.TYPE_3BYTE_BGR ||
		    imageType == BufferedImage.TYPE_4BYTE_ABGR ||
		    imageType == BufferedImage.TYPE_4BYTE_ABGR_PRE ||
		    imageType == BufferedImage.TYPE_BYTE_GRAY) {
			raster.setDataElements(x, y, w, h, BGRPixels);
		} else {
			int[] pixels;
			if (imageType == BufferedImage.TYPE_INT_BGR) {
				pixels = bytes2int(BGRPixels, 2, 1, 0);  // BGR --> BGR
			} else if (imageType == BufferedImage.TYPE_INT_ARGB ||
			           imageType == BufferedImage.TYPE_INT_ARGB_PRE) {
				pixels = bytes2int(BGRPixels, 3, 0, 1, 2);  // ABGR --> ARGB
			} else {
				pixels = bytes2int(BGRPixels, 0, 1, 2);  // BGR --> RGB
			}
			if (w == 0 || h == 0) {
				return;
			} else if (pixels.length < w * h) {
				throw new IllegalArgumentException("pixels array must have a length" + " >= w*h");
			}
			if (imageType == BufferedImage.TYPE_INT_ARGB ||
			    imageType == BufferedImage.TYPE_INT_RGB ||
			    imageType == BufferedImage.TYPE_INT_ARGB_PRE ||
			    imageType == BufferedImage.TYPE_INT_BGR) {
				raster.setDataElements(x, y, w, h, pixels);
			} else {
				// Unmanages the image
				img.setRGB(x, y, w, h, pixels, 0, w);
			}
		}
	}

	public static void ints2bytes(int[] in, byte[] out, int index1, int index2, int index3) {
		for (int i = 0; i < in.length; i++) {
			int index = i * 3;
			int value = in[i];
			out[index + index1] = (byte)value;
			value = value >> 8;
			out[index + index2] = (byte)value;
			value = value >> 8;
			out[index + index3] = (byte)value;
		}
	}

	public static void ints2bytes(int[] in, byte[] out, int index1, int index2, int index3, int index4) {
		for (int i = 0; i < in.length; i++) {
			int index = i * 4;
			int value = in[i];
			out[index + index1] = (byte)value;
			value = value >> 8;
			out[index + index2] = (byte)value;
			value = value >> 8;
			out[index + index3] = (byte)value;
			value = value >> 8;
			out[index + index4] = (byte)value;
		}
	}

	public static int[] bytes2int(byte[] in, int index1, int index2, int index3) {
		int[] out = new int[in.length / 3];
		for (int i = 0; i < out.length; i++) {
			int index = i * 3;
			int b1 = (in[index + index1] & 0xff) << 16;
			int b2 = (in[index + index2] & 0xff) << 8;
			int b3 = in[index + index3] & 0xff;
			out[i] = b1 | b2 | b3;
		}
		return out;
	}

	public static int[] bytes2int(byte[] in, int index1, int index2, int index3, int index4) {
		int[] out = new int[in.length / 4];
		for (int i = 0; i < out.length; i++) {
			int index = i * 4;
			int b1 = (in[index + index1] & 0xff) << 24;
			int b2 = (in[index + index2] & 0xff) << 16;
			int b3 = (in[index + index3] & 0xff) << 8;
			int b4 = in[index + index4] & 0xff;
			out[i] = b1 | b2 | b3 | b4;
		}
		return out;
	}

	public static BufferedImage convert(BufferedImage src, int bufImgType) {
		BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), bufImgType);
		Graphics2D g2d = img.createGraphics();
		g2d.drawImage(src, 0, 0, null);
		g2d.dispose();
		return img;
	}

	/**
	 * Copy JPEG meta data (EXIF) from source to destination and save it to out.
	 *
	 * @param source
	 * @param destination
	 * @return result
	 * @throws IOException
	 */
	public static byte[] copyJpegMetaData(byte[] source, byte[] destination) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
			copyJpegMetaData(new ByteArrayInputStream(source), new ByteArrayInputStream(destination), out);
			return baos.toByteArray();
		}
	}

	/**
	 * Copy JPEG meta data (EXIF) from source to destination and save it to out
	 *
	 * @param source
	 * @param destination
	 * @param out
	 * @throws IOException
	 */
	public static void copyJpegMetaData(InputStream source,
	                                    InputStream destination,
	                                    ImageOutputStream out) throws IOException {
		// Read meta data from source image
		Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
		ImageReader reader = readers.next();

		IIOMetadata metadata;
		try (ImageInputStream iis = new MemoryCacheImageInputStream(source)) {
			reader.setInput(iis);
			metadata = reader.getImageMetadata(0);
		}

		// Read destination image
		IIOImage image;
		try (ImageInputStream outIis = new MemoryCacheImageInputStream(destination)) {
			reader.setInput(outIis);
			image = reader.readAll(0, null);
			image.setMetadata(metadata);
		}

		// Write destination image
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		ImageWriter writer = writers.next();
		writer.setOutput(out);
		writer.write(image);
	}
}
