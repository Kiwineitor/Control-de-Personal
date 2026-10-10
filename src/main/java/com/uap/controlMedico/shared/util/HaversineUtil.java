package com.uap.controlMedico.shared.util;

import org.springframework.stereotype.Component;

@Component
public class HaversineUtil {
    
    /** Radio medio de la Tierra en metros */
    private static final double R = 6_371_000.0;

    /**
     * Calcula la distancia en metros entre dos puntos GPS.
     */
    public double calcularDistanciaMetros(
            double lat1, double lon1,
            double lat2, double lon2) {

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Retorna true si el punto está dentro del radio del lugar.
     */
    public boolean estaEnRadio(
            double latPersona, double lonPersona,
            double latCentro,  double lonCentro,
            int    radioMetros) {
        return calcularDistanciaMetros(latPersona, lonPersona, latCentro, lonCentro)
               <= radioMetros;
    }
}
