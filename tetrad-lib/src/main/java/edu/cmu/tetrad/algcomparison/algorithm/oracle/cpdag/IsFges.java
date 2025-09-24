///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.work_in_progress.ISBDeuScore;
import edu.cmu.tetrad.search.work_in_progress.ISScore;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * IS-FGES (Instance-Specific FGES) wrapper for the algcomparison interface.
 * Uses a discrete instance-specific score (ISBDeuScore) with test=row 0 of train (for now),
 * plus a population BDeu score for the base FGES machinery.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "IS-FGES",
        command = "is-fges",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class IsFges extends AbstractBootstrapAlgorithm implements Algorithm, HasKnowledge,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 1L;

    private Knowledge knowledge = new Knowledge();

    public IsFges() { }

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

        int row = parameters.getInt(Params.INSTANCE_ROW);
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
        edu.cmu.tetrad.search.work_in_progress.IsFges alg = new edu.cmu.tetrad.search.work_in_progress.IsFges(isScore, populationScore);
        alg.setKnowledge(this.knowledge);
        alg.setVerbose(parameters.getBoolean(Params.VERBOSE));
        alg.setOut(System.out);
        alg.setFaithfulnessAssumed(parameters.getBoolean(Params.FAITHFULNESS_ASSUMED));
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

    // --- simple column aligner (order by train variable names, keep train Node objects) ---
    private static DataSet alignByName(DataSet ref, DataSet other) {
        List<Integer> cols = new ArrayList<>(ref.getNumColumns());
        for (int i = 0; i < ref.getNumColumns(); i++) {
            String name = ref.getVariable(i).getName();
            int idx = other.getColumn(other.getVariable(name));
            if (idx < 0) throw new IllegalArgumentException("Instance dataset missing variable: " + name);
            cols.add(idx);
        }
        int[] colIdx = cols.stream().mapToInt(i -> i).toArray();
        DataSet projected = other.subsetColumns(colIdx);
        // ensure identical variable objects
        for (int i = 0; i < ref.getNumColumns(); i++) {
            projected.setVariable(i, ref.getVariable(i));
        }
        return projected;
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