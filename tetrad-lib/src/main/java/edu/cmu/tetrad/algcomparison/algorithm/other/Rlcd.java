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

package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.PoolsImputations;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.search.rlcd.StratifiedChi2RankTest;
import edu.cmu.tetrad.search.rlcd.TestwiseDeletionRankTest;
import edu.cmu.tetrad.util.StratifiedRankTests;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the RLCD algorithm (Rank-based Latent Causal Discovery; Dong et al., ICLR 2024), which learns a graph
 * over the observed variables and newly introduced latent variables from rank constraints on the covariance matrix.
 * The output graph contains the observed variables and the discovered latents, marked as latent nodes. Background
 * knowledge over the observed variables is honored in stage 1, restricts which observed variables may act as parents
 * in stage 2, and restores required observed edges; it cannot refer to the latents.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.Rlcd
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RLCD",
        command = "rlcd",
        algoType = AlgType.search_for_structure_over_latents,
        dataType = DataType.Continuous
)
public class Rlcd implements Algorithm, AcceptsKnowledge, PoolsImputations {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The background knowledge passed to the search.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new instance of the algorithm.
     */
    public Rlcd() {
    }

    /**
     * Runs RLCD on a continuous data set.
     *
     * @param dataModel  the data; must be a continuous tabular data set.
     * @param parameters the parameters.
     * @return the graph over observed and latent variables.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("RLCD requires a continuous tabular data set.");
        }

        boolean censor = parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false);
        MissingDataSpec spec = MissingDataUtils.fromParameters(parameters);
        boolean stratified = parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false);
        boolean anyMissing = new MissingDataAudit(dataSet).anyMissing();

        if (stratified && (censor || spec != null)) {
            throw new IllegalArgumentException("Stratified (domain-pooled) RLCD cannot be combined with a "
                                               + "missing-data policy or ceiling censoring: each sets the rank "
                                               + "test, and only one of them can. Turn one of the two off.");
        }

        if (anyMissing && !censor && spec == null) {
            // Without a policy, RLCD would invert a covariance matrix built from the data and fail deep in the
            // linear algebra with "Cannot invert a matrix with NaN values", which tells the user nothing about
            // what to do. Say it here instead, with the ways out and what each is for.
            throw new IllegalArgumentException("RLCD: the data have missing values and no missing-data policy is "
                                               + "set. Set missingDataPolicy to \"em\" (recommended: one covariance "
                                               + "matrix from all the incomplete rows, consistent when values are "
                                               + "missing at random; set missingEssMode to meanPairwise), to "
                                               + "\"testwise\" (each rank test from the rows complete on its own "
                                               + "columns; for few variables or little missingness), to "
                                               + "\"listwise\", or to \"mi\" with poolImputations. If values sit at "
                                               + "a ceiling such as 100, see rlcdCensorCeiling instead. Missing: "
                                               + TestwiseDeletionRankTest.describe(dataSet));
        }

        if (!stratified && censor) {
            edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(dataSet);
            double ceiling = parameters.getDouble(Params.RLCD_CEILING_VALUE, 100.0);
            CensoredMvnCovarianceEstimator estimator = new CensoredMvnCovarianceEstimator(dataSet,
                    CensoredMvnCovarianceEstimator.commonCeiling(dataSet.getNumColumns(), ceiling), null);
            estimator.setSeed(parameters.getLong(Params.SEED));
            MissingDataSpec.EffectiveSampleSizeMode ess = spec == null
                    ? MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE : spec.getEssMode();
            rlcd.setCensoredCovariance(estimator, ess);
            TetradLogger.getInstance().log("RLCD: censoring at " + ceiling + "; " + estimator.getCensoredCells()
                                           + " censored cells, " + estimator.getMissingCells()
                                           + " missing cells handled as missing at random.");
            return run(rlcd, parameters);
        }

        if (!stratified && spec != null && anyMissing) {
            if (spec.getPolicy() == MissingDataPolicy.MULTIPLE_IMPUTATION) {
                List<DataSet> imputations = new MvnImputer(spec).impute(dataSet, spec.getNumImputations(),
                        parameters.getLong(Params.SEED));
                List<DataModel> models = new ArrayList<>(imputations);
                return searchImputations(models, parameters);
            }
            edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(dataSet);
            rlcd.setMissingDataSpec(spec);
            return run(rlcd, parameters);
        }

        if (parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false)) {
            // Stratified (domain-pooled) mode, the Phase 1 upgrade of LCD-NOD (Dong et al., ICML 2026). RLCD
            // accepts continuous data, so here the domain index is an integer-valued continuous column, named by
            // the domainColumn parameter; it is removed from the variable list and used only to split the rows.
            String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet,
                    parameters.getString(Params.DOMAIN_COLUMN, "domain"));
            Node domain = dataSet.getVariable(domainColumn);
            List<DataSet> perDomain = StratifiedRankTests.split(dataSet, domain);

            int dcol = dataSet.getVariables().indexOf(domain);
            int[] keep = new int[dataSet.getNumColumns() - 1];
            int k = 0;
            for (int j = 0; j < dataSet.getNumColumns(); j++) if (j != dcol) keep[k++] = j;
            DataSet sub = dataSet.subsetColumns(keep);

            StringBuilder sizes = new StringBuilder();
            for (DataSet d : perDomain) {
                if (!sizes.isEmpty()) sizes.append(", ");
                sizes.append(d.getNumRows());
            }
            TetradLogger.getInstance().log("Stratified RLCD: domain column '" + domainColumn + "' removed from the "
                                           + "searched variables and used only to split the rows; " + perDomain.size()
                                           + " domains with n = " + sizes + ".");

            edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(sub);
            rlcd.setStratifiedRankTest(new StratifiedChi2RankTest(perDomain));
            return run(rlcd, parameters);
        }

        return run(new edu.cmu.tetrad.search.Rlcd(dataSet), parameters);
    }

    /**
     * Runs ONE RLCD search over several imputations of a data set with missing values; see
     * {@link edu.cmu.tetrad.search.Rlcd#Rlcd(List)}.
     *
     * @param imputations the imputed data sets; continuous tabular data sets with the same variables and rows.
     * @param parameters  the parameters.
     * @return the graph over observed and latent variables.
     */
    @Override
    public Graph searchImputations(List<DataModel> imputations, Parameters parameters) {
        if (parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false)) {
            throw new IllegalArgumentException("Ceiling censoring cannot be combined with pooled imputations: it "
                                               + "estimates its own covariance matrix from the data as they are. "
                                               + "Turn one of the two off.");
        }

