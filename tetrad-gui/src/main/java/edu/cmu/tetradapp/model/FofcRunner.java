///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.ReidentifyVariables;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Unmarshallable;

import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the
 * BuildPureClusters algorithm.
 *
 * @author Ricardo Silva
 */
public class FofcRunner extends AbstractMimRunner
        implements GraphSource, KnowledgeBoxInput, Unmarshallable {
    static final long serialVersionUID = 23L;

    /**
     * To reidentify variables.
     */
    private SemIm semIm;
    private Graph trueGraph;

    //============================CONSTRUCTORS============================//

    public FofcRunner(DataWrapper dataWrapper,
                                   FofcParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);

    }

    public FofcRunner(DataWrapper dataWrapper, SemImWrapper semImWrapper,
                                   FofcParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);
        this.semIm = semImWrapper.getSemIm();
        this.trueGraph = semIm.getSemPm().getGraph();
    }

    public FofcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper,
                                   FofcParams pureClustersParams) {
        super(dataWrapper, pureClustersParams.getClusters(), pureClustersParams);
        this.trueGraph = graphWrapper.getGraph();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static FofcRunner serializableInstance() {
        return new FofcRunner(DataWrapper.serializableInstance(),
                FofcParams.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        Graph searchGraph;

        FindOneFactorClusters fofc;
        Object source = getData();
        TestType tetradTestType = getParams().getTetradTestType();
        if (tetradTestType == null || (!(tetradTestType == TestType.TETRAD_DELTA ||
                tetradTestType == TestType.TETRAD_WISHART))) {
            tetradTestType = TestType.TETRAD_DELTA;
            getParams().setTetradTestType(tetradTestType);
        }

        FindOneFactorClusters.Algorithm algorithm = ((FofcIndTestParams) getParams().getMimIndTestParams()).getAlgorithm();

        if (source instanceof DataSet) {
            fofc = new FindOneFactorClusters((DataSet) source, tetradTestType, algorithm, getParams().getAlpha());
            searchGraph = fofc.search();
        } else if (source instanceof CovarianceMatrix) {
            fofc = new FindOneFactorClusters((CovarianceMatrix) source, tetradTestType, algorithm, getParams().getAlpha());
            searchGraph = fofc.search();
        } else {
            throw new IllegalArgumentException("Unrecognized data type.");
        }

        if (semIm != null) {
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);

            List<String> variableNames = ReidentifyVariables.reidentifyVariables2(partition, trueGraph, (DataSet) getData());
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables2(searchGraph, semIm);
        } else if (trueGraph != null) {
            List<List<Node>> partition = MimUtils.convertToClusters2(searchGraph);
            List<String> variableNames = ReidentifyVariables.reidentifyVariables1(partition, trueGraph);
            rename(searchGraph, partition, variableNames);
//            searchGraph = reidentifyVariables(searchGraph, trueGraph);
        }

        System.out.println("Search Graph " + searchGraph);


        try {
            Graph graph = new MarshalledObject<>(searchGraph).get();
            GraphUtils.circleLayout(graph, 200, 200, 150);
            GraphUtils.fruchtermanReingoldLayout(graph);
            setResultGraph(graph);
            setClusters(MimUtils.convertToClusters(graph, getData().getVariables()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    private void rename(Graph searchGraph, List<List<Node>> partition, List<String> variableNames) {
        for (Node node : searchGraph.getNodes()) {
            if (!(node.getNodeType() == NodeType.LATENT)) {
                continue;
            }

            List<Node> children = searchGraph.getChildren(node);
            children.removeAll(ReidentifyVariables.getLatents(searchGraph));

            for (int i = 0; i < partition.size(); i++) {
                if (new HashSet<>(partition.get(i)).equals(new HashSet<>(children))) {
                    node.setName(variableNames.get(i));
                }
            }
        }
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    public List<Node> getVariables() {
        List<Node> latents = new ArrayList<>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return MimBuild.generateLatentNames(partition.size());
    }
}





