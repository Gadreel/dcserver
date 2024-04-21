package com.samstevens.totp.qr;

import com.nayuki.qrcodegen.QrCode;
import com.samstevens.totp.exceptions.QrGenerationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NayukiSvgQrGenerator implements QrGenerator {
    @Override
    public String getImageMimeType() {
        return "image/svg";
    }

    @Override
    public byte[] generate(QrData data) throws QrGenerationException, IOException {
        QrCode qr0 = QrCode.encodeText(data.getUri(), QrCode.Ecc.MEDIUM);
        String svg = toSvg(qr0, 4);  // See QrCodeGeneratorDemo

        return svg.getBytes(StandardCharsets.UTF_8);
    }

    public String generateSvg(QrData data) throws QrGenerationException, IOException {
        QrCode qr0 = QrCode.encodeText(data.getUri(), QrCode.Ecc.MEDIUM);
        return toSvg(qr0, 4);  // See QrCodeGeneratorDemo
    }

    private static String toSvg(QrCode qr, int border) {
        return toSvg(qr, border, "#FFFFFF", "#000000");
    }

    /**
     * Returns a string of SVG code for an image depicting the specified QR Code, with the specified
     * number of border modules. The string always uses Unix newlines (\n), regardless of the platform.
     * @param qr the QR Code to render (not {@code null})
     * @param border the number of border modules to add, which must be non-negative
     * @param lightColor the color to use for light modules, in any format supported by CSS, not {@code null}
     * @param darkColor the color to use for dark modules, in any format supported by CSS, not {@code null}
     * @return a string representing the QR Code as an SVG XML document
     * @throws NullPointerException if any object is {@code null}
     * @throws IllegalArgumentException if the border is negative
     */
    private static String toSvg(QrCode qr, int border, String lightColor, String darkColor) {
        if (qr == null)
            throw new IllegalArgumentException("Missing qr code");

        if (border < 0)
            throw new IllegalArgumentException("Border must be non-negative");

        long brd = border;
        StringBuilder sb = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n")
                .append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\" viewBox=\"0 0 %1$d %1$d\" stroke=\"none\">\n",
                        qr.size + brd * 2))
                .append("\t<rect width=\"100%\" height=\"100%\" fill=\"" + lightColor + "\"/>\n")
                .append("\t<path d=\"");
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    if (x != 0 || y != 0)
                        sb.append(" ");
                    sb.append(String.format("M%d,%dh1v1h-1z", x + brd, y + brd));
                }
            }
        }
        return sb
                .append("\" fill=\"" + darkColor + "\"/>\n")
                .append("</svg>\n")
                .toString();
    }
}
