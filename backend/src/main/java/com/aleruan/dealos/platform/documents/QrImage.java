package com.aleruan.dealos.platform.documents;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;

/** QR code as a PNG data URI for document templates. */
public final class QrImage {

    private QrImage() {
    }

    public static BitMatrix matrix(String text, int size) {
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, Map.of(
                    EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Cannot encode a QR code", e);
        }
    }

    public static BufferedImage image(String text, int size) {
        BitMatrix m = matrix(text, size);
        BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }

    public static String dataUri(String text) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image(text, 360), "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
