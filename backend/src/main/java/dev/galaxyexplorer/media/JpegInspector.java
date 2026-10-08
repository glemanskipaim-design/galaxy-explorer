package dev.galaxyexplorer.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Confere que os bytes sao JPEG (magic {@code FF D8 FF}) e le as dimensoes do
 * CABECALHO, sem decodificar pixels — so depois disto alguem decodifica a
 * imagem (miniatura). Puro sobre bytes.
 */
public final class JpegInspector {

    private final long maxPixels;

    public JpegInspector(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public static boolean hasJpegMagic(byte[] bytes) {
        return bytes != null && bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    /** @throws InvalidImageException se nao for JPEG legivel ou passar de {@code maxPixels} */
    public JpegInfo inspect(byte[] bytes) {
        if (!hasJpegMagic(bytes)) {
            throw new InvalidImageException("conteúdo não é JPEG (assinatura FF D8 FF ausente)");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidImageException("nenhum leitor de imagem reconhece o conteúdo");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (!"jpeg".equalsIgnoreCase(reader.getFormatName())) {
                    throw new InvalidImageException("formato " + reader.getFormatName() + ", esperado JPEG");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidImageException("dimensões inválidas no cabeçalho: " + width + "x" + height);
                }
                if ((long) width * height > maxPixels) {
                    throw new InvalidImageException("imagem de " + width + "x" + height + " passa do limite de "
                            + maxPixels + " pixels");
                }
                return new JpegInfo(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("cabeçalho JPEG ilegível: " + e.getMessage(), e);
        }
    }
}
