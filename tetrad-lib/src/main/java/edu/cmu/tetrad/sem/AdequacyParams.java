package edu.cmu.tetrad.sem;

public final class AdequacyParams {

    public double holdoutFrac = 0.2;
    public int minHoldoutRows = 20;

    public int mmdFeatures = 512;
    public int mmdMaxRows = 2000;
    public long mmdSeed = 253213L;

    public AdequacyParams() {}
}