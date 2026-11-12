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

package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.search.rlcd.StratifiedChi2RankTest;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.lcdnod.LcdNodPhase2;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.StratifiedRankTests;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * LCD-NOD (Dong, Dai, Ng, Spirtes, and Zhang, "Identifying Partially Observed Causal Models from
 * Heterogeneous/Nonstationary Data", ICML 2026), end to end from a data set with a domain column:
 * <ol>
 * <li>Phase 1: RLCD in stratified mode learns the latent-variable structure, with every rank test computed within
 * each domain and pooled across domains, so that the structure is valid even though every mechanism may differ
 * across domains.</li>
 * <li>Phase 2: {@link LcdNodPhase2} decides which latents and which indicators have mechanisms that change across
 * domains. The paper establishes this step (its Theorem 8) for one-factor models only, so it is applied to the
 * one-factor part of the RLCD graph: the latents with at least two pure indicators (measured children whose only
 * neighbor is that latent), measured by those indicators, with the structure among them taken from RLCD.</li>
 * </ol>
 * The result is the RLCD graph with the domain variable added and an edge from it to each changing latent and each
 * changing pure indicator. What Phase 2 cannot assess is left as RLCD found it and listed in the log: impure
 * measured variables, latents without two pure indicators, and edges among measured variables. When every latent of
 * the RLCD graph has two pure indicators, the latent decisions consider each DAG in RLCD's latent equivalence class
 * (the paper's Section C.3); when some latent lacks them, the structure among the remaining latents is not a DAG over
 * them alone, and the decisions fall back to the paper's Theorem 8 rule over all subsets of the other latents.
 * <p>
 * The domain column may be discrete, or continuous with whole-number values; every other column must be continuous.
 * RLCD takes only continuous data, so a discrete domain column is recoded as its category index before RLCD sees it.
 * RLCD's parameters apply to Phase 1 except that stratification is always on, and the pooled-imputation, testwise
 * and stability options are not offered. Missing values and ceiling censoring are handled within each domain: the
 * covariance of each domain is estimated by the chosen policy (EM, available-case, listwise) or by the censored
 * model, and those per-domain matrices drive both the pooled rank tests of Phase 1 and the fits of Phase 2, whose
 * change test then uses a parametric bootstrap (see {@link LcdNodPhase2}).
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "LCD-NOD",
        command = "lcd-nod",
        algoType = AlgType.search_for_structure_over_latents,
        dataType = {DataType.Continuous, DataType.Mixed}
)
@Experimental
public class LcdNod implements Algorithm, AcceptsKnowledge {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Knowledge passed through to RLCD.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The report of the last run, for callers that want more than the graph.
     */
    private transient String report;

    /**
     * The Phase 1 (RLCD) graph of the last run.
     */
    private transient Graph rlcdGraph;

    /**
     * The Phase 2 result of the last run.
     */
    private transient LcdNodPhase2.Result phase2Result;

    /**
     * Constructs a new instance of the algorithm.
     */
    public LcdNod() {
    }

    /**
     * Runs LCD-NOD on a data set holding the measured variables and a domain column.
     *
     * @param dataModel  the data set.
     * @param parameters the parameters; see {@link #getParameters()}.
     * @return the RLCD graph with the domain variable and its edges into the changing latents and indicators.
     */
    @Override
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("LCD-NOD expects a tabular data set.");
        }

        String domainColumn = StratifiedRankTests.resolveDomainColumn(dataSet,
                parameters.getString(Params.DOMAIN_COLUMN, "domain"));
        Node domain = dataSet.getVariable(domainColumn);

        for (Node v : dataSet.getVariables()) {
            if (v != domain && v instanceof DiscreteVariable) {
                throw new IllegalArgumentException("LCD-NOD: " + v.getName() + " is discrete. Every column other "
                                                   + "than the domain column (" + domainColumn + ") must be "
                                                   + "continuous.");
            }
        }

        // RLCD takes continuous data only: recode a discrete domain column as its category index.
        DataSet continuous = domain instanceof DiscreteVariable ? recodeDomain(dataSet, domain) : dataSet;
        Node contDomain = continuous.getVariable(domainColumn);

        // Split the rows by domain (the domain column removed) and estimate a covariance within each domain, by
        // the missing-data policy or censoring if set. These matrices drive both phases.
        List<DataSet> perDomain = StratifiedRankTests.split(continuous, contDomain);
        List<String> labels = domainLabels(continuous, contDomain, domain);
        if (perDomain.size() != labels.size()) {
            throw new IllegalStateException("Domain split and labels disagree: " + perDomain.size() + " vs "
                                            + labels.size());
        }

        boolean anyMissing = new MissingDataAudit(dataSet).anyMissing();
        boolean censor = parameters.getBoolean(Params.RLCD_CENSOR_CEILING, false);
        MissingDataSpec spec = MissingDataUtils.fromParameters(parameters);
        if (anyMissing && !censor && spec == null) {
            throw new IllegalArgumentException("LCD-NOD: the data have missing values and no missing-data policy is "
                                               + "set. Set missingDataPolicy to \"em\" (recommended: an EM "
                                               + "covariance within each domain), \"pairwise\" or \"listwise\", or "
                                               + "turn on rlcdCensorCeiling if values sit at a ceiling such as 100 "
                                               + "(missing values are then handled as missing at random within the "
                                               + "censored model).");
        }
        if (spec != null && (spec.getPolicy() == MissingDataPolicy.TESTWISE
                             || spec.getPolicy() == MissingDataPolicy.MULTIPLE_IMPUTATION)) {
            throw new IllegalArgumentException("LCD-NOD: the testwise and multiple-imputation policies cannot be "
                                               + "combined with stratification; use em, pairwise or listwise.");
        }

        List<ICovarianceMatrix> covariances = Rlcd.perDomainCovariances(perDomain, parameters);

        int dcol = continuous.getVariables().indexOf(contDomain);
        int[] keep = new int[continuous.getNumColumns() - 1];
        int kk = 0;
        for (int j = 0; j < continuous.getNumColumns(); j++) if (j != dcol) keep[kk++] = j;
        DataSet sub = continuous.subsetColumns(keep);

        StringBuilder sizes = new StringBuilder();
        for (int t = 0; t < perDomain.size(); t++) {
            if (t > 0) sizes.append(", ");
            sizes.append(labels.get(t)).append(" (").append(perDomain.get(t).getNumRows());
            if (covariances.get(t).getSampleSize() != perDomain.get(t).getNumRows()) {
                sizes.append(", ess ").append(covariances.get(t).getSampleSize());
            }
            sizes.append(')');
        }
        String domainsLine = "LCD-NOD: domains " + sizes + "; per-domain covariance by "
                             + (censor ? "censored MVN estimate at ceiling "
                                         + parameters.getDouble(Params.RLCD_CEILING_VALUE, 100.0)
                                : spec == null ? "sample covariance"
                                : spec.getPolicy().toString().toLowerCase().replace('_', ' ')) + ".";
        TetradLogger.getInstance().log(domainsLine);

        // Phase 1: stratified RLCD on the per-domain covariances.
        Rlcd rlcd = new Rlcd();
        rlcd.setKnowledge(knowledge);
        Graph g1 = rlcd.searchStratified(sub, StratifiedChi2RankTest.fromCovariances(covariances), parameters);
        this.rlcdGraph = g1;

        // The one-factor part: latents with at least two pure indicators.
        List<Node> latents = new ArrayList<>();
        List<List<Integer>> blocks = new ArrayList<>();
        List<String> latentsWithoutPure = new ArrayList<>();
        for (Node l : g1.getNodes()) {
            if (l.getNodeType() != NodeType.LATENT) continue;
            List<Integer> block = new ArrayList<>();
            for (Node c : g1.getChildren(l)) {
                if (c.getNodeType() == NodeType.LATENT) continue;
                List<Node> adj = g1.getAdjacentNodes(c);
                if (adj.size() == 1 && adj.get(0) == l) {
                    block.add(sub.getVariables().indexOf(sub.getVariable(c.getName())));
                }
            }
            if (block.size() >= 2) {
                latents.add(l);
                blocks.add(block);
            } else {
                latentsWithoutPure.add(l.getName());
            }
        }

        List<String> impure = new ArrayList<>();
        for (Node v : g1.getNodes()) {
            if (v.getNodeType() == NodeType.LATENT) continue;
            int col = sub.getVariables().indexOf(sub.getVariable(v.getName()));
            boolean inBlock = false;
            for (List<Integer> b : blocks) if (b.contains(col)) inBlock = true;
            if (!inBlock) impure.add(v.getName());
        }

        StringBuilder sb = new StringBuilder(domainsLine).append('\n').append("LCD-NOD Phase 1 (stratified RLCD): ")
                .append(latents.size() + latentsWithoutPure.size()).append(" latent(s).\n");
        sb.append("Phase 2 covers the one-factor part: ");
        for (int i = 0; i < latents.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(latents.get(i).getName()).append(" (");
            for (int j = 0; j < blocks.get(i).size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(sub.getVariable(blocks.get(i).get(j)).getName());
            }
            sb.append(')');
        }
        if (latents.isEmpty()) sb.append("nothing");
        sb.append('\n');
        if (!latentsWithoutPure.isEmpty()) {
            sb.append("Not assessed (fewer than two pure indicators): ").append(String.join(", ", latentsWithoutPure))
                    .append('\n');
        }
        if (!impure.isEmpty()) {
            sb.append("Not assessed (not a pure indicator of an assessed latent): ").append(String.join(", ", impure))
                    .append('\n');
        }

        Graph out = new EdgeListGraph(g1);
        Node domainNode = domain;
        out.addNode(domainNode);

        if (latents.isEmpty()) {
            sb.append("No latent has two pure indicators, so Phase 2 did not run.\n");
            this.report = sb.toString();
            this.phase2Result = null;
            TetradLogger.getInstance().log(report);
            return out;
        }

        // Phase 2, on the same per-domain covariances.
        LcdNodPhase2 phase2 = new LcdNodPhase2(covariances, labels, domain, blocks, latents);
        phase2.setAlpha(parameters.getDouble(Params.LCD_NOD_CHANGE_ALPHA, 0.05));
        phase2.setNumBootstraps(parameters.getInt(Params.LCD_NOD_BOOTSTRAPS, 100));
        phase2.setChangeRatio(parameters.getDouble(Params.LCD_NOD_CHANGE_RATIO, 1.5));
        phase2.setSeed(parameters.getLong(Params.SEED, -1L));
        phase2.setVerbose(parameters.getBoolean(Params.VERBOSE, false));
        if (latentsWithoutPure.isEmpty()) {
            phase2.setLatentStructure(g1);
        } else {
            sb.append("Some latents are not assessed, so the latent decisions use the Theorem 8 rule (all subsets).\n");
            phase2.setLearnLatentStructure(false);
        }

        try {
            phase2.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LCD-NOD was interrupted.", e);
        }
        LcdNodPhase2.Result r = phase2.getResult();
        this.phase2Result = r;

        for (int i = 0; i < latents.size(); i++) {
            if (r.latentChanges[i]) out.addDirectedEdge(domainNode, out.getNode(latents.get(i).getName()));
        }
        for (int a = 0; a < r.indicatorNames.size(); a++) {
            LcdNodPhase2.Decision d = r.indicatorDecisions[a];
            if (d.applicable && d.changes) out.addDirectedEdge(domainNode, out.getNode(r.indicatorNames.get(a)));
        }

        this.report = sb + "\n" + phase2.getReport();
        TetradLogger.getInstance().log(report);
        return out;
    }

    /**
     * The domain labels in the order of {@link StratifiedRankTests#split(DataSet, Node)}: the distinct whole-number
     * values of the (continuous) domain column in increasing order, shown as the category names of the original
     * discrete column when there was one.
     */
    private static List<String> domainLabels(DataSet continuous, Node contDomain, Node original) {
        int dcol = continuous.getVariables().indexOf(contDomain);
        java.util.TreeSet<Integer> values = new java.util.TreeSet<>();
        for (int r = 0; r < continuous.getNumRows(); r++) {
            double x = continuous.getDouble(r, dcol);
            if (!Double.isNaN(x)) values.add((int) Math.rint(x));
        }
        List<String> labels = new ArrayList<>();
        for (int v : values) {
            labels.add(original instanceof DiscreteVariable dv && v >= 0 && v < dv.getNumCategories()
                    ? dv.getCategory(v) : Integer.toString(v));
        }
        return labels;
    }

    /**
     * The data set with a discrete domain column replaced by a continuous column of its category indices, under the
     * same name.
     */
    private static DataSet recodeDomain(DataSet dataSet, Node domain) {
        int dcol = dataSet.getVariables().indexOf(domain);
        List<Node> vars = new ArrayList<>(dataSet.getVariables());
        vars.set(dcol, new ContinuousVariable(domain.getName()));
        double[][] m = new double[dataSet.getNumRows()][vars.size()];
        for (int r = 0; r < dataSet.getNumRows(); r++) {
            for (int c = 0; c < vars.size(); c++) {
                if (c == dcol) {
                    int v = dataSet.getInt(r, c);
                    m[r][c] = v < 0 ? Double.NaN : v;
                } else {
                    m[r][c] = dataSet.getDouble(r, c);
                }
            }
        }
        return new BoxDataSet(new DoubleDataBox(m), vars);
    }

    /**
     * Returns the graph itself, since the output (latents and the domain variable) has no standard comparison form.
     *
     * @param graph the true graph, if there is one.
     * @return the graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return graph;
    }

    /**
     * Returns a description of the algorithm.
     *
     * @return the description.
     */
    @Override
    public String getDescription() {
        return "LCD-NOD (stratified RLCD, then which latents and indicators change across domains)";
    }

    /**
     * Returns the data type: continuous measured variables with a domain column that is discrete (mixed data) or
     * integer-valued continuous (continuous data).
     *
     * @return {@code DataType.Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Returns the parameters: the RLCD parameters that apply in stratified mode, then those of Phase 2.
     *
     * @return the parameter names.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.DOMAIN_COLUMN);
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
        parameters.add(Params.PENALTY_DISCOUNT_DEFAULT_1);
        parameters.add(Params.MISSING_DATA_POLICY);
        parameters.add(Params.MISSING_ESS_MODE);
        parameters.add(Params.MISSING_EM_RIDGE);
        parameters.add(Params.MISSING_EM_TOLERANCE);
        parameters.add(Params.MISSING_EM_MAX_ITERATIONS);
        parameters.add(Params.RLCD_CENSOR_CEILING);
        parameters.add(Params.RLCD_CEILING_VALUE);
        parameters.add(Params.LCD_NOD_CHANGE_ALPHA);
        parameters.add(Params.LCD_NOD_BOOTSTRAPS);
        parameters.add(Params.LCD_NOD_CHANGE_RATIO);
        parameters.add(Params.SEED);
        parameters.add(Params.VERBOSE);
        return parameters;
    }

    /**
     * Returns the knowledge passed through to RLCD.
     *
     * @return the knowledge.
     */
    @Override
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge passed through to RLCD.
     *
     * @param knowledge the knowledge.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * The report of the last run: what Phase 2 covered, then its estimates and decisions.
     *
     * @return the report, or null before any run.
     */
    public String getReport() {
        return report;
    }

    /**
     * The Phase 1 (stratified RLCD) graph of the last run.
     *
     * @return the graph, or null before any run.
     */
    public Graph getRlcdGraph() {
        return rlcdGraph;
    }

    /**
     * The Phase 2 result of the last run.
     *
     * @return the result, or null before any run or if Phase 2 did not run.
     */
    public LcdNodPhase2.Result getPhase2Result() {
        return phase2Result;
    }
}
