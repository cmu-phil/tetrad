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
import edu.cmu.tetrad.algcomparison.algorithm.TakesCovarianceMatrix;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.PoolsImputations;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.data.missing.PairwiseCovarianceEstimator;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.rlcd.StratifiedChi2RankTest;
import edu.cmu.tetrad.search.rlcd.TestwiseDeletionRankTest;
import edu.cmu.tetrad.util.StratifiedRankTests;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.search.rlcd.RlcdStabilitySummary;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Wrapper for the RLCD algorithm (Rank-based Latent Causal Discovery; Dong et al., ICLR 2024), which learns a graph
 * over the observed variables and newly introduced latent variables from rank constraints on the covariance matrix.
 * The output graph contains the observed variables and the discovered latents, marked as latent nodes. Background
 * knowledge over the observed variables is honored in stage 1, restricts which observed variables may act as parents
 * in stage 2, and restores required observed edges; it cannot refer to the latents.
 * <p>
 * The input may be a continuous data set or a covariance matrix with its sample size; both stages are functions of
 * the covariance matrix and the sample size alone. Over a covariance matrix the options that need rows are refused
 * with a message: the missing-data policies, ceiling censoring, stratification, and pooled imputations; the stability
 * summary, which resamples rows, is skipped with a note in the log.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.search.Rlcd
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "RLCD",
        command = "rlcd",
        algoType = AlgType.search_for_structure_over_latents,
        dataType = {DataType.Continuous, DataType.Mixed, DataType.Covariance}
)
@Experimental
public class Rlcd implements Algorithm, AcceptsKnowledge, PoolsImputations, TakesCovarianceMatrix {
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
     * Runs RLCD on a continuous data set or a covariance matrix.
     *
     * @param dataModel  the data: a continuous tabular data set, or a covariance matrix with its sample size.
     * @param parameters the parameters.
     * @return the graph over observed and latent variables.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (dataModel instanceof ICovarianceMatrix covariance) {
            return searchCovariance(covariance, parameters);
        }

        if (!(dataModel instanceof DataSet given)) {
            throw new IllegalArgumentException("RLCD requires a tabular data set or a covariance matrix.");
        }

