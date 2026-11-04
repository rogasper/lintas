package com.rogasper.lintas.shared;

/**
 * Currencies Lintas handles. {@link #scale()} is the number of decimal places that currency
 * actually uses (IDR and JPY have none), which is what forces explicit rounding decisions
 * whenever money crosses a currency boundary.
 */
public enum Currency {

    IDR(0),
    USD(2),
    SGD(2),
    EUR(2),
    JPY(0);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }

    public boolean isZeroDecimal() {
        return scale == 0;
    }
}
