package com.samstevens.totp.util;

import com.samstevens.totp.code.DefaultCodeGenerator;
import com.samstevens.totp.code.DefaultCodeVerifier;
import com.samstevens.totp.code.HashingAlgorithm;
import com.samstevens.totp.exceptions.CodeGenerationException;
import com.samstevens.totp.exceptions.QrGenerationException;
import com.samstevens.totp.qr.NayukiSvgQrGenerator;
import com.samstevens.totp.qr.QrData;
import com.samstevens.totp.qr.ZxingPngQrGenerator;
import com.samstevens.totp.secret.DefaultSecretGenerator;
import com.samstevens.totp.time.SystemTimeProvider;
import com.samstevens.totp.time.TimeProvider;
import org.apache.commons.codec.binary.Base64;

import java.io.IOException;

public class TotpUtil {
    // Class not meant to be instantiated
    private TotpUtil() {
    }

    /**
     * Given the raw data of an image and the mime type, returns
     * a data URI string representing the image for embedding in
     * HTML/CSS.
     *
     * @param data The raw bytes of the image.
     * @param mimeType The mime type of the image.
     * @return The data URI string representing the image.
     */
    public static String getDataUriForImage(byte[] data, String mimeType) {
        Base64 base64Codec = new Base64();

        String encodedData = new String(base64Codec.encode(data));

        return String.format("data:%s;base64,%s", mimeType, encodedData);
    }

    static public String generateSecret() {
        DefaultSecretGenerator generator = new DefaultSecretGenerator();
        return generator.generate();
    }

    static public String generateAuthCode(String secret) throws CodeGenerationException {
        long time = new SystemTimeProvider().getTime();

        long currentBucket = Math.floorDiv(time, 30);

        DefaultCodeGenerator g = new DefaultCodeGenerator();
        return g.generate(secret, currentBucket);
    }

    static public boolean isValidAuthCode(String secret, String code) {
        TimeProvider timeProvider = new SystemTimeProvider();

        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);
        verifier.setAllowedTimePeriodDiscrepancy(1);

        return verifier.isValidCode(secret, code);
    }

    static public String generateQRCode(String secret, String label, String issuer) throws QrGenerationException, IOException {
        QrData data = new QrData.Builder()
                .label(label)
                .secret(secret)
                .issuer(issuer)
                .digits(6)
                .period(30)
                .build();

        NayukiSvgQrGenerator generator = new NayukiSvgQrGenerator();
        return generator.generateSvg(data);
    }
}
