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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.work_in_progress.IsBDeuScore;
import edu.cmu.tetrad.search.is.IsScore;
import edu.cmu.tetrad.search.is.IsGFci;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * IS-GFCI (Instance-Specific GFCI) wrapper for the algcomparison interface. Uses a discrete instance-specific score
 * (ISBDeuScore), a score for FGES, plus a population BDeu score for the base FGES machinery.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IS-GFCI",
        command = "is-gfci",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class IsGfci extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The score to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for Fges.</p>
     */
    public IsGfci() {

    }

    /**
     * <p>Constructor for Fges.</p>
     *
     * @param test a {@link ScoreWrapper} object
     */
    public IsGfci(IndependenceWrapper test) {
        this.test = test;
    }

    /**
     * Aligns 'other' to have exactly the variables and order of 'ref', by name.
     */
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

            if (refVar instanceof edu.cmu.tetrad.data.DiscreteVariable tv &&
                projected.getVariable(j) instanceof edu.cmu.tetrad.data.DiscreteVariable iv) {

                List<String> tLabels = tv.getCategories();
                List<String> iLabels = iv.getCategories();

                if (!tLabels.equals(iLabels)) {
                    // Same sets?
                    if (!(new java.util.HashSet<>(tLabels).equals(new java.util.HashSet<>(iLabels)))) {
                        throw new IllegalArgumentException(
                                "Discrete categories differ for '" + tv.getName() +
                                "'. Train=" + tLabels + ", Test=" + iLabels);
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
                            throw new IllegalArgumentException("Out-of-range category at row " + r +
                                                               ", var " + tv.getName());
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
        if (this.test == null) {
            throw new IllegalStateException("IndependenceWrapper not set for IS-GFCI.");
        }

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

        // Align columns + variable objects
        DataSet aligned = alignByName(train, testDs);
        DataSet testCase = aligned.subsetRows(new int[]{row});

        // Build instance-specific score (ISBDeuScore falls back to population-only if testCase == null)
        IsScore isScore = new IsBDeuScore(train, testCase);

        // Population score (use algcomparison wrapper consistently)
        Score populationScore = new BdeuScore().getScore(dataModel, parameters);

        // Independence test on the training data
        IndependenceTest independenceTest = this.test.getTest(dataModel, parameters);

        // IGFCI driver (constructor signature must match your work_in_progress IGFci)
        IsGFci search =
                new IsGFci(independenceTest, isScore, populationScore);

        search.setKnowledge(this.knowledge);
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setMaxDegree(parameters.getInt(Params.MAX_DEGREE));
        search.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
        search.setOut(System.out);

        try {
            return search.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        String t = (this.test != null) ? this.test.getDescription() : "<?> test";
        return "IS-GFCI (Instance-Specific GFCI) using ISBDeu and " + t;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}

