package dev.galaxyexplorer.catalog.position;

/**
 * Separacao angular entre dois pontos da esfera celeste (graus), pela formula
 * de Vincenty para a esfera — numericamente estavel tanto para angulos
 * pequenos (onde a lei dos cossenos perde precisao) quanto perto de 180°
 * (onde a haversine perde). Referencia: T. Vincenty, "Direct and inverse
 * solutions of geodesics on the ellipsoid…", Survey Review 23 (176), 1975, caso
 * esferico; mesma forma usada por {@code astropy.coordinates.angular_separation}.
 * Puro.
 */
public final class AngularSeparation {

    private AngularSeparation() {
    }

    public static double degrees(double ra1Deg, double dec1Deg, double ra2Deg, double dec2Deg) {
        double ra1 = Math.toRadians(ra1Deg);
        double dec1 = Math.toRadians(dec1Deg);
        double ra2 = Math.toRadians(ra2Deg);
        double dec2 = Math.toRadians(dec2Deg);
        double dRa = ra2 - ra1;
        double sinDRa = Math.sin(dRa);
        double cosDRa = Math.cos(dRa);
        double sinDec1 = Math.sin(dec1);
        double cosDec1 = Math.cos(dec1);
        double sinDec2 = Math.sin(dec2);
        double cosDec2 = Math.cos(dec2);
        double num1 = cosDec2 * sinDRa;
        double num2 = cosDec1 * sinDec2 - sinDec1 * cosDec2 * cosDRa;
        double denominator = sinDec1 * sinDec2 + cosDec1 * cosDec2 * cosDRa;
        return Math.toDegrees(Math.atan2(Math.hypot(num1, num2), denominator));
    }

    public static double arcminutes(double ra1Deg, double dec1Deg, double ra2Deg, double dec2Deg) {
        return degrees(ra1Deg, dec1Deg, ra2Deg, dec2Deg) * 60.0;
    }
}
