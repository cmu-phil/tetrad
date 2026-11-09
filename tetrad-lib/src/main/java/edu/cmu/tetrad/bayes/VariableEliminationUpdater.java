package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.List;

/**
 * RowSummingExactUpdater-style updater plumbing, but uses VariableEliminationInference
 * for marginals/joints/conditionals. All evidence and BayesIm alignment behavior
 * is inherited from the RowSummingExactUpdater design.
 */
public final class VariableEliminationUpdater implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents a Bayesian network instantiation model (BayesIm) that provides
     * properties or methods to define and manipulate a parameterized Bayesian Network.
     * The structure includes conditional probabilities for nodes in the network,
     * defining how they interact and depend on one another.
     * <p>
     * This variable is immutable and is initialized upon declaration.
     */
    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables (Indexed to SOURCE variable list).
     * <p>
     * IMPORTANT: This is intentionally NOT defensively copied, matching
     * RowSummingExactUpdater behavior. The UI may mutate Evidence in-place.
     */
    private Evidence evidence;
    /**
     * Represents a manipulated Bayesian network in the form of a {@code BayesIm}.
     * This variable is used to store a version of the network modified based on
     * specific interventions or adjustments. These modifications may involve
     * altering conditional probability tables (CPTs) to reflect the effects of
     * applied changes.
     * <p>
     * The {@code manipulatedBayesIm} is distinct from the original and updated
     * Bayesian networks, as it encapsulates the changes applied to the network state
     * during the manipulation process.
     */
    private BayesIm manipulatedBayesIm;
    /**
     * Represents the updated Bayesian network model after incorporating the effects
     * of interventions, adjustments, or evidence updates. This field stores a
     * {@code BayesIm} instance that contains the recalculated probabilities and
     * structure reflecting the current state of the Bayesian network.
     * <p>
     * The updated Bayesian network differs from the original network and the
     * manipulated network in that it encapsulates the comprehensive state derived
     * from applying all relevant changes. It is typically used for inference
     * and analysis within the context of Bayesian network operations.
     */
    private BayesIm updatedBayesIm;

    /**
     * Variable elimination inference engine over manipulatedBayesIm.
     * (manipulations are already baked into manipulatedBayesIm CPTs via do()-surgery)
     */
    private transient VariableEliminationInference vei;

    //==============================CONSTRUCTORS===========================//

    /**
     * Constructs a new instance of {@code VariableElimiinationUpdater} using the provided
     * Bayesian network representation.
     *
     * @param bayesIm the Bayesian network representation in the form of a {@code BayesIm}.
     *                Must not be {@code null}.
     * @throws NullPointerException if {@code bayesIm} is {@code null}.
     */
    public VariableEliminationUpdater(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException();
        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(bayesIm));
    }

    /**
     * Constructs a new instance of {@code VariableElimiinationUpdater}, using the provided
     * Bayesian network representation and associated evidence.
     *
     * @param bayesIm  the Bayesian network representation in the form of a {@code BayesIm}.
     *                 Must not be {@code null}.
     * @param evidence the evidence object containing observed data for the Bayesian network.
     *                 Can be {@code null} if no evidence is provided.
     * @throws NullPointerException if {@code bayesIm} is {@code null}.
     */
    public VariableEliminationUpdater(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) throw new NullPointerException();
        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    /**
     * Creates an instance of the VariableElimiinationUpdater class which performs updates on a variable elimination
     * structure based on Bayesian networks and evidence.
     *
     * @param bayesIm            The Bayesian network representation (BayesIm) used for the initial setup.
     * @param evidence           The evidence object containing observations and values to be applied to the network.
     * @param manipulatedBayesIm The manipulated BayesIm reflecting changes to the original network.
     * @param updatedBayesIm     The updated BayesIm reflecting the state of the network after processing evidence.
     * @param vei                The VariableElimiinationUpdater object used for performing inference.
     */
    public VariableEliminationUpdater(BayesIm bayesIm, Evidence evidence, BayesIm manipulatedBayesIm, BayesIm updatedBayesIm, VariableEliminationInference vei) {
        this.bayesIm = bayesIm;
        this.evidence = evidence;
        this.manipulatedBayesIm = manipulatedBayesIm;
        this.updatedBayesIm = updatedBayesIm;
        this.vei = vei;
    }

    /**
     * Creates and returns a serializable instance of {@code VariableElimiinationUpdater}.
     *
     * @return a new instance of {@code VariableElimiinationUpdater}, configured with a serializable
     * {@code BayesIm} instance, enabling the use of this updater in serialized form.
     */
    public static VariableEliminationUpdater serializableInstance() {
        return new VariableEliminationUpdater(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    /**
     * Retrieves the Bayesian network representation associated with this updater.
     *
     * @return the {@code BayesIm} instance representing the current Bayesian network.
     */
    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * Retrieves the manipulated Bayesian network representation associated with this updater.
     * The manipulated Bayesian network incorporates changes based on applied interventions
     * or adjustments, distinguishing it from the original or updated Bayesian network.
     *
     * @return the {@code BayesIm} instance representing the manipulated Bayesian network.
     */
    @Override
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    /**
     * Retrieves the manipulated Bayesian network representation associated with this updater.
     * The manipulated Bayesian network incorporates changes based on applied interventions
     * or adjustments, distinguishing it from the original or updated Bayesian network.
     *
     * @return the {@code BayesIm} instance representing the manipulated Bayesian network.
     */
    @Override
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    /**
     * Retrieves the updated Bayesian network representation associated with this updater.
     * The updated Bayesian network reflects the current state after applying interventions
     * or adjustments, contrasting with the original or manipulated Bayesian network.
     *
     * @return the {@code BayesIm} instance representing the updated Bayesian network.
     */
    @Override
    public BayesIm getUpdatedBayesIm() {
        if (this.updatedBayesIm == null) {
            updateAll();
        }
        return this.updatedBayesIm;
    }

    /**
     * Retrieves the evidence associated with this updater.
     * The evidence encapsulates the current state of observed variables and their values.
     *
     * @return the {@code Evidence} instance representing the current evidence.
     */
    @Override
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

    /**
     * Sets the evidence for the Bayesian network updater. The provided evidence
     * specifies observed data or conditions that will influence the Bayesian
     * inference process. This method validates the compatibility of the evidence
     * with the existing Bayesian network structure and updates internal states
     * accordingly.
     *
     * @param evidence the {@code Evidence} object representing the observed data
     *                 for the Bayesian network. Must not be {@code null}.
     * @throws NullPointerException     if {@code evidence} is {@code null}.
     * @throws IllegalArgumentException if the variable list in the provided
     *                                  {@code evidence} is incompatible with the
     *                                  variable list in the {@code BayesIm}.
     */
    @Override
    public void setEvidence(Evidence evidence) {
        if (evidence == null) throw new NullPointerException();

        if (evidence.isIncompatibleWith(this.bayesIm)) {
            throw new IllegalArgumentException("The variable list for the " +
                    "given bayesIm must be compatible with the variable list " +
                    "for this evidence.");
        }

        // IMPORTANT: match RowSummingExactUpdater semantics (UI may mutate in place)
        this.evidence = evidence;

        // Build manipulated graph via do()-surgery.
        Graph graph = this.bayesIm.getBayesPm().getDag();
        Dag manipulatedGraph = createManipulatedGraph(graph);
        BayesPm manipulatedPm = createUpdatedBayesPm(manipulatedGraph);

        // Create manipulated IM (we will copy CPTs explicitly).
        this.manipulatedBayesIm = createdUpdatedBayesIm(manipulatedPm);

        // Ensure manipulatedBayesIm matches source BayesIm everywhere except manipulated nodes.
        copyCptsFromSourceIntoManipulatedIm();

        // Override manipulated nodes to match the do() intervention distribution.
        applyDoInterventionsToManipulatedIm();

        // Build (or rebuild) JT engine on manipulated IM.
        this.vei = new VariableEliminationInference(this.manipulatedBayesIm);

        // IMPORTANT: do not compute updatedBayesIm eagerly; updateAll() will do it if asked.
        this.updatedBayesIm = null;

        // Sync current evidence into JT engine (allowed categories + optional singleton hard evidence)
        syncJtiFromCurrentEvidence();
    }

    /**
     * Indicates whether this updater supports computing joint marginals.
     *
     * @return true
     */
    @Override
    public boolean isJointMarginalSupported() {
        return true;
    }

    /**
     * Indicates whether this updater supports computing joint marginals.
     *
     * @param variables variable indices
     * @param values    category indices
     * @return true
     */
    @Override
    public double getJointMarginal(int[] variables, int[] values) {
        if (variables.length != values.length) {
            throw new IllegalArgumentException("Values must match variables.");
        }

        // Map SOURCE variable indices -> manipulated variable indices by name.
        int[] dstVars = new int[variables.length];
        for (int i = 0; i < variables.length; i++) {
            String name = this.bayesIm.getNode(variables[i]).getName();
            Node dstNodeObj = this.manipulatedBayesIm.getNode(name);
            if (dstNodeObj == null) return Double.NaN;
            dstVars[i] = this.manipulatedBayesIm.getNodeIndex(dstNodeObj);
        }

        // Check evidence consistency in manipulated space (same as RowSumming)
        Proposition condition = conditionFromEvidence();
        if (!condition.existsCombination()) return Double.NaN;

        syncJtiFromCurrentEvidence();

        // JT returns probability of this assignment under evidence (your JT code used this interpretation)
        return this.vei.getJointProbability(dstVars, values);
    }

    /**
     * Indicates whether this updater supports computing joint marginals.
     *
     * @param variable variable index
     * @param value    category index
     * @return true
     */
    @Override
    public double getMarginal(int variable, int value) {
        // Interpret "variable" as SOURCE index, map by name to manipulatedBayesIm index.
        String name = this.bayesIm.getNode(variable).getName();

        Node dstNodeObj = this.manipulatedBayesIm.getNode(name);
        if (dstNodeObj == null) return Double.NaN;

        int dstVar = this.manipulatedBayesIm.getNodeIndex(dstNodeObj);

        // Check evidence consistency in manipulated space (same as RowSumming)
        Proposition condition = conditionFromEvidence();
        if (!condition.existsCombination()) return Double.NaN;

        syncJtiFromCurrentEvidence();

        return this.vei.getMarginal(dstVar, value);
    }

    /**
     * Calculates the prior marginals for a specified node in the Bayesian network.
     * The method temporarily resets the evidence to a tautology, calculates the
     * prior marginals for the specified node, and then restores the previous evidence.
     *
     * @param nodeIndex the index of the node in the Bayesian network for which
     *                  the prior marginals are to be calculated.
     *                  Must be a valid index within the range of nodes in the network.
     * @return an array of doubles representing the prior marginal probabilities
     * of the different categories (states) for the specified node.
     * The size of the array corresponds to the number of categories
     * associated with the node.
     */
    @Override
    public double[] calculatePriorMarginals(int nodeIndex) {
        Evidence oldEvidence = getEvidence();

        // Reset to tautology on the same variable source (source BayesIm / evidence indexing).
        setEvidence(Evidence.tautology(oldEvidence.getVariableSource()));

        int numCats = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[numCats];
        for (int cat = 0; cat < numCats; cat++) {
            marginals[cat] = getMarginal(nodeIndex, cat);
        }

        setEvidence(oldEvidence);
        return marginals;
    }

    /**
     * Computes the updated marginal probabilities for a given node in a Bayesian network.
     * The method calculates marginal probabilities for each category based on the
     * current state of the Bayesian network. It ensures that the marginals are normalized
     * unless the computed values are non-finite or the sum is not greater than zero, in which
     * case the resulting marginals are set to NaN.
     *
     * @param nodeIndex the index of the node in the Bayesian network for which the updated
     *                  marginal probabilities are to be calculated
     * @return an array containing the updated marginal probabilities for each category of
     * the specified node. If the normalization conditions fail, the array contains NaN values
     */
    @Override
    public double[] calculateUpdatedMarginals(int nodeIndex) {
        int numCats = this.bayesIm.getNumColumns(nodeIndex);
        double[] marginals = new double[numCats];
        for (int cat = 0; cat < numCats; cat++) {
            marginals[cat] = getMarginal(nodeIndex, cat);
        }

        // Normalize only if finite and sum>0 (same as RowSummingExactUpdater)
        double sum = 0.0;
        for (double v : marginals) {
            if (!Double.isFinite(v)) return marginals;
            sum += v;
        }

        if (!(sum > 0.0)) {
            for (int i = 0; i < marginals.length; i++) marginals[i] = Double.NaN;
            return marginals;
        }

        for (int i = 0; i < marginals.length; i++) marginals[i] /= sum;
        return marginals;
    }

    /**
     * Returns a string representation of the VariableElimiinationUpdater instance,
     * summarizing its inference method and current evidence state.
     *
     * @return a string describing the inference method used (RowSumming plumbing
     * combined with Variable Elimination inference) and the current evidence state.
     */
    @Override
    public String toString() {
        return "VariableElimination (RowSumming plumbing + VE inference), evidence = " + this.evidence;
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * Sync the current Evidence into the JT engine.
     * <p>
     * This is intentionally called before queries because Evidence may be mutated
     * in place by the UI (RowSumming semantics). This keeps JT correct without relying
     * on UI calling setEvidence().
     */
    private void syncJtiFromCurrentEvidence() {
        if (this.vei == null || this.manipulatedBayesIm == null || this.evidence == null) {
            return;
        }

        // Evidence re-indexed to manipulated IM
        Evidence ev2 = new Evidence(this.evidence, this.manipulatedBayesIm);
        Proposition p2 = ev2.getProposition();

        // Soft evidence: allowed categories
        this.vei.setAllowedCategories(p2);
    }

    /**
     * Build a condition Proposition that belongs to manipulatedBayesIm and encodes the Evidence's
     * allowed/disallowed categories, matched by node name.
     */
    private Proposition conditionFromEvidence() {
        Proposition condition = Proposition.tautology(this.manipulatedBayesIm);
        Proposition evProp = this.evidence.getProposition();
        BayesIm src = this.bayesIm;

        for (int dstNode = 0; dstNode < this.manipulatedBayesIm.getNumNodes(); dstNode++) {
            String name = this.manipulatedBayesIm.getNode(dstNode).getName();

            Node srcNodeObj = src.getNode(name);
            if (srcNodeObj == null) {
                throw new IllegalStateException("Node '" + name + "' not found in source BayesIm.");
            }
            int srcNode = src.getNodeIndex(srcNodeObj);

            int dstCats = this.manipulatedBayesIm.getNumColumns(dstNode);
            int srcCats = this.evidence.getNumCategories(srcNode);
            if (dstCats != srcCats) {
                throw new IllegalStateException("Category count mismatch for node '" + name +
                        "': evidence has " + srcCats + ", manipulated has " + dstCats + ".");
            }

            for (int cat = 0; cat < dstCats; cat++) {
                if (!evProp.isAllowed(srcNode, cat)) {
                    condition.removeCategory(dstNode, cat);
                }
            }
        }

        return condition;
    }

    /**
     * Fill updatedBayesIm CPTs using JT inference (instead of BayesImProbs).
     * <p>
     * This mirrors the RowSummingExactUpdater updateAll loop structure, but uses
     * JT for conditional/marginal computations.
     */
    private void updateAll() {
        BayesIm updated = new MlBayesIm(this.manipulatedBayesIm);
        int numNodes = this.manipulatedBayesIm.getNumNodes();

        // Ensure JT sees current evidence before bulk filling CPTs.
        syncJtiFromCurrentEvidence();

        // Precompute evidence condition once per updateAll (same semantics as before).
        // We will then further restrict by parent assignments row-by-row.
        for (int node = 0; node < numNodes; node++) {
            int numRows = this.manipulatedBayesIm.getNumRows(node);
            int numCols = this.manipulatedBayesIm.getNumColumns(node);
            int[] parents = this.manipulatedBayesIm.getParents(node);

            for (int row = 0; row < numRows; row++) {
                int[] parentValues = this.manipulatedBayesIm.getParentValues(node, row);

                // If parents exist, we want P(X=node | parents=parentValues, evidence)
                // JT API you used elsewhere: getConditional(node, parents, parentValues)[col]
                double[] cond;
                if (parents.length > 0) {
                    cond = this.vei.getConditional(node, parents, parentValues);
                } else {
                    cond = null; // use marginals below
                }

                for (int col = 0; col < numCols; col++) {
                    // Keep the RowSumming behavior: if evidence is inconsistent, set NaN.
                    // But ALSO need to guard parent restriction consistency.
                    Proposition condition = conditionFromEvidence();
                    for (int k = 0; k < parents.length; k++) {
                        condition.disallowComplement(parents[k], parentValues[k]);
                    }

                    if (!condition.existsCombination()) {
                        updated.setProbability(node, row, col, Double.NaN);
                        continue;
                    }

                    double p = (parents.length > 0) ? cond[col] : this.vei.getMarginal(node, col);
                    updated.setProbability(node, row, col, p);
                }
            }
        }

        this.updatedBayesIm = updated;
    }

    /**
     * Copy CPTs from the source BayesIm into manipulatedBayesIm for all NON-manipulated nodes.
     * (same as your current version)
     */
    private void copyCptsFromSourceIntoManipulatedIm() {
        BayesIm src = this.bayesIm;
        BayesIm dst = this.manipulatedBayesIm;

        for (int dstNode = 0; dstNode < dst.getNumNodes(); dstNode++) {
            String name = dst.getNode(dstNode).getName();

            Node srcNodeObj = src.getNode(name);
            if (srcNodeObj == null) {
                throw new IllegalStateException("Node '" + name + "' not found in source BayesIm.");
            }
            int srcNode = src.getNodeIndex(srcNodeObj);

            boolean manipulated = this.evidence.isManipulated(srcNode);
            if (manipulated) continue;

            int srcCols = src.getNumColumns(srcNode);
            int dstCols = dst.getNumColumns(dstNode);
            if (srcCols != dstCols) {
                throw new IllegalStateException("Category count mismatch for node '" + name +
                        "': source has " + srcCols + ", manipulated has " + dstCols + ".");
            }

            int[] srcParents = src.getParents(srcNode);
            int[] dstParents = dst.getParents(dstNode);

            int[] srcPosToDstPos = new int[srcParents.length];
            for (int k = 0; k < srcParents.length; k++) {
                String pName = src.getNode(srcParents[k]).getName();
                int dstPos = -1;
                for (int t = 0; t < dstParents.length; t++) {
                    if (dst.getNode(dstParents[t]).getName().equals(pName)) {
                        dstPos = t;
                        break;
                    }
                }
                if (dstPos < 0) {
                    throw new IllegalStateException(
                            "Parent mismatch while copying CPTs for node '" + name +
                                    "': expected parent '" + pName + "' not found in manipulated IM.");
                }
                srcPosToDstPos[k] = dstPos;
            }

            for (int dstRow = 0; dstRow < dst.getNumRows(dstNode); dstRow++) {
                int[] dstParentValuesInDstOrder = dst.getParentValues(dstNode, dstRow);

                int[] srcParentValuesInSrcOrder = new int[srcParents.length];
                for (int k = 0; k < srcParents.length; k++) {
                    srcParentValuesInSrcOrder[k] = dstParentValuesInDstOrder[srcPosToDstPos[k]];
                }

                int srcRow = src.getRowIndex(srcNode, srcParentValuesInSrcOrder);

                for (int col = 0; col < dstCols; col++) {
                    dst.setProbability(dstNode, dstRow, col,
                            src.getProbability(srcNode, srcRow, col));
                }
            }
        }
    }

    /**
     * Apply do() interventions: overwrite manipulated CPT rows with indicator distribution from Evidence proposition.
     */
    private void applyDoInterventionsToManipulatedIm() {
        for (int srcNode = 0; srcNode < evidence.getNumNodes(); srcNode++) {
            if (!evidence.isManipulated(srcNode)) continue;

            String name = evidence.getNode(srcNode).getName();
            Node dstNodeObj = manipulatedBayesIm.getNode(name);
            if (dstNodeObj == null) {
                throw new IllegalStateException("Manipulated node '" + name + "' not found in manipulated BayesIm.");
            }
            int dstNode = manipulatedBayesIm.getNodeIndex(dstNodeObj);

            if (manipulatedBayesIm.getNumRows(dstNode) != 1) {
                throw new IllegalStateException("Expected exactly one row for manipulated node '" + name +
                        "' after graph surgery, but found " + manipulatedBayesIm.getNumRows(dstNode) + ".");
            }

            int numCats = manipulatedBayesIm.getNumColumns(dstNode);
            if (numCats != evidence.getNumCategories(srcNode)) {
                throw new IllegalStateException("Category count mismatch for manipulated node '" + name +
                        "': evidence has " + evidence.getNumCategories(srcNode) +
                        ", manipulated BayesIm has " + numCats + ".");
            }

            for (int cat = 0; cat < numCats; cat++) {
                manipulatedBayesIm.setProbability(dstNode, 0, cat,
                        evidence.getProposition().isAllowed(srcNode, cat) ? 1.0 : 0.0);
            }

            double sum = 0.0;
            for (int cat = 0; cat < numCats; cat++) {
                sum += manipulatedBayesIm.getProbability(dstNode, 0, cat);
            }

            if (sum > 0.0) {
                for (int cat = 0; cat < numCats; cat++) {
                    manipulatedBayesIm.setProbability(dstNode, 0, cat,
                            manipulatedBayesIm.getProbability(dstNode, 0, cat) / sum);
                }
            } else {
                for (int cat = 0; cat < numCats; cat++) {
                    manipulatedBayesIm.setProbability(dstNode, 0, cat, Double.NaN);
                }
            }
        }
    }

    private BayesIm createdUpdatedBayesIm(BayesPm updatedBayesPm) {
        return new MlBayesIm(updatedBayesPm, this.bayesIm, MlBayesIm.InitializationMethod.MANUAL);
    }

    private BayesPm createUpdatedBayesPm(Dag updatedGraph) {
        return new BayesPm(updatedGraph, this.bayesIm.getBayesPm());
    }

    private Dag createManipulatedGraph(Graph graph) {
        Dag updatedGraph = new Dag(graph);

        for (int i = 0; i < this.evidence.getNumNodes(); ++i) {
            if (this.evidence.isManipulated(i)) {
                Node node = updatedGraph.getNode(this.evidence.getNode(i).getName());
                List<Node> parents = updatedGraph.getParents(node);

                for (Node parent : parents) {
                    updatedGraph.removeEdge(parent, node);
                }
            }
        }

        return updatedGraph;
    }

    /**
     * Reads the state of the object from the provided {@code ObjectInputStream}.
     * Ensures that necessary fields are not null after deserialization
     * and reconstructs transient fields required for the object's functionality.
     *
     * @param s the {@code ObjectInputStream} to read data from.
     * @throws IOException            if an I/O error occurs during reading.
     * @throws ClassNotFoundException if a class required for deserialization cannot be found.
     * @throws NullPointerException   if key fields such as {@code bayesIm} or {@code evidence} are null after deserialization.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) throw new NullPointerException();
        if (this.evidence == null) throw new NullPointerException();

        // Rebuild transient JT engine on deserialize
        if (this.manipulatedBayesIm != null) {
            this.vei = new VariableEliminationInference(this.manipulatedBayesIm);
            syncJtiFromCurrentEvidence();
        }
    }
}