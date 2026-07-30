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
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndTestFdrWrapper;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.search.utils.LogUtilsSearch.stampWithBic;

/**
 * Peter/Clark algorithm with adjacency-rescue (PcAR): PC augmented with detection, and optionally
 * repair, of cancelled unfaithful-triangle edges. See {@link edu.cmu.tetrad.search.PcAR} for the
 * detection/recovery machinery itself; this class only wires it into algcomparison.
 * <p>
 * NOTE ON UNWIRED HOOKS: {@code edu.cmu.tetrad.search.PcAR} exposes three functional hooks
 * ({@code DeterminismGuard}, {@code RecoveryOddsEstimator}, {@code MarkovAuditor}) that take code,
 * not primitive values, and so cannot be expressed as {@link Parameters} entries. They are left
 * unset here (i.e. the built-in fallbacks apply, or RECOVER behaves like MARK with no odds
 * estimator) &mdash; wire them programmatically against a specific {@link IndependenceTest}
 * implementation if you want them active, rather than through this wrapper.
 * <p>
 * NOTE ON NEW PARAMS: {@code RESCUE_ACTION} and {@code RECOVERY_ODDS_THRESHOLD} below are new
 * parameter keys that need entries added to {@code Params} (and the corresponding
 * {@code ParamDescriptions} default/description/bounds registration) before this will run;
 * I don't have those files, so I've named them here to match the setters on
 * {@code edu.cmu.tetrad.search.PcAR} and left the defaults as comments for you to place.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "PC-AR",
        command = "pc-ar",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
public class PcAR extends AbstractBootstrapAlgorithm implements Algorithm, AcceptsKnowledge,
        TakesIndependenceWrapper, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for PcAr.</p>
     */
    public PcAR() {
    }

    /**
     * <p>Constructor for PcAr.</p>
     *
     * @param test a {@link edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper} object
     */
    public PcAR(IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a data set for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        boolean allowBidirected = parameters.getBoolean(Params.ALLOW_BIDIRECTED);

        edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle colliderOrientationStyle = switch (parameters.getInt(Params.COLLIDER_ORIENTATION_STYLE)) {
            case 1 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.SEPSETS;
            case 2 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.CONSERVATIVE;
            case 3 -> edu.cmu.tetrad.search.PcAR.ColliderOrientationStyle.MAX_P;
            default -> throw new IllegalArgumentException("Invalid collider orientation style");
        };

        // Defaulted here, not sure this mapping is what you want long-term: 0=OFF, 1=MARK,
        // 2=RECOVER. Falls back to MARK (1) if RESCUE_ACTION isn't a registered param yet.
        int rescueActionCode = parameters.getInt(Params.RESCUE_ACTION); // TODO: register Params.RESCUE_ACTION, default 1 (MARK)
        edu.cmu.tetrad.search.PcAR.RescueAction rescueAction = switch (rescueActionCode) {
            case 0 -> edu.cmu.tetrad.search.PcAR.RescueAction.OFF;
            case 2 -> edu.cmu.tetrad.search.PcAR.RescueAction.RECOVER;
            default -> edu.cmu.tetrad.search.PcAR.RescueAction.MARK;
        };

        IndependenceTest test = getIndependenceWrapper().getTest(dataModel, parameters);
        test = new CachingIndependenceTest(test);

        Graph graph;

        edu.cmu.tetrad.search.PcAR search = new edu.cmu.tetrad.search.PcAR(test);
        search.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));
        search.setDepth(parameters.getInt(Params.DEPTH));
        search.setVerbose(parameters.getBoolean(Params.VERBOSE));
        search.setKnowledge(this.knowledge);
        search.setFasStable(parameters.getBoolean(Params.STABLE_FAS));
        search.setColliderOrientationStyle(colliderOrientationStyle);
        search.setAllowBidirected(allowBidirected ? edu.cmu.tetrad.search.PcAR.AllowBidirected.ALLOW
                : edu.cmu.tetrad.search.PcAR.AllowBidirected.DISALLOW);

        search.setRescueAction(rescueAction);
        // Defaulted, unsure of the right default: paper has no natural "always safe" nonzero
        // threshold outside a calibrated odds estimator, so this only matters if RECOVER is
        // selected above *and* setRecoveryOddsEstimator is wired programmatically elsewhere.
        // TODO: register Params.RECOVERY_ODDS_THRESHOLD; using Double.POSITIVE_INFINITY here
        // (RECOVER behaves like MARK) until you decide on a real default.
        search.setRecoveryOddsThreshold(parameters.getDouble(Params.RECOVERY_ODDS_THRESHOLD));
        // determinismGuard, recoveryOddsEstimator, markovAuditor intentionally left unset (null) --
        // see class javadoc. Wire them here directly if/when you have concrete implementations,
        // e.g.:
        //   search.setDeterminismGuard((v, S) -> ...);
        //   search.setRecoveryOddsEstimator((x, y, z, S) -> ...);
        //   search.setMarkovAuditor((g, t) -> ...);

        double fdrQ = parameters.getDouble(Params.FDR_Q);

        if (fdrQ == 0.0) {
            graph = search.search();
        } else {
            boolean negativelyCorrelated = true;
            boolean verbose = parameters.getBoolean(Params.VERBOSE);
            double alpha = parameters.getDouble(Params.ALPHA);
            // NOTE: doFdrLoop was written against edu.cmu.tetrad.search.Pc in the original
            // wrapper; PcAR implements the same IGraphSearch interface, but I haven't seen
            // IndTestFdrWrapper's signature so can't confirm it doesn't downcast internally.
            // Flagging for a check on your end rather than guessing.
            graph = IndTestFdrWrapper.doFdrLoop(search, negativelyCorrelated, alpha, fdrQ, verbose);
        }

        stampWithBic(graph, dataModel);

        // Surface the tier-1 detections somewhere visible rather than dropping them: attached as
        // graph attributes since algcomparison's runSearch only returns a Graph. Adjust to
        // whatever channel (e.g. a report file, or a dedicated result type) you'd rather use --
        // this is the simplest thing that doesn't lose the information.
        graph.addAttribute("PcAR.contestedDeletions", search.getContestedDeletions().size());
        graph.addAttribute("PcAR.markovAuditFailures", search.getMarkovAuditFailures().size());

        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        Graph dag = new EdgeListGraph(graph);
        return GraphTransforms.dagToCpdag(dag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "PC-AR (adjacency-rescue) using " + this.test.getDescription();
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
        parameters.add(Params.STABLE_FAS);
        parameters.add(Params.COLLIDER_ORIENTATION_STYLE);
        parameters.add(Params.ALLOW_BIDIRECTED);
        parameters.add(Params.DEPTH);
        parameters.add(Params.FDR_Q);
        parameters.add(Params.TIME_LAG);
        parameters.add(Params.TIME_LAG_REPLICATING_GRAPH);
        parameters.add(Params.VERBOSE);
        // New for PC-AR; need registering in Params/ParamDescriptions (see class javadoc).
        parameters.add(Params.RESCUE_ACTION);
        parameters.add(Params.RECOVERY_ODDS_THRESHOLD);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }
        this.test = test;
    }
}
