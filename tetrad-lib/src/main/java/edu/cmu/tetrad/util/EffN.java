package edu.cmu.tetrad.util;

import edu.cmu.tetrad.data.DataSet;

public final class EffN {
    private EffN() {}
    public static int of(DataSet data, EffectiveSampleSizeAware maybeAware) {
        int N = data.getNumRows();
        if (maybeAware == null) return N;
        return maybeAware.getEffectiveSampleSize().orElse(N);
    }
}