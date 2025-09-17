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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgEstimator;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import edu.cmu.tetrad.util.Parameters;
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
 * <p>Behavior mirrors {@code BayesEstimatorWrapper}:</p>
 * <ul>
 *   <li>Consumes one or more {@link DataSet}s from a {@link DataWrapper}.</li>
 *   <li>For each dataset, estimates a {@link edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm}
 *       from the provided {@link edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm}.</li>
 *   <li>Keeps a list of IMs parallel to the dataset list; exposes model index switching.</li>
 * </ul>
 *
 * <p>Parameters are passed through to {@link edu.cmu.tetrad.hybridcg.HybridCgEstimator}:</p>
 * <ul>
 *   <li>{@code hybridcg.alpha} (double, default 1.0)</li>
 *   <li>{@code hybridcg.shareVariance} (boolean, default false)</li>
 *   <li>{@code hybridcg.binPolicy} (string: {@code equal_frequency} | {@code equal_interval} | {@code none};
 *       default {@code equal_frequency})</li>
 *   <li>{@code hybridcg.bins} (int, default 3, min 2)</li>
 *   <li>{@code hybridcg.defaultBins} (int, default 3)</li>
 *   <li>{@code hybridcg.defaultRangeLow} (double, default -1.0)</li>
 *   <li>{@code hybridcg.defaultRangeHigh} (double, default 1.0)</li>
 * </ul>
 */
public class HybridCgEstimatorWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 42L;

    private final DataWrapper dataWrapper;
    private final Parameters parameters;

    private final List<HybridCgIm> hybridIms = new ArrayList<>();
    private final HybridCgPmWrapper pmWrapper;
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
        this.pmWrapper = pmWrapper;
        this.parameters = (parameters == null) ? new Parameters() : parameters;

        DataModelList dml = dataWrapper.getDataModelList();
        if (dml == null || dml.size() == 0) {
            throw new IllegalArgumentException("Data must be a non-empty list of data sets.");
        }

        // Estimate an IM per dataset
        for (int i = 0; i < dml.size(); i++) {
            DataModel dm = dml.get(i);
            if (!(dm instanceof DataSet ds)) {
                throw new IllegalArgumentException("All entries must be DataSet instances (mixed or discrete/continuous).");
            }
            // Defensive PM copy so per-dataset cutpoints don’t bleed across runs.
            HybridCgPm pmCopy = copyPmForEstimation(pmWrapper.getHybridCgPm());
            HybridCgIm im = HybridCgEstimator.estimate(pmCopy, ds, this.parameters);
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

    // Kept for back-compat if something expects this symbol; no-op proxy to existing pattern.
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /** Defensive PM copy so per-dataset cutpoints (set during estimation) don’t mutate the original PM. */
    private static HybridCgPm copyPmForEstimation(HybridCgPm pm) {
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
                    // if shapes changed, we’ll let the estimator recompute per policy
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
        this.hybridIms.add(Objects.requireNonNull(im));
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

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG Estimator" : name;
    }

    public int getNumModels() {
        return this.numModels;
    }

    public void setNumModels(int numModels) {
        this.numModels = numModels;
    }

    public int getModelIndex() {
        return this.modelIndex;
    }

    // In HybridCgEstimatorWrapper
    public DataWrapper getDataWrapper() { return this.dataWrapper; }
    public HybridCgPmWrapper getPmWrapper() { return this.pmWrapper; }
    public Parameters getParameters()   { return this.parameters; }

    // ============================= SERIALIZATION =============================

    public void setModelIndex(int modelIndex) {
        if (modelIndex < 0 || modelIndex >= hybridIms.size()) {
            throw new IndexOutOfBoundsException("modelIndex=" + modelIndex + " outside 0.." + (hybridIms.size() - 1));
        }
        this.modelIndex = modelIndex;
        this.hybridIm = this.hybridIms.get(modelIndex);
        DataModelList dml = this.dataWrapper.getDataModelList();
        this.dataSet = (DataSet) dml.get(modelIndex);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    // ============================ INTERNALS ==================================

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize: " + getClass().getCanonicalName() + ", " + e.getMessage());
            throw e;
        }
    }

    private void log(HybridCgIm im) {
        if (im == null) return;
        TetradLogger.getInstance().log("ML estimated Hybrid CG IM.");
        TetradLogger.getInstance().log(im.toString());
    }
}