package edu.cmu.tetrad.search.test.ffci_utils;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.test.RowsView;
import org.ejml.simple.SimpleMatrix;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class FfCiState {
    final DataSet data;
    final RowsView rowsView; // handles activeRowIndex + nActive
    final ConcurrentHashMap<String, SimpleMatrix> featureCache = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Object> solverCache = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Double> sigmaCache = new ConcurrentHashMap<>();
    final Random rng; // base rng, but engine should derive per-call rng from config.seed + key

    public FfCiState(DataSet data, RowsView rowsView, Random rng) {
        this.data = data;
        this.rowsView = rowsView;
        this.rng = rng;
    }

}