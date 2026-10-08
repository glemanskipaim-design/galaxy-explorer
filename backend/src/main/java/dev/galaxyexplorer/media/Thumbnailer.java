package dev.galaxyexplorer.media;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Gera a miniatura JPEG de um JPEG ja aprovado pelo {@link JpegInspector}.
 * Reducao em etapas (metade por vez) com interpolacao bicubica — reduzir de uma
 * vez so serrilha. Qualidade fixa {@value #QUALITY}. Nenhum metadado do original
 * e copiado (a miniatura e derivada; o original e a imagem da fonte).
 */
public final class Thumbnailer {

    static final float QUALITY = 0.85f;

    private final int maxSize;

    /** @param maxSize maior lado da miniatura, em px */
    public Thumbnailer(int maxSize) {
        this.maxSize = maxSize;
    }

    public byte[] thumbnail(byte[] jpeg) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException e) {
            throw new InvalidImageException("JPEG não decodificável: " + e.getMessage(), e);
        }
        if (source == null) {
            throw new InvalidImageException("JPEG não decodificável");
        }
        double scale = Math.min(1.0, (double) maxSize / Math.max(source.getWidth(), source.getHeight()));
        int targetW = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage current = source;
        while (current.getWidth() / 2 >= targetW && current.getHeight() / 2 >= targetH) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH
                || current.getType() != BufferedImage.TYPE_INT_RGB) {
            current = resize(current, targetW, targetH);
        }
        return encode(current);
    }

    private static BufferedImage resize(BufferedImage image, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new InvalidImageException("falha ao codificar a miniatura: " + e.getMessage(), e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
