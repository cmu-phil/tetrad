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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.hybridcg.HybridCgEdgeSignificance;
import edu.cmu.tetrad.hybridcg.HybridCgEstimator;
import edu.cmu.tetrad.hybridcg.HybridCgPruneReport;
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
public class HybridCgEstimatorWrapper implements SessionModel, GraphSource {

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

    /**
     * The most recent prune proposal, whether or not it was applied; persisted so a saved session reopens with it.
     * Null until a proposal has been made.
     */
    private HybridCgPruneReport pruneReport;

    /**
     * The pruned graph currently applied, or null if the estimator is on the input graph. Persisted; the IMs are
     * serialized already re-estimated on it.
     */
    private Graph prunedGraph;

    // ============================== CONSTRUCTORS ==============================

    /**
     * Constructs a HybridCgEstimatorWrapper instance initialized with a simulation,
     * a HybridCgPmWrapper, and various estimation-related parameters.
     *
     * @param simulation the simulation object to be used in the estimation process
     * @param pmWrapper the HybridCgPmWrapper providing the structure and functionality
     *                  for parameter estimation
     * @param parameters the parameters object containing configuration and constraint
     *                   settings for the estimation
     */
    public HybridCgEstimatorWrapper(Simulation simulation,
                                    HybridCgPmWrapper pmWrapper,
                                    Parameters parameters) {
        this(new DataWrapper(simulation, parameters), pmWrapper, parameters);
    }

    /**
     * Constructs a HybridCgEstimatorWrapper instance initialized with the specified data wrapper,
     * parameter model wrapper, and estimation parameters.
     *
     * @param dataWrapper the data wrapper containing the dataset(s) for estimation; must not be null
     * @param pmWrapper the parameter model wrapper (HybridCgPmWrapper) providing the structure
     *                  required for the estimation; must not be null
     * @param parameters the estimation parameters; if null, default parameters will be used
     * @throws NullPointerException if dataWrapper or pmWrapper is null
     * @throws IllegalArgumentException if the data wrapper contains an empty list
     *                                  or non-DataSet entries
     */
    public HybridCgEstimatorWrapper(DataWrapper dataWrapper,
                                    HybridCgPmWrapper pmWrapper,
                                    Parameters parameters) {
        if (dataWrapper == null) throw new NullPointerException("DataWrapper must not be null.");
        if (pmWrapper == null) throw new NullPointerException("HybridCgPmWrapper must not be null.");

        this.dataWrapper = dataWrapper;
        this.pmWrapper = pmWrapper;
        this.parameters = (parameters == null) ? new Parameters() : parameters;

        DataModelList dml = dataWrapper.getDataModelList();
        if (dml == null || dml.isEmpty()) {
            throw new IllegalArgumentException("Data must be a non-empty list of data sets.");
        }

        // Estimate an IM per dataset
        for (DataModel dm : dml) {
            if (!(dm instanceof DataSet ds)) {
                throw new IllegalArgumentException("All entries must be DataSet instances (mixed or discrete/continuous).");
            }
            // Defensive PM copy so per-dataset cutpoints don’t bleed across runs.
            HybridCgPm pmCopy = copyPmForEstimation(pmWrapper.getHybridCgPm());
            HybridCgIm im = HybridCgEstimator.estimate(pmCopy, ds, this.parameters);
            this.hybridIms.add(im);
        }

        this.hybridIm = this.hybridIms.getFirst();
        this.numModels = this.hybridIms.size();
        this.modelIndex = 0;
        this.dataSet = (DataSet) dataWrapper.getDataModelList().get(this.modelIndex);
        this.name = pmWrapper.getName();
        log(this.hybridIm);
    }

    /**
     * Constructs a HybridCgEstimatorWrapper instance using the provided data wrapper,
     * hybrid independence model wrapper, and estimation parameters.
     *
     * @param dataWrapper the data wrapper containing the dataset for estimation; must not be null
     * @param imWrapper the HybridCgImWrapper representing the independence model for estimation; must not be null
     * @param parameters the estimation parameters; if null, default parameters will be used
     * @throws NullPointerException if dataWrapper or imWrapper is null
     * @throws IllegalArgumentException if the data wrapper contains an empty list or non-DataSet entries
     */
    public HybridCgEstimatorWrapper(DataWrapper dataWrapper,
                                    HybridCgImWrapper imWrapper,
                                    Parameters parameters) {
        this(dataWrapper, new HybridCgPmWrapper(imWrapper.getPm().getGraph(), null, parameters), parameters);
    }

    // ================================ API ====================================