        DataSet dataSet = given;
        if (!dataSet.isContinuous()) {
            // The one discrete column RLCD can take is the domain column of stratified mode; it is recoded to its
            // category indices, since RLCD is otherwise a continuous-data method.
            if (!parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false)) {
                throw new IllegalArgumentException("RLCD requires continuous data. A discrete column is accepted "
                                                   + "only as the domain column with stratifyByDomain on.");
            }
            String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet,
                    parameters.getString(Params.DOMAIN_COLUMN, "domain"));
            try {
                dataSet = StratifiedRankTests.withContinuousDomain(dataSet, domainColumn);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("RLCD: " + e.getMessage() + " RLCD requires continuous data "
                                                   + "apart from the domain column.", e);
            }
        }

        final DataSet data = dataSet;
        Graph graph = searchOnce(data, parameters);

        int stabilityRuns = parameters.getInt(Params.RLCD_STABILITY_RUNS, 0);
        if (stabilityRuns > 0) {
            stabilitySummary(graph, parameters, stabilityRuns,
                    random -> searchOnce(data.subsetRows(bootstrapRows(data.getNumRows(), random)), parameters));
        }

        return graph;
    }

    /**
     * One RLCD search over a covariance matrix. The options that need the data rows are refused by name, so that a
     * setting left on from a run over data does not silently do nothing.
     */
    private Graph searchCovariance(ICovarianceMatrix covariance, Parameters parameters) {
        List<String> needRows = new ArrayList<>();
        if (parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false)) needRows.add("stratifyByDomain");
        if (parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false)) needRows.add("rlcdCensorCeiling");
        if (MissingDataUtils.fromParameters(parameters) != null) needRows.add("missingDataPolicy");
        if (parameters.getBoolean(Params.POOL_IMPUTATIONS, false)) needRows.add("poolImputations");
        if (!needRows.isEmpty()) {
            throw new IllegalArgumentException("RLCD: the input is a covariance matrix, which has no rows, so these "
                                               + "settings cannot apply: " + needRows + ". Turn them off, or give "
                                               + "RLCD the data set instead.");
        }

        if (parameters.getInt(Params.RLCD_STABILITY_RUNS, 0) > 0) {
            TetradLogger.getInstance().log("RLCD: the stability summary resamples rows and is skipped over a "
                                           + "covariance matrix.");
        }

        TetradLogger.getInstance().log("RLCD over a covariance matrix, n = " + covariance.getSampleSize()
                                       + ". Every rank test is calibrated by that sample size.");
        return run(new edu.cmu.tetrad.search.Rlcd(covariance), parameters);
    }

    /**
     * Resampled runs for the stability summary: bootstrap the rows, run the same configuration, tally. Bounded by
     * the run count, by the time limit (checked between runs), and by the interface's stop button (also between
     * runs). No run is cut off part way, and the main graph is never altered; the summary goes to the log and one
     * line of it to the graph's attributes.
     */
    private void stabilitySummary(Graph graph, Parameters parameters, int runs, Resampler resampler) {
        long limitMillis = Math.round(parameters.getDouble(Params.RLCD_STABILITY_TIME_LIMIT, 300.0) * 1000.0);
        long start = System.currentTimeMillis();
        long seed = parameters.getLong(Params.SEED);
        List<Graph> sampled = new ArrayList<>();
        String stoppedBy = null;

        for (int r = 0; r < runs; r++) {
            if (Thread.currentThread().isInterrupted()) {
                stoppedBy = "the stop button";
                break;
            }
            long elapsed = System.currentTimeMillis() - start;
            if (r > 0 && elapsed + elapsed / r > limitMillis) {
                // the next run would, on the average so far, overrun the budget.
                stoppedBy = "the time limit of " + (limitMillis / 1000) + " s";
                break;
            }
            try {
                sampled.add(resampler.run(new Random(seed == -1 ? r : seed + r + 1)));
            } catch (RuntimeException e) {
                if (e.getCause() instanceof InterruptedException) {
                    stoppedBy = "the stop button";
                    break;
                }
                TetradLogger.getInstance().log("RLCD stability run " + (r + 1) + " failed and was skipped: " + e);
            }
        }

        RlcdStabilitySummary summary = new RlcdStabilitySummary(graph, sampled);
        String report = summary.report();
        if (stoppedBy != null) {
            report += "Stopped by " + stoppedBy + " after " + sampled.size() + " of " + runs + " requested runs.\n";
        }
        TetradLogger.getInstance().log(report);
        graph.addAttribute("RLCD stability", summary.oneLine()
                                             + (stoppedBy == null ? "" : " (" + sampled.size() + " of " + runs
                                                                         + " requested runs completed)"));
    }

    /**
     * Runs the search once on a bootstrap resample drawn with the given generator.
     */
    private interface Resampler {
        Graph run(Random random);
    }

    private static int[] bootstrapRows(int n, Random random) {
        int[] rows = new int[n];
        for (int i = 0; i < n; i++) rows[i] = random.nextInt(n);
        return rows;
    }

    /**
     * One RLCD search on the data as given, in whatever configuration the parameters select.
     */
    private Graph searchOnce(DataSet dataSet, Parameters parameters) {
        boolean censor = parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false);
        MissingDataSpec spec = MissingDataUtils.fromParameters(parameters);
        boolean stratified = parameters.getBoolean(Params.STRATIFY_BY_DOMAIN, false);
        boolean anyMissing = new MissingDataAudit(dataSet).anyMissing();

        if (stratified && spec != null && (spec.getPolicy() == MissingDataPolicy.TESTWISE
                                          || spec.getPolicy() == MissingDataPolicy.MULTIPLE_IMPUTATION)) {
            throw new IllegalArgumentException("Stratified (domain-pooled) RLCD cannot be combined with the "
                                               + "testwise or multiple-imputation missing-data policies: each sets "
                                               + "the rank test, and only one of them can. With stratification, use "
                                               + "\"em\", \"pairwise\" or \"listwise\" (each applied within "
                                               + "every domain), or ceiling censoring.");
        }

        if (stratified) {
            // Stratified (domain-pooled) mode, the Phase 1 upgrade of LCD-NOD (Dong et al., ICML 2026). RLCD
            // accepts continuous data, so here the domain index is an integer-valued continuous column, named by
            // the domainColumn parameter; it is removed from the variable list and used only to split the rows.
            // Missing values and ceiling censoring are handled within each domain: the per-domain covariance is
            // estimated by the chosen method, and the per-domain rank statistics are pooled as usual.
            String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet,
                    parameters.getString(Params.DOMAIN_COLUMN, "domain"));
            Node domain = dataSet.getVariable(domainColumn);
            List<DataSet> perDomain = StratifiedRankTests.split(dataSet, domain);

            int dcol = dataSet.getVariables().indexOf(domain);
            int[] keep = new int[dataSet.getNumColumns() - 1];
            int k = 0;
            for (int j = 0; j < dataSet.getNumColumns(); j++) if (j != dcol) keep[k++] = j;
            DataSet sub = dataSet.subsetColumns(keep);

            if (anyMissing && !censor && spec == null) {
                throw new IllegalArgumentException("RLCD: the data have missing values and no missing-data policy "
                                                   + "is set. With stratification, set missingDataPolicy to "
                                                   + "\"em\" (recommended), \"pairwise\" or \"listwise\", each "
                                                   + "applied within every domain, or turn on rlcdCensorCeiling if "
                                                   + "values sit at a ceiling. Missing: "
                                                   + TestwiseDeletionRankTest.describe(dataSet));
            }

            List<ICovarianceMatrix> covariances = perDomainCovariances(perDomain, parameters);
            StringBuilder sizes = new StringBuilder();
            for (int t = 0; t < perDomain.size(); t++) {
                if (!sizes.isEmpty()) sizes.append(", ");
                sizes.append(perDomain.get(t).getNumRows());
                if (covariances.get(t).getSampleSize() != perDomain.get(t).getNumRows()) {
                    sizes.append(" (ess ").append(covariances.get(t).getSampleSize()).append(')');
                }
            }
            TetradLogger.getInstance().log("Stratified RLCD: domain column '" + domainColumn + "' removed from the "
                                           + "searched variables and used only to split the rows; " + perDomain.size()
                                           + " domains with n = " + sizes + "; per-domain covariance by "
                                           + perDomainCovarianceMethod(parameters) + ".");

            return searchStratified(sub, StratifiedChi2RankTest.fromCovariances(covariances), parameters);
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

        Graph graph = run(new edu.cmu.tetrad.search.Rlcd(dataSets), parameters);

        int stabilityRuns = parameters.getInt(Params.RLCD_STABILITY_RUNS, 0);
        if (stabilityRuns > 0) {
            stabilitySummary(graph, parameters, stabilityRuns, random -> {
                int[] rows = bootstrapRows(dataSets.get(0).getNumRows(), random);
                List<DataSet> resampled = new ArrayList<>();
                for (DataSet d : dataSets) resampled.add(d.subsetRows(rows));
                return run(new edu.cmu.tetrad.search.Rlcd(resampled), parameters);
            });
        }

        return graph;
    }

    /**
     * Runs stratified RLCD over the given variables with a pooled rank test already built from the per-domain
     * covariances, with the RLCD parameters applied. Used by this wrapper and by LCD-NOD, which builds the per-domain
     * covariances once and shares them with its second phase.
     *
     * @param sub        the data set over the searched variables (the domain column removed); it supplies the
     *                   variables, and may contain missing values, since the rank tests and the stage-1 covariance
     *                   come from the pooled test.
     * @param stratified the pooled test, over the same variables in the same order.
     * @param parameters the RLCD parameters.
     * @return the graph.
     */
    public Graph searchStratified(DataSet sub, StratifiedChi2RankTest stratified, Parameters parameters) {
        edu.cmu.tetrad.search.Rlcd rlcd = new edu.cmu.tetrad.search.Rlcd(sub);
        rlcd.setStratifiedRankTest(stratified);
        return run(rlcd, parameters);
    }

    /**
     * The per-domain covariance matrices for stratified mode, each carrying the sample size its rank statistics are
     * scaled by: the sample covariance when the domain is complete and censoring is off; otherwise, by the
     * parameters, a censored (ceiling) MVN estimate ({@code rlcdCensorCeiling}, {@code rlcdCeilingValue}), an EM
     * estimate ({@code missingDataPolicy} "em"), an available-case estimate ("pairwise"), or the covariance of the
     * complete rows ("listwise"). For EM, pairwise and censored estimates the sample size is the effective sample
     * size by {@code missingEssMode} (mean pairwise complete count by default).
     *
     * @param perDomain  the per-domain data sets (domain column removed), as from
     *                   {@link StratifiedRankTests#split(DataSet, Node)}.
     * @param parameters the parameters.
     * @return the covariance matrices, in the order of the domains.
     */
    public static List<ICovarianceMatrix> perDomainCovariances(List<DataSet> perDomain, Parameters parameters) {
        boolean censor = parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false);
        MissingDataSpec spec = MissingDataUtils.fromParameters(parameters);
        MissingDataSpec.EffectiveSampleSizeMode essMode = spec == null
                ? MissingDataSpec.EffectiveSampleSizeMode.MEAN_PAIRWISE : spec.getEssMode();
        long seed = parameters.getLong(Params.SEED, -1L);

        List<ICovarianceMatrix> out = new ArrayList<>();
        for (int t = 0; t < perDomain.size(); t++) {
            DataSet d = perDomain.get(t);
            boolean missing = new MissingDataAudit(d).anyMissing();

            if (censor) {
                double ceiling = parameters.getDouble(Params.RLCD_CEILING_VALUE, 100.0);
                CensoredMvnCovarianceEstimator estimator = new CensoredMvnCovarianceEstimator(d,
                        CensoredMvnCovarianceEstimator.commonCeiling(d.getNumColumns(), ceiling), null);
                if (seed >= 0) estimator.setSeed(seed + t);
                ICovarianceMatrix cov = estimator.estimate();
                int ess = effectiveSampleSize(estimator.getPairwiseCounts(), d.getNumRows(), essMode);
                out.add(new CovarianceMatrix(new ArrayList<>(d.getVariables()), cov.getMatrix(), ess));
                TetradLogger.getInstance().log("Stratified RLCD, domain " + (t + 1) + ": censored covariance, "
                                               + estimator.getCensoredCells() + " censored cells, "
                                               + estimator.getMissingCells() + " missing cells, ess " + ess + ".");
            } else if (!missing || spec == null) {
                out.add(new CovarianceMatrix(d));
            } else {
                switch (spec.getPolicy()) {
                    case LISTWISE -> out.add(new CovarianceMatrix(MissingDataUtils.listwiseDelete(d)));
                    case EM_COVARIANCE -> {
                        EmCovarianceEstimator estimator = new EmCovarianceEstimator(d);
                        estimator.setRidge(spec.getEmRidge());
                        estimator.setTolerance(spec.getEmTolerance());
                        estimator.setMaxIterations(spec.getEmMaxIterations());
                        ICovarianceMatrix cov = estimator.estimate();
                        int ess = effectiveSampleSize(estimator.getPairwiseCounts(), d.getNumRows(), essMode);
                        out.add(new CovarianceMatrix(new ArrayList<>(d.getVariables()), cov.getMatrix(), ess));
                        TetradLogger.getInstance().log("Stratified RLCD, domain " + (t + 1) + ": EM covariance, "
                                                       + estimator.getNumIterations() + " iterations, converged "
                                                       + estimator.isConverged() + ", ess " + ess + ".");
                    }
                    case PAIRWISE_COVARIANCE -> {
                        ICovarianceMatrix cov = PairwiseCovarianceEstimator.estimate(d);
                        int ess = effectiveSampleSize(new MissingDataAudit(d).getPairwiseCompleteCounts(),
                                d.getNumRows(), essMode);
                        out.add(new CovarianceMatrix(new ArrayList<>(d.getVariables()), cov.getMatrix(), ess));
                    }
                    default -> throw new IllegalArgumentException("Stratified RLCD cannot use the missing-data "
                                                                  + "policy " + spec.getPolicy() + "; use em, "
                                                                  + "pairwise or listwise.");
                }
            }
        }
        return out;
    }

    private static String perDomainCovarianceMethod(Parameters parameters) {
        if (parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false)) {
            return "censored MVN estimate at ceiling " + parameters.getDouble(Params.RLCD_CEILING_VALUE, 100.0);
        }
        MissingDataSpec spec = MissingDataUtils.fromParameters(parameters);
        if (spec == null) return "sample covariance";
        return switch (spec.getPolicy()) {
            case LISTWISE -> "sample covariance of complete rows";
            case EM_COVARIANCE -> "EM estimate";
            case PAIRWISE_COVARIANCE -> "available-case estimate";
            default -> spec.getPolicy().toString();
        };
    }

    private static int effectiveSampleSize(int[][] counts, int n, MissingDataSpec.EffectiveSampleSizeMode mode) {
        if (mode == MissingDataSpec.EffectiveSampleSizeMode.FULL_N || counts == null) return n;
        double sum = 0;
        int pairs = 0;
        int min = Integer.MAX_VALUE;
        for (int a = 0; a < counts.length; a++) {
            for (int b = a + 1; b < counts.length; b++) {
                sum += counts[a][b];
                pairs++;
                min = Math.min(min, counts[a][b]);
            }
        }
        if (pairs == 0) return n;
        int ess = mode == MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE ? min : (int) Math.round(sum / pairs);
        return Math.max(2, Math.min(n, ess));
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
        rlcd.setQualifyObservedCauses(parameters.getBoolean(Params.RLCD_QUALIFY_OBSERVED_CAUSES, true));
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
        // Mixed, not Continuous: the search box checks this against the data, and Mixed admits both continuous data
        // and mixed data (continuous variables plus a discrete domain column, in stratified mode); search() itself
        // refuses mixed data when stratification is off.
        return DataType.Mixed;
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
        parameters.add(Params.RLCD_QUALIFY_OBSERVED_CAUSES);
        parameters.add(Params.RLCD_OBSERVED_EXPLANATION_CHECK);
        parameters.add(Params.RLCD_OBSERVED_EXPLANATION_ALPHA);
        parameters.add(Params.RLCD_PARTITION_CLIQUE_THRESHOLD);
        parameters.add(Params.RLCD_STAGE1_FGES);
        parameters.add(Params.RLCD_STAGE1_NONE);
        parameters.add(Params.RLCD_STABILITY_RUNS);
        parameters.add(Params.RLCD_STABILITY_TIME_LIMIT);
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
