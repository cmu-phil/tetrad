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

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.algcomparison.utils.MultiDataSetScoreWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Basis Function BIC Score (Basis-BIC) version.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Score(name = "BF-BIC (Basis Function BIC)", command = "bf-bic-score", dataType = DataType.Mixed)
@Mixed
@General
//@Experimental
public class BasisFunctionBicScore implements ScoreWrapper, MultiDataSetScoreWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data set.
     */
    private DataModel dataSet;

    /**
     * Initializes a new instance of the BasisFunctionBicScore class.
     */
    public BasisFunctionBicScore() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, false, "BF-BIC (Basis Function BIC)");
        this.dataSet = dataSet;

        // Changes from the pre-2026-8 implementation: the singularity lambda was previously read
        // from Params.REGULARIZATION_LAMBDA, while callers such as py-tetrad set
        // Params.SINGULARITY_LAMBDA (the key used by the BF-LRT wrapper and matching the score
        // constructor's documentation), so the setting was silently ignored. The wrapper now
        // reads SINGULARITY_LAMBDA. DO_ONE_EQUATION_ONLY was previously accepted but never
        // forwarded to the score; it is now applied.
        edu.cmu.tetrad.search.score.BasisFunctionBicScore score = new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT),
                parameters.getDouble(Params.SINGULARITY_LAMBDA),
                parameters.getBoolean(Params.ADAPTIVE_BASIS_SELECTION));
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setDoOneEquationOnly(parameters.getBoolean(Params.DO_ONE_EQUATION_ONLY));
        return score;
    }

    /**
     * {@inheritDoc}
     *
     * <p>With adaptive basis selection enabled and more than one data set, the basis-column
     * decision is made ONCE for all data sets: each data set's adaptive decision is computed
     * separately and the union of kept columns (per variable, in the original column order)
     * is used as the common embedding for every score. A column that is informative in ANY
     * data set is kept for all, so no data set loses expressiveness, and every data set
     * scores the identical parameterization - which is what a common-model algorithm such as
     * IMaGES requires of a summed score. Without this, different data sets can keep
     * different columns, and the same edge is then scored against different response bases
     * in different data sets. All data sets must have the same variables (same names, types,
     * and discrete categories), which multi-data-set algorithms require anyway; this is
     * checked via the unpruned embedding layouts.
     */
    @Override
    public java.util.List<Score> getScores(java.util.List<DataModel> dataModels, Parameters parameters) {
        java.util.List<Score> scores = new java.util.ArrayList<>();

        boolean adaptive = parameters.getBoolean(Params.ADAPTIVE_BASIS_SELECTION);
        if (!adaptive || dataModels.size() <= 1) {
            for (DataModel dataModel : dataModels) {
                scores.add(getScore(dataModel, parameters));
            }
            return scores;
        }

        int truncationLimit = parameters.getInt(Params.TRUNCATION_LIMIT);

        // Gate each data set exactly as getScore does, so the decision is made on the same
        // data the scores will see.
        java.util.List<DataSet> gated = new java.util.ArrayList<>();
        for (DataModel dataModel : dataModels) {
            DataModel gatedModel = MissingDataUtils.gate(dataModel, parameters, false, "BF-BIC (Basis Function BIC)");
            gated.add(SimpleDataLoader.getMixedDataSet(gatedModel));
        }

        // Verify identical unpruned layouts, then union the per-data-set decisions.
        java.util.Map<Integer, java.util.List<Integer>> layout =
                edu.cmu.tetrad.search.score.BasisFunctionBicScore.fullEmbedding(gated.get(0), truncationLimit);
        for (int d = 1; d < gated.size(); d++) {
            java.util.Map<Integer, java.util.List<Integer>> other =
                    edu.cmu.tetrad.search.score.BasisFunctionBicScore.fullEmbedding(gated.get(d), truncationLimit);
            if (!layout.equals(other)) {
                throw new IllegalArgumentException("Data sets have different embedding layouts; a common basis "
                        + "cannot be shared. Multi-data-set algorithms require the same variables (names, types, "
                        + "and discrete categories) in every data set.");
            }
        }

        java.util.Set<Integer> keptUnion = new java.util.HashSet<>();
        for (DataSet dataSet : gated) {
            java.util.Map<Integer, java.util.List<Integer>> decision =
                    edu.cmu.tetrad.search.score.BasisFunctionBicScore.adaptivePrunedEmbedding(dataSet, truncationLimit);
            for (java.util.List<Integer> cols : decision.values()) {
                keptUnion.addAll(cols);
            }
        }

        java.util.Map<Integer, java.util.List<Integer>> common = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> e : layout.entrySet()) {
            java.util.List<Integer> kept = new java.util.ArrayList<>();
            for (Integer c : e.getValue()) {
                if (keptUnion.contains(c)) kept.add(c);
            }
            common.put(e.getKey(), kept);
        }

        for (DataSet dataSet : gated) {
            edu.cmu.tetrad.search.score.BasisFunctionBicScore score =
                    new edu.cmu.tetrad.search.score.BasisFunctionBicScore(
                            dataSet,
                            truncationLimit,
                            parameters.getDouble(Params.SINGULARITY_LAMBDA),
                            common);
            score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            score.setDoOneEquationOnly(parameters.getBoolean(Params.DO_ONE_EQUATION_ONLY));
            scores.add(score);
        }
        return scores;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BF BIC";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.TRUNCATION_LIMIT);
        parameters.add(Params.ADAPTIVE_BASIS_SELECTION);
        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.SINGULARITY_LAMBDA);
        parameters.add(Params.DO_ONE_EQUATION_ONLY);
        parameters.add(Params.MISSING_DATA_POLICY);
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}