    /**
     * Provides a serializable instance of the PcRunner class.
     *
     * @return A PcRunner instance that supports serialization for use in
     *         operations requiring a serializable object.
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /** Defensive PM copy so per-dataset cutpoints (set during estimation) don’t mutate the original PM. */
    private static HybridCgPm copyPmForEstimation(HybridCgPm pm) {
        return copyPmForEstimation(pm, pm.getGraph());
    }

    /**
     * As {@link #copyPmForEstimation(HybridCgPm)}, over the given graph, which may have fewer edges than the
     * source's (a pruned graph). Typing, categories, and node order come from the source PM; cutpoints are carried
     * over where the receiving child's continuous-parent set allows.
     */
    private static HybridCgPm copyPmForEstimation(HybridCgPm pm, Graph g) {
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

    /**
     * Retrieves the estimated Hybrid Conditional Gaussian Independence Model (HybridCgIm)
     * that is associated with this instance.
     *
     * @return the estimated HybridCgIm for the current instance
     */
    public HybridCgIm getEstimatedHybridCgIm() {
        return this.hybridIm;
    }

    /**
     * Sets the specified Hybrid Conditional Gaussian Independence Model (HybridCgIm)
     * for this instance. This method clears existing HybridCgIm models,
     * adds the provided model, and updates related internal parameters.
     *
     * @param im the HybridCgIm model to be set; must not be null
     * @throws NullPointerException if the provided HybridCgIm model is null
     */
    public void setHybridCgIm(HybridCgIm im) {
        this.hybridIms.clear();
        this.hybridIms.add(Objects.requireNonNull(im));
        this.hybridIm = im;
        this.numModels = 1;
        this.modelIndex = 0;
    }

    /**
     * Retrieves the DataSet associated with this instance.
     *
     * @return the DataSet currently associated with this instance
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * Retrieves the graph associated with the current instance.
     * If the hybrid independence model (HybridCgIm) is not null,
     * this method returns the graph from its parameter model (HybridCgPm).
     * Otherwise, it returns null.
     *
     * @return the graph from the parameter model if the HybridCgIm is not null;
     *         null otherwise
     */
    public Graph getGraph() {
        return this.hybridIm != null ? this.hybridIm.getPm().getGraph() : null;
    }

    // ============================ SIGNIFICANCE AND PRUNING ====================

    /**
     * Per-edge LRT significance for the current model against the current dataset, using this wrapper's
     * {@code hybridcg.shareVariance} setting. Computed on demand; see
     * {@link HybridCgEdgeSignificance#compute(HybridCgPm, DataSet, boolean)} for the test and its stated
     * approximations.
     *
     * @return map from each edge of the current model's graph to its significance result
     */
    public Map<Edge, HybridCgEdgeSignificance.Result> edgeSignificance() {
        return HybridCgEdgeSignificance.compute(this.hybridIm.getPm(), this.dataSet,
                this.parameters.getBoolean("hybridcg.shareVariance", false));
    }

    /**
     * Per-stratum OLS t-test p-values for the linear coefficients of continuous children in the current model, for
     * table display; see {@link HybridCgEdgeSignificance#coefficientPValues}.
     *
     * @return the p-value array, indexed [node][stratum row][continuous-parent order index]
     */
    public double[][][] coefficientPValues() {
        return HybridCgEdgeSignificance.coefficientPValues(this.hybridIm, this.dataSet);
    }

    /**
     * Proposes a backward-elimination prune of the current model's graph at the given level, computed on the
     * currently selected dataset; see {@link HybridCgEdgeSignificance#backwardPrune} for the elimination rule and
     * its caveats. The proposal is stored (and persisted with the session) but nothing is modified until
     * {@link #applyPrune()}.
     *
     * @param alpha the significance level for the per-edge LRT
     * @return the proposal
     */
    public HybridCgPruneReport proposePrune(double alpha) {
        this.pruneReport = HybridCgEdgeSignificance.backwardPrune(this.hybridIm.getPm(), this.dataSet, alpha,
                this.parameters.getBoolean("hybridcg.shareVariance", false));
        return this.pruneReport;
    }

    /**
     * Adopts the stored prune proposal: every dataset's IM is re-estimated on the proposal's pruned graph, and
     * {@link #getGraph()} subsequently returns that graph. The upstream PM wrapper is not modified, so the input
     * graph remains recoverable via {@link #getInputGraph()} and {@link #revertPrune()}.
     * <p>
     * Note for multi-dataset use: the proposal was computed on the currently selected dataset only, but the pruned
     * structure is applied to every dataset's re-estimate, since this wrapper shares one structure across its
     * datasets by design.
     *
     * @throws IllegalStateException if no proposal has been made
     */
    public void applyPrune() {
        if (this.pruneReport == null) {
            throw new IllegalStateException("No prune proposal to apply; call proposePrune(alpha) first.");
        }
        reestimateOn(this.pruneReport.getPrunedGraph());
        this.prunedGraph = this.pruneReport.getPrunedGraph();
        TetradLogger.getInstance().log("Applied Hybrid CG prune; re-estimated on the pruned graph.");
        TetradLogger.getInstance().log(this.pruneReport.toString());
    }

    /**
     * Re-estimates every dataset's IM on the input graph and clears the applied pruned graph. The stored proposal is
     * kept for reference.
     */
    public void revertPrune() {
        if (this.prunedGraph == null) return;
        reestimateOn(getInputGraph());
        this.prunedGraph = null;
        TetradLogger.getInstance().log("Reverted Hybrid CG prune; re-estimated on the input graph.");
    }

    /**
     * The input graph this estimator was constructed on, from the upstream PM wrapper, regardless of any applied
     * prune.
     *
     * @return the input graph
     */
    public Graph getInputGraph() {
        return this.pmWrapper.getHybridCgPm().getGraph();
    }

    /**
     * The most recent prune proposal, applied or not, or null if none has been made.
     *
     * @return the proposal or null
     */
    public HybridCgPruneReport getPruneReport() {
        return this.pruneReport;
    }

    /**
     * The pruned graph currently applied, or null if the estimator is on the input graph.
     *
     * @return the applied pruned graph or null
     */
    public Graph getPrunedGraph() {
        return this.prunedGraph;
    }

    /**
     * Re-estimates every dataset's IM with the current parameters on the graph currently in force: the applied
     * pruned graph if there is one, else the input graph. This is what the estimator editor's Estimate button runs,
     * so re-estimating does not silently discard an applied prune.
     */
    public void reestimate() {
        reestimateOn(this.prunedGraph != null ? this.prunedGraph : getInputGraph());
    }

    /** Re-estimates one IM per dataset over the given graph, preserving model index and selection. */
    private void reestimateOn(Graph graph) {
        DataModelList dml = this.dataWrapper.getDataModelList();
        List<HybridCgIm> ims = new ArrayList<>();
        for (DataModel dm : dml) {
            DataSet ds = (DataSet) dm;
            HybridCgPm pmCopy = copyPmForEstimation(this.pmWrapper.getHybridCgPm(), graph);
            ims.add(HybridCgEstimator.estimate(pmCopy, ds, this.parameters));
        }
        this.hybridIms.clear();
        this.hybridIms.addAll(ims);
        this.numModels = ims.size();
        if (this.modelIndex >= ims.size()) this.modelIndex = 0;
        this.hybridIm = this.hybridIms.get(this.modelIndex);
        this.dataSet = (DataSet) dml.get(this.modelIndex);
    }

    /**
     * Retrieves the name associated with this instance.
     *
     * @return the name associated with this instance as a String
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name associated with this instance.
     * If the provided name is null or blank, it defaults to "Hybrid CG Estimator".
     *
     * @param name the name of the session model.
     */
    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Hybrid CG Estimator" : name;
    }

