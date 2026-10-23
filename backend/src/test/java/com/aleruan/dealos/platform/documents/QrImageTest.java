package com.aleruan.dealos.platform.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QrImageTest {

    @Test
    void encodedPaymentCanBeReadBack() throws Exception {
        String text = PaymentQr.epc("BPKOPLPW", "Aleruan-industry s.r.o.", "PL23102010130000020204812139",
                new BigDecimal("16717.36"), "FV-2026-0001 Fasáda");
        BufferedImage img = QrImage.image(text, 360);
        int[] pixels = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        String read = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(img.getWidth(), img.getHeight(), pixels)))).getText();
        assertEquals(text, read);
        assertTrue(QrImage.dataUri(text).startsWith("data:image/png;base64,iVBOR"));
    }
}
