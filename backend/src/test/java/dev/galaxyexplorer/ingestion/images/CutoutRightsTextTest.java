package dev.galaxyexplorer.ingestion.images;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsPropertiesParser;
import dev.galaxyexplorer.ingestion.connector.hips2fits.HipsSurvey;
import dev.galaxyexplorer.support.Fixtures;

/** rights_text do recorte, a partir dos arquivos {@code properties} reais. */
class CutoutRightsTextTest {

    static final HipsSurvey DSS2 = new HipsSurvey("DSS2_COLOR", "CDS/P/DSS2/color",
            URI.create("https://alasky.cds.unistra.fr/DSS/DSSColor/properties"), "DSS2 via CDS", -90, 10);

    @Test
    void namesTheLicensorAndTheLicense() {
        String props = Fixtures.read("hips2fits/dss2_color.properties");
        assertThat(CutoutIngestion.rightsText(HipsPropertiesParser.toMetadata(HipsPropertiesParser.parse(props), DSS2)))
                .isEqualTo("HiPS © CNRS/Unistra — ODbL-1.0");
    }

    @Test
    void withoutHipsCopyrightSaysSoInsteadOfPresumingALicensor() {
        // alteracao em memoria declarada: remove a linha hips_copyright do arquivo real
        String props = Fixtures.read("hips2fits/dss2_color.properties").replaceAll("(?m)^hips_copyright.*$", "");
        assertThat(CutoutIngestion.rightsText(HipsPropertiesParser.toMetadata(HipsPropertiesParser.parse(props), DSS2)))
                .isEqualTo("HiPS sob ODbL-1.0 (properties sem hips_copyright)");
    }
}
