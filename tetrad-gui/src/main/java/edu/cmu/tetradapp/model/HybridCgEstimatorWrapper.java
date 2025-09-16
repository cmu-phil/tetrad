/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Wraps the Hybrid CG MLE estimator for use in the Tetrad application.
 *
 * <p>Behavior mirrors {@code BayesEstimatorWrapper}:
 * <ul>
 *   <li>Consumes one or more DataSet(s) from a {@link DataWrapper}.</li>
 *   <li>For each dataset, estimates a {@link HybridCgIm} from the provided {@link HybridCgPm}.</li>
 *   <li>Keeps a list of IMs parallel to the dataset list; exposes model index switching, etc.</li>
 * </ul>
 *
 * <p><b>Parameters</b> (optional; with defaults):
 * <ul>
 *   <li>{@code hybridcg.alpha} (double, default 1.0): Dirichlet pseudo-count for discrete CPT rows.</li>
 *   <li>{@code hybridcg.shareVariance} (boolean, default false): share one variance across strata per continuous child.</li>
 *   <li>{@code hybridcg.binPolicy} (string: {@code equal_frequency} | {@code equal_interval} | {@code none}; default {@code equal_frequency})</li>
 *   <li>{@code hybridcg.bins} (int, default 3): number of bins if binPolicy != none.</li>
 *   <li>{@code hybridcg.defaultBins} (int, default 3), {@code hybridcg.defaultRangeLow} (double, default -1.0),
 *       {@code hybridcg.defaultRangeHigh} (double, default 1.0): fallback cutpoints when data not usable.</li>
 * </ul>
 */
