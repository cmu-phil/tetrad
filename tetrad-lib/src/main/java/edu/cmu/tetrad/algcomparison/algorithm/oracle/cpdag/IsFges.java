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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ReturnsBootstrapGraphs;
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.work_in_progress.ISBDeuScore;
import edu.cmu.tetrad.search.ISScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * IS-FGES (Instance-Specific FGES) wrapper for the algcomparison interface. Uses a discrete instance-specific score
 * (ISBDeuScore) with test=row 0 of train (for now), plus a population BDeu score for the base FGES machinery.
 */
@edu.cmu.tetrad.annotation.Algorithm(name = "IS-FGES", command = "is-fges", algoType = AlgType.forbid_latent_common_causes)
@Bootstrapping
public class IsFges extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge, ReturnsBootstrapGraphs,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Represents the prior knowledge constraints used by the algorithm. This object
     * manages structural knowledge such as required edges, forbidden edges, and other
     * constraints that guide the search process in the algorithm. Should also
     * contain a test dataset. Needed for reflection.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Default constructor for the IsFges class.
     * Initializes an instance of the IsFges algorithm. This algorithm
     * is designed for structure learning in causal discovery frameworks.
     */
    public IsFges() {
    }

    // --- simple column aligner (order by train variable names, keep train Node objects) ---
    private static DataSet alignByName(DataSet ref, DataSet other) {
        // Map ref var names -> column indices in `other`
        List<Node> refVars = ref.getVariables();
        int p = refVars.size();
        int[] cols = new int[p];

        for (int i = 0; i < p; i++) {
            String name = refVars.get(i).getName();
            Node instVar = other.getVariable(name);
            if (instVar == null) {
                throw new IllegalArgumentException("Instance dataset missing variable: " + name);
            }
            cols[i] = other.getColumn(instVar);
        }

        DataSet projected = other.subsetColumns(cols);

        // Ensure variable *objects* match ref, and (if discrete) remap category indices
        for (int j = 0; j < p; j++) {
            Node refVar = refVars.get(j);
            projected.setVariable(j, refVar); // unify Node identity

            if (refVar instanceof edu.cmu.tetrad.data.DiscreteVariable tv && projected.getVariable(j)
                    instanceof edu.cmu.tetrad.data.DiscreteVariable iv) {

                List<String> tLabels = tv.getCategories();
                List<String> iLabels = iv.getCategories();

                if (!tLabels.equals(iLabels)) {
                    // Same sets?
                    if (!(new java.util.HashSet<>(tLabels).equals(new java.util.HashSet<>(iLabels)))) {
                        throw new IllegalArgumentException("Discrete categories differ for '" + tv.getName()
                                                           + "'. Train=" + tLabels + ", Test=" + iLabels);
                    }
                    // Build remap: instanceIndex -> trainIndex
                    int K = iLabels.size();
                    int[] remap = new int[K];
                    java.util.Map<String, Integer> trainIdx = new java.util.HashMap<>();
                    for (int k = 0; k < K; k++) trainIdx.put(tLabels.get(k), k);
                    for (int k = 0; k < K; k++) remap[k] = trainIdx.get(iLabels.get(k));

                    for (int r = 0; r < projected.getNumRows(); r++) {
                        int v = projected.getInt(r, j);
                        if (v == -99) continue; // preserve your missing sentinel
                        if (v < 0 || v >= K) {
                            throw new IllegalArgumentException("Out-of-range category at row " + r + ", var "
                                                               + tv.getName());
                        }
                        projected.setInt(r, j, remap[v]);
                    }
                }
            }
        }

        return projected;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet train)) {
            throw new IllegalArgumentException("IS-FGES requires a tabular DataSet.");
        }

        if (!train.isDiscrete()) {
            throw new IllegalArgumentException("Training data must be discrete.");
        }

        // Instance dataset & row (hardwired now; wire to knowledge later)
        DataSet testDs = train;

        if (knowledge != null && knowledge.getTestingData() != null) {
            testDs = knowledge.getTestingData();
        }

        if (!testDs.isDiscrete()) {
            throw new IllegalArgumentException("Testing data must be discrete.");
        }

        int row = parameters.getInt(Params.INSTANCE_ROW, 0);
        if (row < 0 || row >= testDs.getNumRows()) {
            throw new IllegalArgumentException("Instance row out of range: " + row);
        }

        DataSet aligned = alignByName(train, testDs);
        DataSet testCase = aligned.subsetRows(new int[]{row});

        // Instance-specific score (discrete). If you add continuous support, branch here.
        if (train.isContinuous()) {
            throw new IllegalArgumentException("ISBDeuScore requires discrete data; got continuous.");
        }
        ISScore isScore = new ISBDeuScore(train, testCase);

        // Population score (discrete BDeu).
        Score populationScore = new edu.cmu.tetrad.algcomparison.score.BdeuScore().getScore(dataModel, parameters);

        // Driver
        edu.cmu.tetrad.search.IsFges alg = new edu.cmu.tetrad.search.IsFges(isScore,
                populationScore);
        alg.setKnowledge(this.knowledge);
        alg.setVerbose(parameters.getBoolean(Params.VERBOSE));
        alg.setOut(System.out);
        alg.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
        alg.setSymmetricFirstStep(false);
        if (parameters.getInt(Params.MAX_DEGREE) >= 0) {
            alg.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        }
        if (parameters.getInt(Params.NUM_THREADS) > 0) {
            alg.setNumThreads(parameters.getInt(Params.NUM_THREADS));
        }
        // Optional: if you want the symmetric first step exposed as a param later:
        // alg.setSymmetricFirstStep(parameters.getBoolean(Params.SYMMETRIC_FIRST_STEP));

        try {
            return alg.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Boilerplate required by the framework ----

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "IS-FGES (Instance-Specific FGES) using ISBDeu for the instance row and BDeu for the population.";
    }

    // ISBDeu is discrete; report discrete so the UI can filter appropriately.
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.MAX_DEGREE);
        parameters.add(Params.NUM_THREADS);
        parameters.add(Params.FAITHFULNESS_ASSUMED);
        parameters.add(Params.INSTANCE_ROW);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}