package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.Map;
import java.util.Optional;

/**
 * Unidades do SIMBAD -> unidades canonicas nossas. Puro. Unidade desconhecida
 * nunca e convertida "no chute".
 */
public final class SimbadUnits {

    /**
     * Grafias de {@code mesDistance.unit} observadas ao vivo (vem com padding:
     * {@code 'pc  '}, {@code 'kpc '}, {@code 'Mpc '}; o trim e feito antes).
     */
    private static final Map<String, Double> PARSECS_PER_UNIT = Map.of(
            "pc", 1.0,
            "kpc", 1.0e3,
            "Mpc", 1.0e6);

    /** Grafia VOUnit do {@code metadata.unit} do TAP -> unidade canonica de {@code property_definition}. */
    private static final Map<String, String> CANONICAL = Map.of(
            "deg", "deg",
            "mas", "mas",
            "km.s-1", "km/s",
            "arcmin", "arcmin",
            "pc", "pc");

    private SimbadUnits() {
    }

    /** @return vazio se a unidade for desconhecida (vira {@code issue}, valor descartado) */
    public static Optional<Double> distanceToParsecs(double value, String unit) {
        if (unit == null) {
            return Optional.empty();
        }
        Double factor = PARSECS_PER_UNIT.get(unit.trim());
        return factor == null ? Optional.empty() : Optional.of(value * factor);
    }

    /** @throws IllegalArgumentException grafia desconhecida — e bug de mapeamento, nao dado */
    public static String canonicalUnitFor(String sourceUnit) {
        String canonical = CANONICAL.get(sourceUnit);
        if (canonical == null) {
            throw new IllegalArgumentException("unidade SIMBAD sem traducao canonica: '" + sourceUnit + "'");
        }
        return canonical;
    }
}
