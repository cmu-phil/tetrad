package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

// Not working yet.
public final class PythonKciIndependenceTest implements IndependenceTest, Closeable {

    private final DataSet data;
    private final List<Node> vars;
    private final Map<String, Integer> nameToIndex;

    private final PythonCiService service;

    private volatile double alpha = 0.01;
    private volatile boolean verbose;

    // Any KCI parameters you want to forward
    private final Map<String, Object> kciParams = new HashMap<>();

    public PythonKciIndependenceTest(DataSet data, PythonCiService service) {
        this.data = Objects.requireNonNull(data, "data");
        this.service = Objects.requireNonNull(service, "service");

        this.vars = Collections.unmodifiableList(new ArrayList<>(data.getVariables()));
        this.nameToIndex = new HashMap<>();
        for (int i = 0; i < vars.size(); i++) {
            nameToIndex.put(vars.get(i).getName(), i);
        }

        // initialize remote side once
        service.initializeIfNeeded(data, vars, kciParams);
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        int xi = idx(x);
        int yi = idx(y);

        int[] zi = z == null || z.isEmpty()
                ? new int[0]
                : z.stream().mapToInt(this::idx).sorted().toArray();

        double p = service.pValue(xi, yi, zi, alpha);

        // Your convention: score = alpha - p  (positive means "dependence holds" when used as score-as-test)
        return new IndependenceResult(new IndependenceFact(x, y, z), p > alpha, p, alpha - p);
    }

    private int idx(Node n) {
        Integer i = nameToIndex.get(n.getName());
        if (i == null) {
            throw new IllegalArgumentException("Unknown variable: " + n.getName());
        }
        return i;
    }

    @Override
    public List<Node> getVariables() {
        return vars;
    }

    @Override
    public DataModel getData() {
        return data;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        service.setVerbose(verbose);
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void close() {
        try {
            service.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "KCI (Python / causal-learn)";
    }

    // Optional: allow setting KCI-specific parameters via your Parameters plumbing
    public void setKciParam(String key, Object value) {
        kciParams.put(key, value);
        service.updateParams(kciParams);
    }
}