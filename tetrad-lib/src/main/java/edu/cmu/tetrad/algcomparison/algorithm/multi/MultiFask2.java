package edu.cmu.tetrad.algcomparison.algorithm.multi;

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SemBicScoreImages;
import edu.cmu.tetrad.search.TimeSeriesUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.bootstrap.BootstrapEdgeEnsemble;
import edu.pitt.dbmi.algo.bootstrap.GeneralBootstrapTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps the IMaGES algorithm for continuous variables.
 * </p>
 * Requires that the parameter 'randomSelectionSize' be set to indicate how many
 * datasets should be taken at a time (randomly). This cannot given multiple
 * values.
 *
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "MultiFAsk2",
        command = "mult_fask2",
        algoType = AlgType.forbid_latent_common_causes,
        description = ""
)
public class MultiFask2 implements MultiDataSetAlgorithm, HasKnowledge {

    static final long serialVersionUID = 23L;
    private IKnowledge knowledge = new Knowledge2();

    public MultiFask2() {
    }

    @Override
    public Graph search(List<DataModel> dataSets, Parameters parameters) {
    	if (parameters.getInt("bootstrapSampleSize") < 1) {
            final SemBicScoreImages score = new SemBicScoreImages(dataSets);
            score.setPenaltyDiscount(parameters.getDouble("penaltyDiscount"));
            edu.cmu.tetrad.search.Fges search = new edu.cmu.tetrad.search.Fges(score);
            search.setKnowledge(knowledge);
            final Graph imagesGraph = search.search();

            List<DataSet> _dataSets = new ArrayList<>();
            for (DataModel dataSet : dataSets) {
                final DataSet dataSet1 = (DataSet) dataSet;
//                _dataSets.add(dataSet1);

                DataSet model = TimeSeriesUtils.ar2((DataSet) dataSet, 1);
                _dataSets.add(model);
            }



            List<Graph> faskGraphs = new ArrayList<>();

            for (DataSet dataSet : _dataSets) {
                edu.cmu.tetrad.search.Fask fask = new edu.cmu.tetrad.search.Fask(dataSet,
                        new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(dataSet)));
                fask.setInitialGraph(imagesGraph);
                faskGraphs.add(fask.search());
            }

            Graph voted = new EdgeListGraph(imagesGraph.getNodes());

            for (Edge edge : imagesGraph.getEdges()) {
                int left = 0;
                int right= 0;
                int twocycle = 0;

                for (Graph graph : faskGraphs) {
                    List<Edge> edges = graph.getEdges(edge.getNode1(), edge.getNode2());
                    if (edges.isEmpty()) continue;

                    if (edges.size() == 2) twocycle++;

                    Edge e = edges.get(0);

                    if (e.pointsTowards(edge.getNode1())) left++;
                    if (e.pointsTowards(edge.getNode2())) right++;
                }

                if (left > right && left > twocycle) voted.addEdge(Edges.directedEdge(edge.getNode2(), edge.getNode1()));
                else if (right > left && right > twocycle) voted.addEdge(Edges.directedEdge(edge.getNode1(), edge.getNode2()));
                else {
                    voted.addEdge(Edges.directedEdge(edge.getNode2(), edge.getNode1()));
                    voted.addEdge(Edges.directedEdge(edge.getNode1(), edge.getNode2()));
                }

            }

//            final DataSet dataModel = DataUtils.concatenate(_dataSets);
//            edu.cmu.tetrad.search.Fask fask = new edu.cmu.tetrad.search.Fask(dataModel,
//                    new edu.cmu.tetrad.search.SemBicScore(new CovarianceMatrixOnTheFly(dataModel)));

//            fask.setInitialGraph(imagesGraph);

            return voted;
        } else {
            MultiFask2 imagesSemBic = new MultiFask2();
            //imagesSemBic.setKnowledge(knowledge);

            List<DataSet> datasets = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                datasets.add((DataSet) dataModel);
            }
            GeneralBootstrapTest search = new GeneralBootstrapTest(datasets, imagesSemBic,
                    parameters.getInt("bootstrapSampleSize"));
            search.setKnowledge(knowledge);

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph search(DataModel dataSet, Parameters parameters) {
        if (!parameters.getBoolean("bootstrapping")) {
            return search(Collections.singletonList((DataModel) DataUtils.getContinuousDataSet(dataSet)), parameters);
        } else {
            MultiFask2 imagesSemBic = new MultiFask2();
            imagesSemBic.setKnowledge(knowledge);

            List<DataSet> dataSets = Collections.singletonList(DataUtils.getContinuousDataSet(dataSet));
            GeneralBootstrapTest search = new GeneralBootstrapTest(dataSets, imagesSemBic,
                    parameters.getInt("bootstrapSampleSize"));

            BootstrapEdgeEnsemble edgeEnsemble = BootstrapEdgeEnsemble.Highest;
            switch (parameters.getInt("bootstrapEnsemble", 1)) {
                case 0:
                    edgeEnsemble = BootstrapEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = BootstrapEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = BootstrapEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean("verbose"));
            return search.search();
        }
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
//        return SearchGraphUtils.patternForDag(graph);
//        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    @Override
    public String getDescription() {
        return "MultiFast2)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new Fges(new SemBicScore(), false).getParameters();
        parameters.add("numRuns");
        parameters.add("randomSelectionSize");

        parameters.add("depth");
        parameters.add("twoCycleAlpha");
        parameters.add("extraEdgeThreshold");
        parameters.add("faskDelta");

        parameters.add("useFasAdjacencies");
        parameters.add("useCorrDiffAdjacencies");

        // Bootstrapping
        parameters.add("bootstrapSampleSize");
        parameters.add("bootstrapEnsemble");
        parameters.add("verbose");

        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return knowledge;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {
        this.knowledge = knowledge;
    }
}
