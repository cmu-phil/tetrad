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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.independence.BlockIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.independence.TakesGraph;
import edu.cmu.tetrad.algcomparison.score.BlockScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.PoolsImputations;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.ParamsResettable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.ParseException;
import java.util.*;

/**
 * Stores an algorithms in the format of the algorithm comparison API.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralAlgorithmRunner implements AlgorithmRunner, ParamsResettable,
        Unmarshallable, IndTestProducer,
        KnowledgeBoxInput {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The user's algorithm selections, copied over from the runner this one is constructed from.
     */
    private final Map<String, Object> userAlgoSelections = new HashMap<>();
    /**
     * A display subtitle (e.g., sample size or pooling note) for each result graph, keyed by graph identity.
     */
    private final Map<Graph, String> graphSubtitle = new IdentityHashMap<>();
    /**
     * The graph list.
     */
    List<Graph> graphList = new ArrayList<>();
    /**
     * The results of the previous search in this box, kept only as a layout reference for the next one when the
     * session recreates the runner (see the copy constructor); otherwise the reference is graphList itself. Not
     * saved with the session, which saves graphList.
     */
    private transient List<Graph> layoutReference;
    /**
     * The block specification (clusters of measured variables) passed to block-based tests, scores and latent
     * structure algorithms; null if none.
     */
    BlockSpec blockSpec = null;
    /**
     * The upstream Latent Clusters runner, non-null only when this search box was given a Latent Clusters box as a
     * parent. When present, {@link #execute()} refreshes the {@link BlockSpec} from it before the search and appends
     * the measurement edges (each block's latent to its indicators) to the result, as the Latent Structure box did.
     * Added 2026-10-11; absent from sessions saved earlier, which read it as null (serialVersionUID is pinned).
     */
    private LatentClustersRunner latentClustersRunner;
    /**
     * The data model.
     */
    private DataWrapper dataWrapper;
    /**
     * The name of the model.
     */
    private String name;
    /**
     * The wrapped algorithm.
     */
    private Algorithm algorithm;
    /**
     * The params object, so the GUI can remember stuff for logging.
     */
    private Parameters parameters;
    /**
     * The graph source.
     */
    private Graph sourceGraph;
    /**
     * The external graph.
     */
    private Graph externalGraph;
    /**
     * The knowledge.
     */
    private Knowledge knowledge;
    /**
     * The independence tests.
     */
    private transient List<IndependenceTest> independenceTests;
    /**
     * The display names of the result graphs, parallel to the graph list.
     */
    private List<String> resultNames = new ArrayList<>();
    /**
     * The elapsed time for the algorithm to run.
     */
    private long elapsedTime = -1L;

    /**
     * Which of the result graphs is the one this runner hands to downstream boxes (see getGraph()). Chosen by
     * selecting a result in the search editor; reset to the first result when a search is run.
     */
    private int selectedResultIndex = 0;

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param runner     a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(GeneralAlgorithmRunner runner, Parameters parameters)
            throws ParseException {
        this(runner.getDataWrapper(), runner, parameters, null, null);
        this.sourceGraph = runner.sourceGraph;
        this.knowledge = runner.knowledge;
        this.algorithm = runner.algorithm;
        this.parameters = parameters;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);

        // The previous results are the layout reference for the next search, so the layout survives the session
        // recreating this box (e.g. on Execute after an upstream change). They are not this runner's results.
        if (runner.graphList != null && !runner.graphList.isEmpty()) {
            this.layoutReference = new ArrayList<>(runner.graphList);
        }
    }

    //===========================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters)
            throws ParseException {
        this(dataWrapper, null, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, KnowledgeBoxModel knowledgeBoxModel,
                                  Parameters parameters) throws ParseException {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters)
            throws ParseException {
        this(dataWrapper, graphSource, parameters, null, null);
    }

    /**
     * Constructs a search-box runner over data and supplied clusters: the upstream {@link BlockSpec} is handed to
     * block-aware tests, scores, and algorithms, and the measurement edges are appended to the result. The chooser
     * lists the algorithms that can take such a configuration (see
     * {@link edu.cmu.tetradapp.editor.search.AlgorithmChooserLogic.BoxMode#LATENTS_FROM_CLUSTERS}).
     *
     * @param dataWrapper          the data wrapper containing the dataset to be used
     * @param latentClustersRunner the Latent Clusters parent providing the upstream BlockSpec; must not be null
     * @param parameters           parameters for the session node
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, LatentClustersRunner latentClustersRunner,
                                  Parameters parameters) throws ParseException {
        this(dataWrapper, null, parameters, null, null);
        this.latentClustersRunner = Objects.requireNonNull(latentClustersRunner, "ClustersRunner required");
        this.blockSpec = latentClustersRunner.getBlockSpec();
    }

    /**
     * Constructs a search-box runner over data, supplied clusters, and knowledge.
     *
     * @param dataWrapper          the data wrapper containing the dataset to be used
     * @param latentClustersRunner the Latent Clusters parent providing the upstream BlockSpec; must not be null
     * @param knowledgeBoxModel    the knowledge box
     * @param parameters           parameters for the session node
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, LatentClustersRunner latentClustersRunner,
                                  KnowledgeBoxModel knowledgeBoxModel, Parameters parameters) throws ParseException {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
        this.latentClustersRunner = Objects.requireNonNull(latentClustersRunner, "ClustersRunner required");
        this.blockSpec = latentClustersRunner.getBlockSpec();
    }

    /**
     * Constructs a search-box runner over data and supplied clusters, taking its algorithm and chooser selections
     * from the previous runner of this box, if it had any.
     *
     * @param dataWrapper          the data wrapper containing the dataset to be used
     * @param latentClustersRunner the Latent Clusters parent providing the upstream BlockSpec; must not be null
     * @param runner               the previous runner of this box
     * @param parameters           parameters for the session node
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, LatentClustersRunner latentClustersRunner,
                                  GeneralAlgorithmRunner runner, Parameters parameters) throws ParseException {
        this(dataWrapper, latentClustersRunner, parameters);
        if (runner != null && runner.getAlgorithm() != null) {
            this.algorithm = runner.getAlgorithm();
        }
        if (runner != null) {
            this.userAlgoSelections.putAll(runner.userAlgoSelections);
        }
    }

    /**
     * Constructs a search-box runner over data, supplied clusters, and knowledge, taking its algorithm and chooser
     * selections from the previous runner of this box, if it had any.
     *
     * @param dataWrapper          the data wrapper containing the dataset to be used
     * @param latentClustersRunner the Latent Clusters parent providing the upstream BlockSpec; must not be null
     * @param runner               the previous runner of this box
     * @param knowledgeBoxModel    the knowledge box
     * @param parameters           parameters for the session node
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, LatentClustersRunner latentClustersRunner,
                                  GeneralAlgorithmRunner runner, KnowledgeBoxModel knowledgeBoxModel,
                                  Parameters parameters) throws ParseException {
        this(dataWrapper, latentClustersRunner, knowledgeBoxModel, parameters);
        if (runner != null && runner.getAlgorithm() != null) {
            this.algorithm = runner.getAlgorithm();
        }
        if (runner != null) {
            this.userAlgoSelections.putAll(runner.userAlgoSelections);
        }
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource,
                                  KnowledgeBoxModel knowledgeBoxModel,
                                  Parameters parameters) throws ParseException {
        this(dataWrapper, graphSource, parameters, knowledgeBoxModel, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param facts             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts)
            throws ParseException {
        this(dataWrapper, null, parameters, knowledgeBoxModel, facts);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters)
            throws ParseException {
        this(dataWrapper, null, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param runner            a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GeneralAlgorithmRunner runner, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) throws ParseException {
        this(dataWrapper, null, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters) throws ParseException {
        this(dataWrapper, graphSource, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner            a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @throws ParseException if any
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, GeneralAlgorithmRunner runner,
                                  Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel) throws ParseException {
        this(dataWrapper, graphSource, parameters, knowledgeBoxModel, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * Constucts a wrapper for the given graph.
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param runner      a {@link edu.cmu.tetradapp.model.GeneralAlgorithmRunner} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, GeneralAlgorithmRunner runner, Parameters parameters)
            throws ParseException {
        this(null, graphSource, parameters, null, null);
        this.algorithm = runner.algorithm;

        this.userAlgoSelections.putAll(runner.userAlgoSelections);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @throws ParseException if any
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel)
            throws ParseException {
        this(null, graphSource, parameters, knowledgeBoxModel, null);
    }

    /**
     * <p>Constructor for GeneralAlgorithmRunner.</p>
     *
     * @param model             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @throws ParseException if any
     */
    public GeneralAlgorithmRunner(IndependenceFactsModel model,
                                  Parameters parameters, KnowledgeBoxModel knowledgeBoxModel)
            throws ParseException {
        this(null, null, parameters, knowledgeBoxModel, model);
    }

    /**
     * Constucts a wrapper for the given graph.
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws ParseException if any
     */
    public GeneralAlgorithmRunner(GraphSource graphSource, Parameters parameters) throws ParseException {
        this(null, graphSource, parameters, null, null);
    }

    /**
     * Constructs a wrapper for the given DataWrapper. The DatWrapper must contain a DataSet that is either a DataSet or
     * a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper       a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param graphSource       a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters        a {@link edu.cmu.tetrad.util.Parameters} object
     * @param knowledgeBoxModel a {@link edu.cmu.tetradapp.model.KnowledgeBoxModel} object
     * @param facts             a {@link edu.cmu.tetradapp.model.IndependenceFactsModel} object
     * @throws ParseException if any.
     */
    public GeneralAlgorithmRunner(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters,
                                  KnowledgeBoxModel knowledgeBoxModel, IndependenceFactsModel facts)
            throws ParseException {
        if (parameters == null) {
            throw new NullPointerException();
        }

        this.parameters = parameters;

        if (graphSource instanceof GeneralAlgorithmRunner parent) {
            this.algorithm = parent.getAlgorithm();

            // A search box whose only parent is a search box searches that box's data, with its result as the
            // source graph (set below).
            if (dataWrapper == null) dataWrapper = parent.getDataWrapper();
        }

        if (dataWrapper != null) {
            this.dataWrapper = dataWrapper;

            if (dataWrapper.getDataModelList().isEmpty() && dataWrapper instanceof Simulation) {
                ((Simulation) dataWrapper).createSimulation();
            }
        }

        if (graphSource != null) {
            if (dataWrapper == null && graphSource instanceof DataWrapper) {
                this.dataWrapper = (DataWrapper) graphSource;
            } else {
                this.sourceGraph = graphSource.getGraph();
            }
        }

        if (dataWrapper != null) {
            List<String> names = this.dataWrapper.getVariableNames();
            transferVarNamesToParams(names);
        }

        if (knowledgeBoxModel != null) {
            this.knowledge = knowledgeBoxModel.getKnowledge();
        } else {
            this.knowledge = new Knowledge();
        }

        if (facts != null) {
            getParameters().set("independenceFacts", facts.getFacts());
        }
    }

    /**
     * Constructs a runner that holds the given result graphs, without an algorithm or data.
     *
     * @param graphList the result graphs
     */
    public GeneralAlgorithmRunner(List<Graph> graphList) {
        this.graphList = graphList;
    }

    //============================PUBLIC METHODS==========================//

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String datasetDisplayName(DataModel dm) {
        // DataSet, CovarianceMatrix, etc. all implement DataModel#getName().
        String n = (dm == null) ? null : dm.getName();
        return hasText(n) ? n : null;
    }

    private static String noteFor(DataModel dm) {
        if (dm instanceof DataSet ds) {
            return "n=" + ds.getNumRows();
        }
        if (dm instanceof ICovarianceMatrix cm) {
            return "n=" + cm.getSampleSize();
        }
        return null;
    }

    private static String noteForAggregate(List<? extends DataModel> dms) {
        int k = 0, nTot = 0;
        for (DataModel dm : dms) {
            String s = noteFor(dm);
            if (s != null && s.startsWith("n=")) {
                try {
                    nTot += Integer.parseInt(s.substring(2));
                    k++;
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return (k > 0) ? String.format("aggregated over %d datasets, n_total=%d", k, nTot) : null;
    }

    /**
     * Returns the display subtitle recorded for the given result graph during the last search.
     *
     * @param g the result graph (looked up by identity)
     * @return the subtitle, or null if none was recorded for this graph
     */
    public String getGraphSubtitle(Graph g) {
        return graphSubtitle.get(g);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() {
        // Clusters-parent mode (Search box with a Latent Clusters parent): re-fetch the BlockSpec from the parent,
        // since the clusters may have been re-run or edited since this runner was built, and validate it against
        // this box's data before anything runs.
        if (this.latentClustersRunner != null) {
            BlockSpec spec = this.latentClustersRunner.getBlockSpec();

            if (spec == null) {
                throw new IllegalStateException("No BlockSpec is available. Run a clustering algorithm first and/or "
                        + "click Apply in the blocks editor.");
            }

            DataModel specDataModel = resolveSpecDataModel(spec, getDataModel());
            BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
            ensureSpecMatchesRunnerData(spec, specDataModel);
            setBlockSpec(spec);
        }

        long start = System.currentTimeMillis();

        // The results of the previous search in this box, for laying out the new ones; see the layout pass below.
        List<Graph> previous = !this.graphList.isEmpty() ? new ArrayList<>(this.graphList)
                : this.layoutReference != null ? this.layoutReference : new ArrayList<>();
        this.layoutReference = null;

        this.graphList.clear();
        this.resultNames.clear();
        this.graphSubtitle.clear();
        this.selectedResultIndex = 0;

        if (this.independenceTests != null) {
            this.independenceTests.clear();
        }

        Algorithm algo = getAlgorithm();

        // Knowledge into algo if it accepts it
        if (this.knowledge != null && !knowledge.isEmpty()) {
            if (algo instanceof AcceptsKnowledge) {
                ((AcceptsKnowledge) algo).setKnowledge(this.knowledge.copy());
            } else {
                throw new IllegalArgumentException("Knowledge has been supplied, but this algorithm does not use knowledge.");
            }
        }

        // ===== CASE 1: No datasets, but a source graph is provided (graph-only search) =====
        DataModelList dataModelList = getDataModelList();
        if (dataModelList.isEmpty() && getSourceGraph() != null) {
            if (algo instanceof TakesScoreWrapper) {
                // Inject graph into special scores (e.g., MSepScore) and set block spec if present.
                ScoreWrapper scoreWrapper = ((TakesScoreWrapper) algo).getScoreWrapper();
                if (scoreWrapper instanceof MSepScore) {
                    ((MSepScore) scoreWrapper).setGraph(getSourceGraph());
                }
                if (scoreWrapper instanceof BlockScoreWrapper) {
                    ((BlockScoreWrapper) scoreWrapper).setBlockSpec(blockSpec);
                }
            }

            if (algo instanceof TakesIndependenceWrapper) {
                IndependenceWrapper wrapper = ((TakesIndependenceWrapper) algo).getIndependenceWrapper();
                if (wrapper instanceof MSeparationTest) {
                    ((MSeparationTest) wrapper).setGraph(getSourceGraph());
                }
                if (wrapper instanceof BlockIndependenceWrapper) {
                    ((BlockIndependenceWrapper) wrapper).setBlockSpec(blockSpec);
                }
            }

            if (algo instanceof TakesGraph) {
                ((TakesGraph) algo).setGraph(this.sourceGraph);
            }

            // An algorithm that takes an external graph (FASK, the pairwise orientation algorithms) is given the source
            // graph afresh at every run. The algorithm object is carried over from one run to the next, and the external
            // graph it was built with may be stale, or absent if the source graph was not known when it was built.
            if (algo instanceof TakesExternalGraph && this.sourceGraph != null) {
                ((TakesExternalGraph) algo).setExternalGraph(new SingleGraphAlg(this.sourceGraph));
            }

            if (this.algorithm instanceof AcceptsKnowledge) {
                Knowledge knowledge1 = TsUtils.getKnowledge(getSourceGraph());
                if (this.knowledge.isEmpty() && !knowledge1.isEmpty()) {
                    ((AcceptsKnowledge) algo).setKnowledge(knowledge1);
                } else {
                    ((AcceptsKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                }
            }

            Graph graph;
            try {
                graph = GraphUtils.detachNodes(algo.search(null, this.parameters));
                resultNames.add("Oracle Result");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            graphList.add(graph);
            graphSubtitle.put(graph, "");
        }
        // ===== CASE 2: There ARE datasets =====
        else {
            // ----- 2A) Multi-dataset algorithms: one combined graph from a subset (possibly randomized), possibly repeated -----
            if (getAlgorithm() instanceof MultiDataSetAlgorithm) {
                for (int k = 0; k < this.parameters.getInt("numRuns"); k++) {
                    Knowledge knowledge1 = dataModelList.getFirst().getKnowledge();
                    List<DataModel> dataSets = new ArrayList<>(dataModelList);
                    for (DataModel dataSet : dataSets) dataSet.setKnowledge(knowledge1);

                    int randomSelectionSize = this.parameters.getInt("randomSelectionSize");
                    if (randomSelectionSize == 0) {
                        randomSelectionSize = dataSets.size();
                    }
                    if (dataSets.size() < randomSelectionSize) {
                        throw new IllegalArgumentException("Sorry, the 'random selection size' is greater than "
                                + "the number of data sets: " + randomSelectionSize + " > " + dataSets.size());
                    }
                    RandomUtil.shuffle(dataSets);

                    List<DataModel> sub = new ArrayList<>();
                    for (int j = 0; j < randomSelectionSize; j++) {
                        sub.add(dataSets.get(j));
                    }

                    if (algo instanceof TakesGraph) {
                        ((TakesGraph) algo).setGraph(this.sourceGraph);
                    }

                    // An algorithm that takes an external graph (FASK, the pairwise orientation algorithms) is given the source
                    // graph afresh at every run. The algorithm object is carried over from one run to the next, and the external
                    // graph it was built with may be stale, or absent if the source graph was not known when it was built.
                    if (algo instanceof TakesExternalGraph && this.sourceGraph != null) {
                        ((TakesExternalGraph) algo).setExternalGraph(new SingleGraphAlg(this.sourceGraph));
                    }

                    if (this.algorithm instanceof AcceptsKnowledge) {
                        // Prefer knowledge supplied via a Knowledge box; otherwise pass along the
                        // knowledge carried by the data sets (knowledge1, stamped from the first
                        // data set above), which previously was stamped onto the data sets but
                        // never handed to the algorithm - so knowledge attached to the data was
                        // silently ignored by multi-data-set algorithms such as IMaGES.
                        Knowledge effectiveKnowledge =
                                (this.knowledge != null && !this.knowledge.isEmpty())
                                        ? this.knowledge
                                        : (knowledge1 != null ? knowledge1 : new Knowledge());
                        ((AcceptsKnowledge) this.algorithm).setKnowledge(effectiveKnowledge.copy());
                    }

                    try {
                        Graph graph = GraphUtils.detachNodes(((MultiDataSetAlgorithm) algo).search(sub, this.parameters));
                        graphList.add(graph);
                        graphSubtitle.put(graph, noteForAggregate(sub));
                        resultNames.add("Multi-dataset Algorithm");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            // ----- 2A') Imputations: several imputations of one data set, ONE search, when requested -----
            // Not 2B' below: imputations share their observed values, so they are not pooled as independent data
            // sets; the algorithm combines each of its tests over them. See PoolsImputations.
            else if (dataModelList.size() > 1 && algo instanceof PoolsImputations poolsImputations
                     && this.parameters.getBoolean(Params.POOL_IMPUTATIONS, false)) {
                if (knowledge == null) {
                    Knowledge knowledgeFromData = dataModelList.getFirst().getKnowledge();
                    if (knowledgeFromData != null && !knowledgeFromData.getVariables().isEmpty()) {
                        this.knowledge = knowledgeFromData;
                    }
                }

                if (this.algorithm instanceof AcceptsKnowledge && this.knowledge != null) {
                    ((AcceptsKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                }

                try {
                    Graph graph = GraphUtils.detachNodes(poolsImputations.searchImputations(
                            new ArrayList<>(dataModelList), this.parameters));
                    graphList.add(graph);
                    // Not noteForAggregate: its total sample size would count each row once per imputation.
                    graphSubtitle.put(graph, "pooled over " + dataModelList.size() + " imputations");
                    resultNames.add("Pooled (" + dataModelList.size() + " imputations)");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // ----- 2B') Pooled search: several data sets, ONE search (IMaGES-style), when requested -----
            else if (dataModelList.size() > 1 && this.parameters.getBoolean(Params.POOL_DATA_SETS, false)) {
                if (!(algo instanceof AbstractBootstrapAlgorithm)
                        || !(algo instanceof TakesScoreWrapper || algo instanceof TakesIndependenceWrapper)) {
                    throw new IllegalArgumentException("Pooling data sets (the 'poolDataSets' option) requires a "
                            + "score- or test-based algorithm; this algorithm cannot pool. "
                            + "Turn the option off to search each data set separately.");
                }

                if (knowledge == null) {
                    Knowledge knowledgeFromData = dataModelList.getFirst().getKnowledge();
                    if (knowledgeFromData != null && !knowledgeFromData.getVariables().isEmpty()) {
                        this.knowledge = knowledgeFromData;
                    }
                }

                if (algo instanceof TakesScoreWrapper) {
                    ScoreWrapper scoreWrapper = ((TakesScoreWrapper) algo).getScoreWrapper();
                    if (scoreWrapper instanceof BlockScoreWrapper) {
                        ((BlockScoreWrapper) scoreWrapper).setBlockSpec(blockSpec);
                    }
                    if (scoreWrapper instanceof AcceptsKnowledge) {
                        ((AcceptsKnowledge) scoreWrapper).setKnowledge(knowledge);
                    }
                }

                if (algo instanceof TakesIndependenceWrapper) {
                    IndependenceWrapper wrapper = ((TakesIndependenceWrapper) algo).getIndependenceWrapper();
                    if (wrapper instanceof BlockIndependenceWrapper) {
                        ((BlockIndependenceWrapper) wrapper).setBlockSpec(blockSpec);
                    }
                    if (wrapper instanceof AcceptsKnowledge) {
                        ((AcceptsKnowledge) wrapper).setKnowledge(knowledge);
                    }
                }

                if (algo instanceof TakesGraph) {
                    ((TakesGraph) algo).setGraph(this.sourceGraph);
                }

                // An algorithm that takes an external graph (FASK, the pairwise orientation algorithms) is given the source
                // graph afresh at every run. The algorithm object is carried over from one run to the next, and the external
                // graph it was built with may be stale, or absent if the source graph was not known when it was built.
                if (algo instanceof TakesExternalGraph && this.sourceGraph != null) {
                    ((TakesExternalGraph) algo).setExternalGraph(new SingleGraphAlg(this.sourceGraph));
                }

                if (this.algorithm instanceof AcceptsKnowledge && this.knowledge != null) {
                    ((AcceptsKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                }

                DataType algDataType = algo.getDataType();

                for (DataModel data : dataModelList) {
                    if (data instanceof ICovarianceMatrix && parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                        throw new IllegalArgumentException("Sorry, you need tabular datasets in order to do bootstrapping.");
                    }
                    boolean ok =
                            (data.isContinuous() && (algDataType == DataType.Continuous || algDataType == DataType.Mixed)) ||
                                    (data.isDiscrete() && (algDataType == DataType.Discrete || algDataType == DataType.Mixed)) ||
                                    (data.isMixed() && algDataType == DataType.Mixed);
                    if (!ok) {
                        throw new IllegalArgumentException("The algorithm was not expecting that type of data: "
                                + data.getName());
                    }
                }

                try {
                    // Passing the DataModelList itself is the request to pool; see
                    // AbstractBootstrapAlgorithm.searchPooled.
                    Graph graph = GraphUtils.detachNodes(algo.search(dataModelList, this.parameters));
                    graphList.add(graph);
                    graphSubtitle.put(graph, noteForAggregate(dataModelList));
                    resultNames.add("Pooled (" + dataModelList.size() + " data sets)");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // ----- 2B) Standard algorithms: run once PER DATASET and collect graphs -----
            else { // NEW
                // (Removed the old single-dataset assertion; now we iterate all datasets.)

                // In blocks mode (a BlockSpec is present; the Latent Structure box with a Latent Clusters parent),
                // the block-aware scores and tests are built from the BlockSpec's own data set and ignore the data
                // model handed to each run, so running once per data model would produce identical copies of one
                // result. With several data models (for example, several imputations of one data set), run ONCE,
                // on the BlockSpec's data set.
                List<DataModel> modelsToRun;
                boolean collapsedToSpecData = this.blockSpec != null && dataModelList.size() > 1;

                if (collapsedToSpecData) {
                    modelsToRun = new ArrayList<>();
                    modelsToRun.add(this.blockSpec.dataSet());
                } else {
                    modelsToRun = new ArrayList<>(dataModelList);
                }

                for (int i = 0; i < modelsToRun.size(); i++) {
                    DataModel data = modelsToRun.get(i);

                    if (knowledge == null) {

                        // If knowledge is not set, use knowledge embedded in each dataset if present
                        Knowledge knowledgeFromData = data.getKnowledge();
                        if (knowledgeFromData != null && !knowledgeFromData.getVariables().isEmpty()) {
                            this.knowledge = knowledgeFromData;
                        }
                    }

                    // Wire score/test wrappers with BlockSpec if applicable
                    if (algo instanceof TakesScoreWrapper) {
                        ScoreWrapper scoreWrapper = ((TakesScoreWrapper) algo).getScoreWrapper();
                        if (scoreWrapper instanceof BlockScoreWrapper) {
                            ((BlockScoreWrapper) scoreWrapper).setBlockSpec(blockSpec);
                        }
                        if (scoreWrapper instanceof AcceptsKnowledge) {
                            ((AcceptsKnowledge) scoreWrapper).setKnowledge(knowledge);
                        }
                    }

                    if (algo instanceof TakesIndependenceWrapper) {
                        IndependenceWrapper wrapper = ((TakesIndependenceWrapper) algo).getIndependenceWrapper();
                        if (wrapper instanceof BlockIndependenceWrapper) {
                            ((BlockIndependenceWrapper) wrapper).setBlockSpec(blockSpec);
                        }
                        if (wrapper instanceof AcceptsKnowledge) {
                            ((AcceptsKnowledge) wrapper).setKnowledge(knowledge);
                        }
                    }

                    DataType algDataType = algo.getDataType();

                    if (algo instanceof TakesGraph) {
                        ((TakesGraph) algo).setGraph(this.sourceGraph);
                    }

                    // An algorithm that takes an external graph (FASK, the pairwise orientation algorithms) is given the source
                    // graph afresh at every run. The algorithm object is carried over from one run to the next, and the external
                    // graph it was built with may be stale, or absent if the source graph was not known when it was built.
                    if (algo instanceof TakesExternalGraph && this.sourceGraph != null) {
                        ((TakesExternalGraph) algo).setExternalGraph(new SingleGraphAlg(this.sourceGraph));
                    }

                    if (this.algorithm instanceof AcceptsKnowledge) {
                        ((AcceptsKnowledge) this.algorithm).setKnowledge(this.knowledge.copy());
                    }

                    // Guard: bootstrapping requires tabular, not covariance
                    if (data instanceof ICovarianceMatrix && parameters.getInt(Params.NUMBER_RESAMPLING) > 0) {
                        throw new IllegalArgumentException("Sorry, you need a tabular dataset in order to do bootstrapping.");
                    }

                    // Type compatibility checks & run
                    boolean ok =
                            (data.isContinuous() && (algDataType == DataType.Continuous || algDataType == DataType.Mixed)) ||
                                    (data.isDiscrete() && (algDataType == DataType.Discrete || algDataType == DataType.Mixed)) ||
                                    (data.isMixed() && algDataType == DataType.Mixed);

                    if (!ok) {
                        throw new IllegalArgumentException("The algorithm was not expecting that type of data.");
                    }

                    Graph graph;
                    try {
                        graph = GraphUtils.detachNodes(algo.search(data, this.parameters));
                        graphList.add(graph);
                        graphSubtitle.put(graph, noteFor(data) + (collapsedToSpecData
                                ? "; the clusters' data set, one of " + dataModelList.size() + " in the data box"
                                : ""));

                        // Name = dataset/covariance name, if present
                        String nm = data.getName() != null ? data.getName() : "Result" + (i + 1);
                        resultNames.add(nm);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // elapsed time for dataset branch
            long stop = System.currentTimeMillis();
            this.elapsedTime = stop - start;
        }

        // Layout pass. Each result above was detached (GraphUtils.detachNodes), so its nodes are its own: it holds
        // no positions from anywhere else, and the positions the workbench writes to it reach nothing else. The
        // results of a search used to be built on the data set's variable objects, which every box on that data
        // shares, so with the conditional default layout a box inherited the layout of whichever box on the same
        // data had laid out last, however different its graph. Fixed 2026-10-5.
        //
        // Lagged (time-series) graphs are ALWAYS laid out by lag index - current slice at the bottom, earlier lags
        // in rows above - whether the lag came from the algorithm's timeLag parameter or from pre-lagged data, and
        // whether or not knowledge tiers are present: the knowledge here is the user's base (unlagged) knowledge, so
        // laying a lagged graph out by its tiers misplaces or drops the lagged nodes, and the default layout hides
        // the lag structure, which is the main thing a time-series graph is for.
        //
        // Any other result is arranged like the corresponding result of the previous search in this box, when that
        // covers all of its nodes, so that searching again (other settings, another algorithm) keeps the layout the
        // user has in this box; failing that, like the source graph, when there is one and it covers all of the
        // nodes, so that a search run against a known graph lines up with it; failing that, it is left unpositioned
        // and the workbench lays it out, with the real label sizes, when it is first shown.
        for (int i = 0; i < graphList.size(); i++) {
            Graph graph = graphList.get(i);

            if (LayoutUtil.isLaggedGraph(graph)) {
                LayoutUtil.layoutByKnowledgeIndices(graph);
                continue;
            }

            Graph reference = previous.isEmpty() ? null : previous.get(Math.min(i, previous.size() - 1));

            if (reference != null && LayoutUtil.arrangeBySourceGraph(graph, reference)) {
                continue;
            }

            if (this.sourceGraph != null && LayoutUtil.arrangeBySourceGraph(graph, this.sourceGraph)) {
                continue;
            }

            // A partial match above may have positioned some nodes; clear them so the workbench lays out the whole
            // graph rather than treating it as positioned.
            for (Node node : graph.getNodes()) {
                node.setCenter(-1, -1);
            }
        }

        // Clusters-parent mode: append the measurement edges to the selected result and collapse the result list to
        // that one graph, exactly as the Latent Structure box does.
        if (this.latentClustersRunner != null) {
            Graph graph = getGraph();

            if (graph != null) {
                attachMeasurementEdges(getBlockSpec(), graph);
                LayoutUtil.fruchtermanReingoldLayout(graph);
                this.graphList.clear();
                this.graphList.add(graph);
            }
        }

//        this.graphList = graphList;
    }

    /**
     * Checks that the spec's data set is the runner's data, by identity or, failing that, by variable names.
     *
     * @param spec       the upstream block spec.
     * @param runnerData the data model the search will run on.
     * @throws IllegalStateException if they do not match.
     */
    static void ensureSpecMatchesRunnerData(BlockSpec spec, DataModel runnerData) {
        if (spec.dataSet() == runnerData) return; // exact same object

        // Fallback: compare by variable names and count
        List<String> specNames = spec.dataSet().getVariableNames();
        List<String> runNames = runnerData.getVariableNames();

        if (specNames.size() != runNames.size() || !specNames.equals(runNames)) {
            throw new IllegalStateException(
                    "BlockSpec dataset does not match the runner's dataset. " +
                            "Ensure the clusters were derived from the same data used for this search node.");
        }
    }

    /**
     * Picks, from the data handed to this box, the data model the upstream clusters were derived from. With a single
     * data model it must be the spec's data set; with several (for example, several imputations of one data set from
     * the Impute Missing Values box), the spec's data set must be among them--the clusters box ran on one member of
     * the same list.
     *
     * @param spec  the upstream block spec.
     * @param model the data model this runner was given: a single model, or a {@link edu.cmu.tetrad.data.DataModelList}.
     * @return the member the spec was derived from.
     * @throws IllegalStateException if the spec's data set is not (among) the given data.
     */
    static DataModel resolveSpecDataModel(BlockSpec spec, DataModel model) {
        if (model instanceof DataModelList list) {
            for (DataModel member : list) {
                if (member == spec.dataSet() || spec.dataSet().equals(member)) {
                    return member;
                }
            }

            throw new IllegalStateException("The data given to this box holds " + list.size() + " data sets, and "
                    + "the supplied latent clusters were not derived from any of them. Make sure this box and the "
                    + "Latent Clusters box share the same data parent, and re-run the clusters if the data has "
                    + "changed.");
        }

        if (model == spec.dataSet() || spec.dataSet().equals(model)) {
            return model;
        }

        throw new IllegalStateException("The dataset for the supplied latent clusters is not the " +
                "dataset given as a parent to this box.");
    }

    /**
     * Appends the measurement edges of the spec--each block's latent (or block variable) directed into each of its
     * indicators--to the given result graph, adding any missing nodes.
     *
     * @param spec  the upstream block spec.
     * @param graph the result graph of the search over the block variables.
     */
    static void attachMeasurementEdges(BlockSpec spec, Graph graph) {
        for (int i = 0; i < spec.blocks().size(); i++) {
            // Use the graph's own node objects. The search may return latents that are equal to (same name
            // and type) but not identical to the BlockSpec's nodes, and Edge.getDistalNode compares by identity,
            // so an edge built from a non-identical node yields null adjacents.
            Node specVar = spec.blockVariables().get(i);
            Node var = graph.getNode(specVar.getName());

            if (var == null) {
                graph.addNode(specVar);
                var = specVar;
            }

            for (int j : spec.blocks().get(i)) {
                Node specNode2 = spec.dataSet().getVariables().get(j);
                Node node2 = graph.getNode(specNode2.getName());

                if (node2 == null) {
                    graph.addNode(specNode2);
                    node2 = specNode2;
                }

                graph.addDirectedEdge(var, node2);
            }
        }
    }

    /**
     * Returns a sample-size subtitle (of the form "n = ...") for a result graph, using the data model at the given
     * index if there is one, and otherwise the runner's data model.
     *
     * @param g         the result graph (currently unused)
     * @param indexHint the index of the data model paired with the graph, or a negative number if unknown
     * @return the subtitle, or an empty string if no sample size is available
     */
    public String getGraphSubtitle(Graph g, int indexHint) {
        // Prefer the paired data model when available and aligned by index.
        DataModelList dml = getDataModelList();
        if (dml != null && indexHint >= 0 && indexHint < dml.size()) {
            DataModel dm = dml.get(indexHint);
            if (dm instanceof ICovarianceMatrix) {
                int n = ((ICovarianceMatrix) dm).getSampleSize();
                return "n = " + n;
            } else if (dm instanceof DataSet) {
                int n = ((DataSet) dm).getNumRows();
                return "n = " + n;
            }
        }

        // Fallbacks (in case graphs outnumber datasets or source-only runs):
        if (getDataModel() instanceof ICovarianceMatrix) {
            return "n = " + ((ICovarianceMatrix) getDataModel()).getSampleSize();
        }
        if (getDataModel() instanceof DataSet) {
            return "n = " + ((DataSet) getDataModel()).getNumRows();
        }

        return ""; // No subtitle
    }

    /**
     * <p>hasMissingValues.</p>
     *
     * @return a boolean
     */
    public boolean hasMissingValues() {
        DataModelList dataModelList = getDataModelList();
        if (dataModelList.containsEmptyData()) {
            return false;
        } else {
            if (dataModelList.getFirst() instanceof CovarianceMatrix) {
                return false;
            }

            DataSet dataSet = (DataSet) dataModelList.getFirst();

            return dataSet.existsMissingValue();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * By default, algorithm do not support knowledge. Those that do will speak up.
     */
    @Override
    public boolean supportsKnowledge() {
        return false;
    }

    /**
     * Returns the display names of the result graphs, in the same order as the graphs.
     *
     * @return the result names
     */
    public List<String> getResultNames() {
        return resultNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MeekRules getMeekRules() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExternalGraph(Graph graph) {
        this.externalGraph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Graph getSourceGraph() {
        return this.sourceGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final DataModel getDataModel() {
        if (this.dataWrapper != null) {
            DataModelList dataModelList = this.dataWrapper.getDataModelList();

            if (dataModelList.size() == 1) {
                return dataModelList.getFirst();
            } else {
                return dataModelList;
            }
        } else {

            // Do not throw an exception here!
            return new BoxDataSet(new VerticalDoubleDataBox(0, 0), new ArrayList<>());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Parameters getParams() {
        return null;
    }

    /**
     * <p>getDataModelList.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public final DataModelList getDataModelList() {
        if (this.dataWrapper == null) {
            return new DataModelList();
        }
        return this.dataWrapper.getDataModelList();
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public final Parameters getParameters() {
        return this.parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getResettableParams() {
        return this.getParameters();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetParams(Object params) {
        this.parameters = (Parameters) params;
    }

    //===========================PRIVATE METHODS==========================//
    private void transferVarNamesToParams(List<String> names) {
        getParameters().set("varNames", names);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getIndependenceTest() {
        if (this.independenceTests == null) {
            this.independenceTests = new ArrayList<>();
        }

        if (this.independenceTests.size() == 1) {
            return this.independenceTests.getFirst();
        }

        Algorithm algo = getAlgorithm();

        if (getDataModelList().isEmpty() && getSourceGraph() != null) {
            // We inject the graph to the test to satisfy the tests like MSeparationTest - Zhou
            IndependenceWrapper test = new MSeparationTest(getSourceGraph());

            if (this.independenceTests == null) {
                this.independenceTests = new ArrayList<>();
            }

            // Grabbing this independence test for the independence tests interface. JR 2020.8.24
//            IndependenceTest test = indTestWrapper.getTest(null, parameters);
            this.independenceTests.add(test.getTest(null, this.parameters));
        } else if (algo instanceof TakesIndependenceWrapper) {
            if (getDataModelList().size() == 1) {
                IndependenceWrapper indTestWrapper = ((TakesIndependenceWrapper) getAlgorithm()).getIndependenceWrapper();

                if (this.independenceTests == null) {
                    this.independenceTests = new ArrayList<>();
                }

                if (indTestWrapper instanceof BlockIndependenceWrapper) {
                    ((BlockIndependenceWrapper) indTestWrapper).setBlockSpec(blockSpec);
                }

                // Grabbing this independence test for the independence tests interface. JR 2020.8.24
                IndependenceTest test = indTestWrapper.getTest(getDataModelList().get(0), this.parameters);
                this.independenceTests.add(test);
            }
        } else if (algo instanceof TakesScoreWrapper) {
            if (getDataModelList().size() == 1) {
                ScoreWrapper wrapper = ((TakesScoreWrapper) getAlgorithm()).getScoreWrapper();

                if (this.independenceTests == null) {
                    this.independenceTests = new ArrayList<>();
                }

                if (wrapper instanceof BlockScoreWrapper) {
                    ((BlockScoreWrapper) wrapper).setBlockSpec(blockSpec);
                }

                // Grabbing this independence score for the independence tests interface. JR 2020.8.24
                Score score = wrapper.getScore(getDataModelList().get(0), this.parameters);
                this.independenceTests.add(new ScoreIndTest(score));
            }
        }

        if (this.independenceTests.isEmpty()) {
            throw new IllegalArgumentException("One or more of the parents was a search that didn't use "
                    + "a test or a score.");
        }

        return this.independenceTests.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>algorithm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public Algorithm getAlgorithm() {
        if (this.algorithm instanceof ExtraLatentStructureAlgorithm) {
            ((ExtraLatentStructureAlgorithm) this.algorithm).setBlockSpec(blockSpec);
        }

        return this.algorithm;
    }

    /**
     * <p>Setter for the field <code>algorithm</code>.</p>
     *
     * @param algorithm a {@link edu.cmu.tetrad.algcomparison.algorithm.Algorithm} object
     */
    public void setAlgorithm(Algorithm algorithm) {
        if (algorithm == null) {
            throw new NullPointerException("Algorithm not specified");
        }
        this.algorithm = algorithm;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getTriplesClassificationTypes() {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getParamSettings() {
        return Collections.EMPTY_MAP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return Collections.EMPTY_MAP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getGraph() {
        if (this.graphList == null || this.graphList.isEmpty()) {
            return null;
        } else {
            return this.graphList.get(getSelectedResultIndex());
        }
    }

    /**
     * The index of the result graph that getGraph() returns, and so the one downstream boxes analyze.
     *
     * @return the index, always within the list of results (0 if there are none)
     */
    public int getSelectedResultIndex() {
        int size = this.graphList == null ? 0 : this.graphList.size();
        if (this.selectedResultIndex < 0 || this.selectedResultIndex >= size) return 0;
        return this.selectedResultIndex;
    }

    /**
     * Sets which result graph getGraph() returns. Downstream boxes that were built from the previous selection
     * are not changed by this call; they need to be re-executed.
     *
     * @param index the index of the result
     */
    public void setSelectedResultIndex(int index) {
        this.selectedResultIndex = index;
    }

    /**
     * The name of the selected result, as shown on its tab in the search editor.
     *
     * @return the name, or null if the search has at most one result, in which case there is nothing to say
     */
    public String getSelectedResultName() {
        if (this.graphList == null || this.graphList.size() < 2) return null;
        int index = getSelectedResultIndex();
        if (this.resultNames != null && index < this.resultNames.size()) return this.resultNames.get(index);
        return "Result " + (index + 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Graph> getGraphs() {
        return this.graphList;
    }

    /**
     * Replaces the result graphs and resets the selected result to the first one.
     *
     * @param gs the new result graphs
     */
    public void setResultGraphs(List<Graph> gs) {
        this.graphList = gs;
        this.selectedResultIndex = 0;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * <p>Getter for the field <code>dataWrapper</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public DataWrapper getDataWrapper() {
        return this.dataWrapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Node> getVariables() {
        return Collections.EMPTY_LIST;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getVariableNames() {
        return Collections.EMPTY_LIST;
    }

    /**
     * <p>getCompareGraphs.</p>
     *
     * @param graphs a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public List<Graph> getCompareGraphs(List<Graph> graphs) {
        if (graphs == null) {
            throw new NullPointerException();
        }

        List<Graph> compareGraphs = new ArrayList<>();

        for (Graph graph : graphs) {
            compareGraphs.add(this.algorithm.getComparisonGraph(graph));
        }

        return compareGraphs;
    }

    /**
     * <p>Getter for the field <code>userAlgoSelections</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> getUserAlgoSelections() {
        return this.userAlgoSelections;
    }

    /**
     * Returns the elapsed time for the algorithm to run, in milliseconds.
     *
     * @return the elapsed time for the algorithm to run. If the algorithm does not (or has not) run, this is set to -1.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Returns the block specification used by block-based tests, scores and algorithms.
     *
     * @return the block specification, or null if none
     */
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Sets the block specification used by block-based tests, scores and algorithms.
     *
     * @param blockSpec the block specification, or null for none
     */
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }
}

