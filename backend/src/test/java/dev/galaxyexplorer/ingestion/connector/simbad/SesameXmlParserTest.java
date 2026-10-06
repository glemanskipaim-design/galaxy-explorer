package dev.galaxyexplorer.ingestion.connector.simbad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.support.Fixtures;

/** Sobre respostas REAIS do Sesame gravadas em fixtures/sesame/. */
class SesameXmlParserTest {

    @Test
    void parsesOneTargetPerNameInOrder() {
        List<SesameTarget> targets = SesameXmlParser.parse(Fixtures.read("sesame/sample_resolve.xml"));
        assertThat(targets).extracting(SesameTarget::name).containsExactly("M31", "M1", "M42", "M45", "M87", "M13",
                "M57", "M82", "Betelgeuse", "Sgr A*", "M54", "XyzzyNotAnObject123");
        SesameTarget m31 = targets.get(0);
        assertThat(m31.oid()).isEqualTo(1575544L);
        assertThat(m31.oname()).isEqualTo("M  31");
        assertThat(m31.otype()).isEqualTo("AGN");
        assertThat(targets.get(3).oname()).isEqualTo("Cl Melotte   22"); // M45: main_id nao e o nome popular
        assertThat(targets.get(8).oname()).isEqualTo("* alf Ori");
        assertThat(targets.get(9).otype()).isEqualTo("X");
    }

    @Test
    void notFoundHasNoOid() {
        SesameTarget missing = SesameXmlParser.parse(Fixtures.read("sesame/not_found.xml")).get(0);
        assertThat(missing.found()).isFalse();
        assertThat(missing.name()).isEqualTo("XyzzyNotAnObject123");
        assertThat(missing.info()).contains("Nothing found");
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<Sesame><Target><name>&e;</name></Target></Sesame>";
        assertThatThrownBy(() -> SesameXmlParser.parse(xxe)).isInstanceOf(SourceQueryException.class);
    }

    @Test
    void rejectsNonSesameDocument() {
        assertThatThrownBy(() -> SesameXmlParser.parse(Fixtures.read("simbad/error_bad_adql.xml")))
                .isInstanceOf(SourceQueryException.class).hasMessageContaining("<Sesame>");
        assertThatThrownBy(() -> SesameXmlParser.parse("not xml")).isInstanceOf(SourceQueryException.class);
    }

    @Test
    void urlEncodesEachNameAndJoinsWithAmpersand() {
        URI base = URI.create("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S");
        assertThat(SesameUrls.build(base, List.of("M31", "Sgr A*", "Barnard's Star")).toString())
                .isEqualTo("https://cds.unistra.fr/cgi-bin/nph-sesame/-oxp/S?M31&Sgr%20A*&Barnard%27s%20Star");
        // um '&' no nome nao pode virar separador
        assertThat(SesameUrls.build(base, List.of("A&B")).toString()).endsWith("?A%26B");
    }
}