public class HybridCgEstimatorWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 42L;

    private final DataWrapper dataWrapper;
    private final Parameters parameters;

    private final List<HybridCgIm> hybridIms = new ArrayList<>();
    private HybridCgIm hybridIm;
    private DataSet dataSet;

    private String name = "Hybrid CG Estimator";
    private int numModels = 0;
    private int modelIndex = 0;

    // ============================== CONSTRUCTORS ==============================

    /** Convenience: build from a Simulation like Bayes does. */
    public HybridCgEstimatorWrapper(Simulation simulation,
                                    HybridCgPmWrapper pmWrapper,
                                    Parameters parameters) {
        this(new DataWrapper(simulation, parameters), pmWrapper, parameters);
    }

    public HybridCgEstimatorWrapper(DataWrapper dataWrapper,
                                    HybridCgPmWrapper pmWrapper,
                                    Parameters parameters) {
        if (dataWrapper == null) throw new NullPointerException("DataWrapper must not be null.");
        if (pmWrapper == null) throw new NullPointerException("HybridCgPmWrapper must not be null.");

        this.dataWrapper = dataWrapper;
        this.parameters = parameters == null ? new Parameters() : parameters;

        DataModelList dml = dataWrapper.getDataModelList();
        if (dml == null || dml.size() == 0) {
            throw new IllegalArgumentException("Data must be a non-empty list of data sets.");
        }

        for (int i = 0; i < dml.size(); i++) {
            DataModel dm = dml.get(i);
            if (!(dm instanceof DataSet ds)) {
                throw new IllegalArgumentException("All entries must be DataSet instances (mixed or discrete/continuous).");
            }

            // Fetch the PM from the wrapper and make a defensive copy for this dataset
            HybridCgPm pmOriginal = pmWrapper.getHybridCgPm();
            HybridCgPm pm = copyPmForEstimation(pmOriginal); // see helper below

            HybridCgIm im = estimate(ds, pm, this.parameters);
            this.hybridIms.add(im);
        }

        this.hybridIm = this.hybridIms.get(0);
        this.numModels = this.hybridIms.size();
        this.modelIndex = 0;
        this.dataSet = (DataSet) dataWrapper.getDataModelList().get(this.modelIndex);
        this.name = pmWrapper.getName();
        log(this.hybridIm);
    }

    /** Alternate ctor: if starting from an IM wrapper, delegate to a PM wrapper for structure. */
    public HybridCgEstimatorWrapper(DataWrapper dataWrapper,
                                    HybridCgImWrapper imWrapper,
                                    Parameters parameters) {
        this(dataWrapper, new HybridCgPmWrapper(imWrapper.getPm().getGraph(), parameters), parameters);
    }

    // ================================ API ====================================

    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /** Ensure the PM has uniform cutpoints (lo..hi) split into {@code bins} for every continuous parent of a discrete child. */
    private static void ensureDefaultCutpoints(HybridCgPm pm, int bins, double lo, double hi) {
        final double[] cuts = new double[bins - 1];
        for (int i = 0; i < cuts.length; i++) cuts[i] = lo + (i + 1) * (hi - lo) / bins;

        final Node[] nodes = pm.getNodes();
        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;
            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node, double[]> byParent = new LinkedHashMap<>();
            for (int t = 0; t < cps.length; t++) {
                byParent.put(pm.getNodes()[cps[t]], cuts.clone());
            }
            pm.setContParentCutpointsForDiscreteChild(nodes[y], byParent);
        }
    }

    /** Set cutpoints for each continuous parent of each discrete child, from data. */
    private static void setCutpointsFromData(HybridCgPm pm, DataSet data, int bins, boolean equalFrequency) {
        final Node[] nodes = pm.getNodes();

        for (int y = 0; y < nodes.length; y++) {
            if (!pm.isDiscrete(y)) continue;

            int[] cps = pm.getContinuousParents(y);
            if (cps.length == 0) continue;

            Map<Node, double[]> byParent = new LinkedHashMap<>();

            for (int t = 0; t < cps.length; t++) {
                int parentIndex = cps[t];
                Node parentNode = nodes[parentIndex];

                int col = getColumnByNodeOrName(data, parentNode);
                if (col < 0) {
                    throw new IllegalArgumentException("Variable not in dataset: " + parentNode.getName());
                }

                double[] colData = new double[data.getNumRows()];
                for (int r = 0; r < data.getNumRows(); r++) colData[r] = data.getDouble(r, col);

                double[] cuts;
                if (equalFrequency) {
                    cuts = edu.cmu.tetrad.data.Discretizer.getEqualFrequencyBreakPoints(colData, bins);
                } else {
                    double min = edu.cmu.tetrad.util.StatUtils.min(colData);
                    double max = edu.cmu.tetrad.util.StatUtils.max(colData);
                    cuts = new double[bins - 1];
                    double step = (max - min) / bins;
                    for (int k = 0; k < cuts.length; k++) cuts[k] = min + (k + 1) * step;
                }

                for (int k = 1; k < cuts.length; k++) {
                    if (!(cuts[k] > cuts[k - 1])) cuts[k] = Math.nextUp(cuts[k - 1]);
                }
                byParent.put(parentNode, cuts);
            }

            pm.setContParentCutpointsForDiscreteChild(nodes[y], byParent);
        }
    }

    /** Defensive PM copy so per-dataset cutpoints don't bleed across runs. */
    private static HybridCgPm copyPmForEstimation(HybridCgPm pm) {
        // If you have a real copy constructor, prefer: return new HybridCgPm(pm);
        Graph g = pm.getGraph();
        List<Node> order = List.of(pm.getNodes());

        Map<Node, Boolean> isDisc = new LinkedHashMap<>();
        Map<Node, List<String>> cats = new LinkedHashMap<>();
        for (Node v : order) {
            int idx = pm.indexOf(v);
            boolean d = pm.isDiscrete(idx);
            isDisc.put(v, d);
            cats.put(v, d ? new ArrayList<>(pm.getCategories(idx)) : null);
        }

        HybridCgPm copy = new HybridCgPm(g, order, isDisc, cats);

        // Preserve any cutpoints already present on the original
        for (Node child : order) {
            int yOrig = pm.indexOf(child);
            if (!pm.isDiscrete(yOrig)) continue;
            int[] cps = pm.getContinuousParents(yOrig);
            if (cps.length == 0) continue;

            Map<Node, double[]> cpMap = new LinkedHashMap<>();
            pm.getContParentCutpointsForDiscreteChild(yOrig).ifPresent(cuts -> {
                for (int t = 0; t < cps.length; t++) {
                    cpMap.put(pm.getNodes()[cps[t]], cuts[t].clone());
                }
            });
            if (!cpMap.isEmpty()) {
                try {
                    copy.setContParentCutpointsForDiscreteChild(child, cpMap);
                } catch (Exception ignore) {
                }
            }
        }
        return copy;
    }

    public HybridCgIm getEstimatedHybridCgIm() {
        return this.hybridIm;
    }

    public void setHybridCgIm(HybridCgIm im) {
        this.hybridIms.clear();
        this.hybridIms.add(im);
        this.hybridIm = im;
        this.numModels = 1;
        this.modelIndex = 0;
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    public Graph getGraph() {
        return this.hybridIm != null ? this.hybridIm.getPm().getGraph() : null;
    }

    public String getName() {
        return this.name;
    }

    // ============================= SERIALIZATION =============================

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG Estimator" : name;
    }

    public int getNumModels() {
        return this.numModels;
    }

    public void setNumModels(int numModels) {
        this.numModels = numModels;
    }

    // ============================ INTERNALS ==================================

    public int getModelIndex() {
        return this.modelIndex;
    }

    public void setModelIndex(int modelIndex) {
        if (modelIndex < 0 || modelIndex >= hybridIms.size()) {
            throw new IndexOutOfBoundsException("modelIndex=" + modelIndex + " outside 0.." + (hybridIms.size() - 1));
        }
        this.modelIndex = modelIndex;
        this.hybridIm = this.hybridIms.get(modelIndex);
        DataModelList dml = this.dataWrapper.getDataModelList();
        this.dataSet = (DataSet) dml.get(modelIndex);
    }

    // ------------------------ Cutpoint utilities -----------------------------

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    private HybridCgIm estimate(DataSet data, HybridCgPm pm, Parameters params) {
        if (edu.cmu.tetrad.data.DataUtils.containsMissingValue(data)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        // *** NEW: align dataset variable objects to PM nodes ***
        DataSet aligned = alignDataVariablesToPm(pm, data);

        final double alpha    = params.getDouble("hybridcg.alpha", 1.0);
        final boolean shareVar = params.getBoolean("hybridcg.shareVariance", false);
        final String binPolicy = params.getString("hybridcg.binPolicy", "equal_frequency");
        final int bins         = Math.max(2, params.getInt("hybridcg.bins", 3));
        final int defaultBins  = Math.max(2, params.getInt("hybridcg.defaultBins", 3));
        final double defLo     = params.getDouble("hybridcg.defaultRangeLow", -1.0);
        final double defHi     = params.getDouble("hybridcg.defaultRangeHigh",  1.0);

        try {
            switch (binPolicy.toLowerCase(java.util.Locale.ROOT)) {
                case "equal_interval"  -> setCutpointsFromData(pm, aligned, bins, false);
                case "equal_frequency" -> setCutpointsFromData(pm, aligned, bins, true);
                case "none"            -> ensureDefaultCutpoints(pm, defaultBins, defLo, defHi);
                default                -> setCutpointsFromData(pm, aligned, bins, true);
            }
        } catch (Exception ex) {
            ensureDefaultCutpoints(pm, defaultBins, defLo, defHi);
        }

        // *** IMPORTANT: run MLE against the aligned dataset ***
        HybridCgIm.HybridEstimator est = new HybridCgIm.HybridEstimator(alpha, shareVar);
        return est.mle(pm, aligned);
    }

    // 1) Build a dataset whose variables are the PM's Node instances (by name), in the SAME column order
    private static DataSet alignDataVariablesToPm(HybridCgPm pm, DataSet data) {
        // Build name -> PM node map
        Map<String, Node> pmByName = new LinkedHashMap<>();
        for (Node v : pm.getNodes()) pmByName.put(v.getName(), v);

        // For each existing dataset column, swap in the PM node with the same name (if present)
        java.util.List<Node> newVars = new java.util.ArrayList<>(data.getNumColumns());
        for (int i = 0; i < data.getNumColumns(); i++) {
            Node dv = data.getVariable(i);
            Node pv = pmByName.get(dv.getName());
            newVars.add(pv != null ? pv : dv); // fall back to original if not found
        }

        // Re-wrap using the same DataBox but the new variable list
        return new edu.cmu.tetrad.data.BoxDataSet(((BoxDataSet) data).getDataBox(), newVars);
    }

    // 2) Safe column lookup (by node, with name fallback)
    private static int getColumnByNodeOrName(DataSet data, Node v) {
        int c = data.getColumn(v);
        if (c >= 0) return c;
        Node byName = data.getVariable(v.getName());
        return byName == null ? -1 : data.getColumn(byName);
    }

    private void log(HybridCgIm im) {
        if (im == null) return;
        TetradLogger.getInstance().log("ML estimated Hybrid CG IM.");
        TetradLogger.getInstance().log(im.toString());
    }
}