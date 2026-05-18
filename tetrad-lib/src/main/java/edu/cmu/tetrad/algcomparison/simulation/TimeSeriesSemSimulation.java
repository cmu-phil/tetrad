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

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TMath;

import java.io.Serial;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Time series SEM simulation.
 *
 * @author josephramsey
 * @author danielmalinsky
 * @version $Id: $Id
 */
public class TimeSeriesSemSimulation implements Simulation, AcceptsKnowledge {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The expanded knowledge derived from the lagged graph structure, and optionally
     * augmented with the expanded within-lag knowledge. This is what gets stamped onto
     * each dataset and passed to the search algorithm.
     */
    private Knowledge knowledge;

    /**
     * Within-lag knowledge supplied by the user over base variable names (no lag suffix).
     * May be null. If non-null, this is expanded across all lags in createData and merged
     * into the structural knowledge.
     */
    private Knowledge withinLagKnowledge = null;

    /* -------------------- Constructors -------------------- */

    /**
     * Creates a new TimeSeriesSemSimulation with the given random graph and no
     * within-lag knowledge.
     *
     * @param randomGraph a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     */
    public TimeSeriesSemSimulation(RandomGraph randomGraph) {
        if (randomGraph == null) {
            throw new NullPointerException();
        }
        this.randomGraph = randomGraph;
    }

    /**
     * Creates a new TimeSeriesSemSimulation with the given random graph and within-lag
     * knowledge over base variable names (no lag suffix). The knowledge will be expanded
     * across all time lags when createData is called.
     *
     * @param randomGraph      a {@link edu.cmu.tetrad.algcomparison.graph.RandomGraph} object
     * @param knowledge        within-lag knowledge over base variable names; may be null
     */
    public TimeSeriesSemSimulation(RandomGraph randomGraph, Knowledge knowledge) {
        if (randomGraph == null) {
            throw new NullPointerException();
        }
        this.randomGraph = randomGraph;
        this.withinLagKnowledge = (knowledge != null) ? knowledge.copy() : null;
    }

    /* -------------------- Static utilities -------------------- */

    /**
     * <p>topToBottomLayout.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public static void topToBottomLayout(TimeLagGraph graph) {
        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + _node);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

    /* -------------------- Simulation -------------------- */

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        Graph graph = this.randomGraph.createGraph(parameters);
        int numLags = parameters.getInt(Params.NUM_LAGS);
        int numExtraLagged = (int) TMath.floor(graph.getNumEdges() * 1.5);
        graph = TsUtils.graphToLagGraph(graph, numLags, numExtraLagged);
        LayoutUtil.layoutByKnowledgeIndices(graph);

        // Derive structural knowledge from the lagged graph
        this.knowledge = TsUtils.getKnowledge(graph);

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
                graph = TsUtils.graphToLagGraph(graph, numLags, numExtraLagged);
                LayoutUtil.layoutByKnowledgeIndices(graph);
            }

            this.graphs.add(graph);

            SemPm pm = new SemPm(graph);
            SemIm im = new SemIm(pm, parameters);

            int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);
            boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

            DataSet dataSet;
            try {
                dataSet = im.simulateData(sampleSize, saveLatentVars);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            // If within-lag knowledge was supplied, expand it across lags and
            // use the resulting knowledge in place of the purely structural one.
            if (withinLagKnowledge != null) {
                DataSet laggedData = TsUtils.createLagData(dataSet, numLags, withinLagKnowledge);
                this.knowledge = laggedData.getKnowledge();
            }

            dataSet.setName("Run " + (i + 1));
            dataSet.setKnowledge(this.knowledge.copy());
            this.dataSets.add(dataSet);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Linear, Gaussian Dynamic SEM (1-lag SVAR) simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return "Time Series SEM Simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.NUM_LAGS);

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.addAll(SemIm.getParameterNames());

        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);

        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
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
     * <p>
     * Sets the within-lag knowledge. This knowledge should be over base variable
     * names only (no lag suffix); it will be expanded across all time lags the
     * next time createData is called.
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.withinLagKnowledge = (knowledge != null) ? new Knowledge(knowledge) : null;
    }
}