        if (parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false)) {
            throw new IllegalArgumentException("Stratified (domain-pooled) RLCD cannot be combined with pooled "
                                               + "imputations: the strata must be independent samples, and "
                                               + "imputations share their rows. Turn one of the two off.");
        }

        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel dataModel : imputations) {
            if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
                throw new IllegalArgumentException("RLCD requires continuous tabular data sets.");
            }

            dataSets.add(dataSet);
        }

        return run(new edu.cmu.tetrad.search.Rlcd(dataSets), parameters);
    }

    private Graph run(edu.cmu.tetrad.search.Rlcd rlcd, Parameters parameters) {
        rlcd.setAlpha(parameters.getDouble(Params.ALPHA));
        rlcd.setMaxK(parameters.getInt(Params.RLCD_MAX_K));
        rlcd.setAllowNonLeafX(parameters.getBoolean(Params.RLCD_ALLOW_NON_LEAF_X));
        rlcd.setUnfoldCovers(parameters.getBoolean(Params.RLCD_UNFOLD_COVERS));
        rlcd.setCheckV(parameters.getBoolean(Params.RLCD_CHECK_V));
        rlcd.setLatentGate(parameters.getBoolean(Params.RLCD_LATENT_GATE));
        rlcd.setResolveTwoIndicatorLatents(parameters.getBoolean(Params.RLCD_RESOLVE_TWO_INDICATOR_LATENTS, false));
        rlcd.setKnowledgeInStage1(parameters.getBoolean(Params.RLCD_KNOWLEDGE_IN_STAGE1, true));
        rlcd.setObservedExplanationCheck(parameters.getBoolean(Params.RLCD_OBSERVED_EXPLANATION_CHECK, false),
                parameters.getDouble(Params.RLCD_OBSERVED_EXPLANATION_ALPHA, 0.05));
        rlcd.setPartitionCliqueThreshold(parameters.getInt(Params.RLCD_PARTITION_CLIQUE_THRESHOLD));
        rlcd.setStage1Method(parameters.getBoolean(Params.RLCD_STAGE1_NONE, false)
                ? edu.cmu.tetrad.search.Rlcd.Stage1Method.NONE
                : parameters.getBoolean(Params.RLCD_STAGE1_FGES)
                ? edu.cmu.tetrad.search.Rlcd.Stage1Method.FGES
                : edu.cmu.tetrad.search.Rlcd.Stage1Method.BOSS);
        rlcd.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT_DEFAULT_1));
        rlcd.setSeed(parameters.getLong(Params.SEED));
        rlcd.setVerbose(parameters.getBoolean(Params.VERBOSE));
        rlcd.setKnowledge(this.knowledge);

        try {
            return rlcd.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RLCD was interrupted.", e);
        }
    }

    /**
     * The true graph itself is used for comparison, since the output includes latent nodes.
     *
     * @param graph the true graph.
     * @return a copy of it.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "RLCD (Rank-based Latent Causal Discovery)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.RLCD_MAX_K);
        parameters.add(Params.RLCD_ALLOW_NON_LEAF_X);
        parameters.add(Params.RLCD_UNFOLD_COVERS);
        parameters.add(Params.RLCD_CHECK_V);
        parameters.add(Params.RLCD_LATENT_GATE);
        parameters.add(Params.RLCD_RESOLVE_TWO_INDICATOR_LATENTS);
        parameters.add(Params.RLCD_KNOWLEDGE_IN_STAGE1);
        parameters.add(Params.RLCD_OBSERVED_EXPLANATION_CHECK);
        parameters.add(Params.RLCD_OBSERVED_EXPLANATION_ALPHA);
        parameters.add(Params.RLCD_PARTITION_CLIQUE_THRESHOLD);
        parameters.add(Params.RLCD_STAGE1_FGES);
        parameters.add(Params.RLCD_STAGE1_NONE);
        parameters.add(Params.RLCD_CENSOR_CEILING);
        parameters.add(Params.RLCD_CEILING_VALUE);
        parameters.add(Params.MISSING_DATA_POLICY);
        parameters.add(Params.MISSING_ESS_MODE);
        parameters.add(Params.MISSING_NUM_IMPUTATIONS);
        parameters.add(Params.MISSING_EM_RIDGE);
        parameters.add(Params.MISSING_EM_TOLERANCE);
        parameters.add(Params.MISSING_EM_MAX_ITERATIONS);
        parameters.add(Params.STRATIFY_BY_DOMAIN);
        parameters.add(Params.DOMAIN_COLUMN);
        parameters.add(Params.POOL_IMPUTATIONS);
        parameters.add(Params.PENALTY_DISCOUNT_DEFAULT_1);
        parameters.add(Params.SEED);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
