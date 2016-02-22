package edu.cmu.tetrad.search;

/**
 * Created by jdramsey on 2/21/16.
 */
public interface IBDeuScore extends FgsScore {
    double getStructurePrior();

    double getSamplePrior();

    void setStructurePrior(double structurePrior);

    void setSamplePrior(double samplePrior);
}
