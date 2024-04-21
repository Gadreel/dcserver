package com.samstevens.totp.qr;

/*
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

 */
import com.nayuki.qrcodegen.QrCode;
import com.samstevens.totp.exceptions.QrGenerationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class NayukiPngQrGenerator implements QrGenerator {
    private int imageSize = 512;

    /*
    private final Writer writer;

    public ZxingPngQrGenerator() {
        this(new QRCodeWriter());
    }

    public ZxingPngQrGenerator(Writer writer) {
        this.writer = writer;
    }

    @Override
    public byte[] generate(QrData data) throws QrGenerationException {
        try {
            BitMatrix bitMatrix = writer.encode(data.getUri(), BarcodeFormat.QR_CODE, imageSize, imageSize);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            throw new QrGenerationException("Failed to generate QR code. See nested exception.", e);
        }
    }

     */

    public void setImageSize(int imageSize) {
        this.imageSize = imageSize;
    }

    public int getImageSize() {
        return imageSize;
    }

    @Override
    public String getImageMimeType() {
        return "image/png";
    }

    @Override
    public byte[] generate(QrData data) throws QrGenerationException, IOException {
        QrCode qr0 = QrCode.encodeText(data.getUri(), QrCode.Ecc.MEDIUM);
        BufferedImage img = toImage(qr0, 4, 10);  // See QrCodeGeneratorDemo

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngOutputStream);
        return pngOutputStream.toByteArray();
    }

    private static BufferedImage toImage(QrCode qr, int scale, int border) {
        return toImage(qr, scale, border, 0xFFFFFF, 0x000000);
    }

    /**
     * Returns a raster image depicting the specified QR Code, with
     * the specified module scale, border modules, and module colors.
     * <p>For example, scale=10 and border=4 means to pad the QR Code with 4 light border
     * modules on all four sides, and use 10&#xD7;10 pixels to represent each module.
     * @param qr the QR Code to render (not {@code null})
     * @param scale the side length (measured in pixels, must be positive) of each module
     * @param border the number of border modules to add, which must be non-negative
     * @param lightColor the color to use for light modules, in 0xRRGGBB format
     * @param darkColor the color to use for dark modules, in 0xRRGGBB format
     * @return a new image representing the QR Code, with padding and scaling
     * @throws NullPointerException if the QR Code is {@code null}
     * @throws IllegalArgumentException if the scale or border is out of range, or if
     * {scale, border, size} cause the image dimensions to exceed Integer.MAX_VALUE
     */
    private static BufferedImage toImage(QrCode qr, int scale, int border, int lightColor, int darkColor) {
        if (qr == null)
            throw new IllegalArgumentException("Missing qr code");
        if (scale <= 0 || border < 0)
            throw new IllegalArgumentException("Value out of range");
        if (border > Integer.MAX_VALUE / 2 || qr.size + border * 2L > Integer.MAX_VALUE / scale)
            throw new IllegalArgumentException("Scale or border too large");

        BufferedImage result = new BufferedImage((qr.size + border * 2) * scale, (qr.size + border * 2) * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                boolean color = qr.getModule(x / scale - border, y / scale - border);
                result.setRGB(x, y, color ? darkColor : lightColor);
            }
        }
        return result;
    }
}
