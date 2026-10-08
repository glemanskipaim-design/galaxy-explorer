package dev.galaxyexplorer.ingestion.images;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.galaxyexplorer.catalog.image.HeroCandidate;
import dev.galaxyexplorer.catalog.image.HeroPolicy;
import dev.galaxyexplorer.catalog.image.ImageLicense;
import dev.galaxyexplorer.catalog.image.ImageLicenseRepository;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.catalog.image.ObjectImageRow;
import dev.galaxyexplorer.catalog.image.ObjectImageWrite;
import dev.galaxyexplorer.catalog.source.SourceRepository;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.image.DownloadedImage;
import dev.galaxyexplorer.ingestion.connector.image.ImageCandidate;
import dev.galaxyexplorer.ingestion.connector.image.ImageTarget;
import dev.galaxyexplorer.ingestion.pipeline.RawPayloadRepository;
import dev.galaxyexplorer.media.ImageFileStore;
import dev.galaxyexplorer.media.JpegInfo;
import dev.galaxyexplorer.media.JpegInspector;
import dev.galaxyexplorer.media.StoredFile;
import dev.galaxyexplorer.media.Thumbnailer;

/**
 * Grava uma imagem aceita. Fronteira:
 * <ul>
 *   <li>fora de transacao: {@link JpegInspector} (dimensoes pelo cabecalho, limite de pixels), arquivos
 *       (original e miniatura) e o {@code raw_payload} do download — existem mesmo se o resto falhar;</li>
 *   <li>uma transacao por objeto: upsert da linha + reordenacao/hero pela {@link HeroPolicy} sobre TODAS as
 *       imagens do objeto.</li>
 * </ul>
 * Arquivo sem linha (transacao falhou) e orfao inofensivo: enderecado por conteudo.
 */
@Component
public class ImageWriter {

    private final JpegInspector inspector;
    private final Thumbnailer thumbnailer;
    private final ImageFileStore files;
    private final RawPayloadRepository rawPayloads;
    private final ObjectImageRepository images;
    private final ImageLicenseRepository licenses;
    private final SourceRepository sources;
    private final TransactionTemplate tx;

    public ImageWriter(JpegInspector inspector, Thumbnailer thumbnailer, ImageFileStore files,
                       RawPayloadRepository rawPayloads, ObjectImageRepository images, ImageLicenseRepository licenses,
                       SourceRepository sources, TransactionTemplate tx) {
        this.inspector = inspector;
        this.thumbnailer = thumbnailer;
        this.files = files;
        this.rawPayloads = rawPayloads;
        this.images = images;
        this.licenses = licenses;
        this.sources = sources;
        this.tx = tx;
    }

    /**
     * @param metadataPayloadId payload de onde vieram credito/licenca (AVM ou properties); pode ser {@code null}
     * @param downloadDataset   {@code hips2fits_cutout} / {@code esa_screen_jpg}
     * @return id da linha em {@code object_image}
     * @throws dev.galaxyexplorer.media.InvalidImageException bytes nao aceitos como JPEG
     * @throws SourceQueryException licenca fora de {@code image_license}
     */
    public long write(ImageTarget target, ImageCandidate c, DownloadedImage d, long runId, Long metadataPayloadId,
                      String downloadDataset) {
        ImageLicense license = licenses.findByCode(c.licenseCode()).orElseThrow(() -> new SourceQueryException(
                "licença '" + c.licenseCode() + "' fora da tabela image_license — imagem não gravada"));
        JpegInfo info = inspector.inspect(d.bytes());
        StoredFile display = files.store(d.bytes());
        byte[] thumbBytes = thumbnailer.thumbnail(d.bytes());
        JpegInfo thumbInfo = inspector.inspect(thumbBytes);
        StoredFile thumb = files.store(thumbBytes);
        long downloadPayloadId = rawPayloads.insert(runId, RawPayload.ofBinary(downloadDataset, d.requestUrl(),
                c.sourceImageId(), d.contentType(), d.bytes().length, display.sha256(), display.storageKey(),
                d.retrievedAt()));
        ObjectImageWrite w = new ObjectImageWrite(c.kind(), c.sourceImageId(), runId, metadataPayloadId,
                downloadPayloadId, d.requestUrl(), c.pageUrl(), c.provider(), c.providerShort(), c.creditVerbatim(),
                c.mission(), c.instrument(), c.wavelength(), c.title(), c.rightsText(), license.code(), license.url(),
                c.termsUrl(), c.releaseDate(), c.centerRaDeg(), c.centerDecDeg(), c.fovDeg(), c.fovBasis(),
                c.coverageRatio(), c.separationArcmin(), c.derivedFromMeasurementId(), c.matchBasis(),
                c.curated() ? "manual" : "automatic", c.curatedHero(), display, info.width(), info.height(), thumb,
                thumbInfo.width(), thumbInfo.height(), d.retrievedAt());
        short sourceId = sources.getByCode(c.source()).id();
        Long id = tx.execute(status -> {
            long imageId = images.upsert(target.objectId(), sourceId, w);
            rerank(target.objectId());
            return imageId;
        });
        return id;
    }

    /**
     * Fecha uma fonte para o objeto: remove as imagens desta fonte/{@code curatedBy} fora de {@code keepIds}
     * e reaplica a ordem/hero.
     */
    public void finishSource(UUID objectId, short sourceId, Set<String> keepIds, String curatedBy) {
        tx.executeWithoutResult(status -> {
            images.deleteForSourceExcept(objectId, sourceId, keepIds, curatedBy);
            rerank(objectId);
        });
    }

    private void rerank(UUID objectId) {
        List<HeroCandidate> candidates = images.findByObject(objectId).stream().map(ObjectImageRow::toHeroCandidate)
                .toList();
        List<Long> ordered = HeroPolicy.order(candidates).stream().map(HeroCandidate::id).toList();
        images.applyRanking(objectId, ordered, HeroPolicy.choose(candidates).orElse(null));
    }
}
