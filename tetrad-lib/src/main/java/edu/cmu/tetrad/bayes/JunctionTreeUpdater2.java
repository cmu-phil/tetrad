package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.List;

/**
 * RowSummingExactUpdater-style updater plumbing, but uses JunctionTreeInference
 * for marginals/joints/conditionals. All evidence and BayesIm alignment behavior
 * is inherited from the RowSummingExactUpdater design.
 */
public final class JunctionTreeUpdater2 implements ManipulatingBayesUpdater {
    @Serial
    private static final long serialVersionUID = 23L;

    private final BayesIm bayesIm;

    /**
     * Stores evidence for all variables (Indexed to SOURCE variable list).
     *
     * IMPORTANT: This is intentionally NOT defensively copied, matching
     * RowSummingExactUpdater behavior. The UI may mutate Evidence in-place.
     */
    private Evidence evidence;

    private BayesIm manipulatedBayesIm;
    private BayesIm updatedBayesIm;

    /**
     * Junction tree inference engine over manipulatedBayesIm.
     * (manipulations are already baked into manipulatedBayesIm CPTs via do()-surgery)
     */
    private transient JunctionTreeInference jti;

    //==============================CONSTRUCTORS===========================//

    public JunctionTreeUpdater2(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException();
        this.bayesIm = bayesIm;
        setEvidence(Evidence.tautology(bayesIm));
    }

    public JunctionTreeUpdater2(BayesIm bayesIm, Evidence evidence) {
        if (bayesIm == null) throw new NullPointerException();
        this.bayesIm = bayesIm;
        setEvidence(evidence);
    }

    public static JunctionTreeUpdater2 serializableInstance() {
        return new JunctionTreeUpdater2(MlBayesIm.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//

    @Override
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    @Override
    public BayesIm getManipulatedBayesIm() {
        return this.manipulatedBayesIm;
    }

    @Override
    public Graph getManipulatedGraph() {
        return getManipulatedBayesIm().getDag();
    }

    @Override
    public BayesIm getUpdatedBayesIm() {
        if (this.updatedBayesIm == null) {
            updateAll();
        }
        return this.updatedBayesIm;
    }

    @Override
    public Evidence getEvidence() {
        return new Evidence(this.evidence);
    }

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
        this.jti = new JunctionTreeInference(this.manipulatedBayesIm);

        // IMPORTANT: do not compute updatedBayesIm eagerly; updateAll() will do it if asked.
        this.updatedBayesIm = null;

        // Sync current evidence into JT engine (allowed categories + optional singleton hard evidence)
        syncJtiFromCurrentEvidence();
    }

    @Override
    public boolean isJointMarginalSupported() {
        return true;
    }

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
        return this.jti.getJointProbability(dstVars, values);
    }

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

        return this.jti.getMarginal(dstVar, value);
    }

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

    @Override
    public String toString() {
        return "JunctionTreeUpdater2 (RowSumming plumbing + JT inference), evidence = " + this.evidence;
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * Sync the current Evidence into the JT engine.
     *
     * This is intentionally called before queries because Evidence may be mutated
     * in place by the UI (RowSumming semantics). This keeps JT correct without relying
     * on UI calling setEvidence().
     */
    private void syncJtiFromCurrentEvidence() {
        if (this.jti == null || this.manipulatedBayesIm == null || this.evidence == null) return;

        // Evidence re-indexed to manipulated IM
        Evidence ev2 = new Evidence(this.evidence, this.manipulatedBayesIm);
        Proposition p2 = ev2.getProposition();

        // Soft evidence: allowed categories
        this.jti.setAllowedCategories(p2);

//        // Optional: singleton hard evidence. If enabled, you MUST clear old singleton evidence when not singleton.
//        for (int i = 0; i < ev2.getNumNodes(); i++) {
//            int only = getOnlyAllowedCategoryOrMinusOne(p2, i, ev2.getNumCategories(i));
//            this.jti.setEvidence(i, only); // assumes setEvidence(i, -1) clears; if not, we can add an explicit clear API
//        }
    }

//    /**
//     * Returns the only allowed category for a node if exactly one category is allowed; else -1.
//     */
//    private static int getOnlyAllowedCategoryOrMinusOne(Proposition p, int node, int numCats) {
//        int only = -1;
//        for (int c = 0; c < numCats; c++) {
//            if (p.isAllowed(node, c)) {
//                if (only >= 0) return -1;
//                only = c;
//            }
//        }
//        return only;
//    }

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
     *
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
                    cond = this.jti.getConditional(node, parents, parentValues);
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

                    double p = (parents.length > 0) ? cond[col] : this.jti.getMarginal(node, col);
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

    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) throw new NullPointerException();
        if (this.evidence == null) throw new NullPointerException();

        // Rebuild transient JT engine on deserialize
        if (this.manipulatedBayesIm != null) {
            this.jti = new JunctionTreeInference(this.manipulatedBayesIm);
            syncJtiFromCurrentEvidence();
        }
    }
}