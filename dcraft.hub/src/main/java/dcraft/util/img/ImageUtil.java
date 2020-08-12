package dcraft.util.img;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

public class ImageUtil {
	public static BufferedImage resize(BufferedImage img, int newW, int newH) {
		Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
		BufferedImage dimg = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
		
		Graphics2D g2d = dimg.createGraphics();
		g2d.drawImage(tmp, 0, 0, null);
		g2d.dispose();
		
		return dimg;
	}

	public static boolean createJPEG(Path input, Path output, int quality) {
		try {
			Files.deleteIfExists(output);

			ImageInputStream iis = ImageIO.createImageInputStream(input.toFile());	// needs to be File
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			ImageReader reader = readers.next();

			if (reader != null) {
				try {
					reader.setInput(iis, false);
					IIOMetadata metadata = reader.getImageMetadata(0);
					BufferedImage bi = reader.read(0);

					return createJPEG(bi, new FileImageOutputStream(output.toFile()), metadata, quality);
				}
				finally {
					reader.dispose();
				}
			}
		}
		catch (IOException x) {
		}

		return false;
	}

	public static boolean createJPEG(BufferedImage input, Path output, int quality) {
		try {
			Files.deleteIfExists(output);

			return createJPEG(input, new FileImageOutputStream(output.toFile()), null, quality);
		}
		catch (IOException x) {
			return false;
		}
	}

	public static boolean createJPEG(BufferedImage input, ImageOutputStream output, IIOMetadata metadata, int quality) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();

		if (writer != null) {
			try {
				writer.setOutput(output);

				ImageWriteParam iwParam = writer.getDefaultWriteParam();
				iwParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				iwParam.setCompressionQuality(quality / 100f);

				writer.write(null, new IIOImage(input, null, metadata), iwParam);

				return true;
			}
			catch (IOException x) {
			}
			finally {
				writer.dispose();
			}
		}

		return false;
	}

	// https://www.npmjs.com/package/imagemin basics


	// TODO support for Jimp https://www.npmjs.com/package/jimp and Sharp too https://www.npmjs.com/package/sharp
}