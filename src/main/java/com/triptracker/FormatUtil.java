package com.triptracker;

import java.text.DecimalFormat;

public final class FormatUtil {

    private FormatUtil() {
        // Utility class, not instantiable
    }

    /**
     * Shortens a number for display (e.g., 15000 -> "15k", 2500000 -> "2.5m").
     */
    public static String shortenNumber(long number) {
        if (number >= 1000000000) {
            DecimalFormat df = new DecimalFormat("#.###");
            return df.format(number / 1000000000.0) + "b";
        } else if (number >= 1000000) {
            DecimalFormat df = new DecimalFormat("#.##");
            return df.format(number / 1000000.0) + "m";
        } else if (number >= 10000) {
            DecimalFormat df = new DecimalFormat("#.#");
            return df.format(number / 1000.0) + "k";
        }
        return String.valueOf(number);
    }
}
