package edu.cmu.tetrad.sem;

// wherever DagMetricResult lives (you used it as a record)
public record DagMetricResult(String name, double value, String note, Better better) {

    public enum Better { HIGHER, LOWER, NA }

    // Back-compat ctor (defaults to NA if old call sites exist)
    public DagMetricResult(String name, double value, String note) {
        this(name, value, note, Better.NA);
    }
}