    /**
     * Retrieves the number of models associated with this instance.
     *
     * @return the number of models as an integer
     */
    public int getNumModels() {
        return this.numModels;
    }

    /**
     * Sets the number of models associated with this instance.
     * If the provided number of models is less than 1, it defaults to 1.
     *
     * @param numModels the number of models to set
     */
    public void setNumModels(int numModels) {
        this.numModels = numModels;
    }

    /**
     * Retrieves the index of the current model.
     *
     * @return the index of the current model as an integer
     */
    public int getModelIndex() {
        return this.modelIndex;
    }

    /**
     * Retrieves the data wrapper associated with this instance.
     *
     * @return the data wrapper associated with this instance
     */
    public DataWrapper getDataWrapper() { return this.dataWrapper; }

    /**
     * Retrieves the parameter model wrapper associated with this instance.
     *
     * @return the parameter model wrapper associated with this instance
     */
    public HybridCgPmWrapper getPmWrapper() { return this.pmWrapper; }

    /**
     * Retrieves the parameters associated with this instance.
     *
     * @return the parameters associated with this instance
     */
    public Parameters getParameters()   { return this.parameters; }

    /**
     * Sets the index of the current model.
     * If the provided index is out of bounds, it throws an IndexOutOfBoundsException.
     *
     * @param modelIndex the index of the current model
     */
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