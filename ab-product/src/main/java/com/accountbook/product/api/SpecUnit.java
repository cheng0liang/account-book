package com.accountbook.product.api;

import java.util.Arrays;
import java.util.Optional;

/** Spec units and their conversion to a base unit (g / ml / piece) so prices can be compared per standard unit. */
public enum SpecUnit {
    G("g", Dimension.WEIGHT, 1),
    KG("kg", Dimension.WEIGHT, 1000),
    ML("ml", Dimension.VOLUME, 1),
    L("l", Dimension.VOLUME, 1000),
    PCS("pcs", Dimension.COUNT, 1);

    public enum Dimension { WEIGHT, VOLUME, COUNT }

    private final String symbol;
    private final Dimension dimension;
    private final double toBase;

    SpecUnit(String symbol, Dimension dimension, double toBase) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.toBase = toBase;
    }

    public String symbol() {
        return symbol;
    }

    public Dimension dimension() {
        return dimension;
    }

    public double toBase(double value) {
        return value * toBase;
    }

    public static Optional<SpecUnit> parse(String symbol) {
        return symbol == null ? Optional.empty()
                : Arrays.stream(values()).filter(u -> u.symbol.equalsIgnoreCase(symbol.strip())).findFirst();
    }
}
