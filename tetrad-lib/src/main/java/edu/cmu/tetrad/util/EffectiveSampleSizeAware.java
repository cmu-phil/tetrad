package edu.cmu.tetrad.util;

import java.util.OptionalInt;

public interface EffectiveSampleSizeAware {
    void setEffectiveSampleSize(int nEff);           // nEff > 0
    OptionalInt getEffectiveSampleSize();            // empty => use raw N
}