///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Purify algorithm.
 *
 * @author Ricardo Silva
 * @version $Id: $Id
 */
public class PurifyRunner extends AbstractMimRunner implements GraphSource, KnowledgeBoxInput {
    private static final long serialVersionUID = 23L;

    //============================CONSTRUCTORS============================//

    /**
     * <p>Constructor for PurifyRunner.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param mmWrapper   a {@link edu.cmu.tetradapp.model.MeasurementModelWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PurifyRunner(DataWrapper dataWrapper,
                        MeasurementModelWrapper mmWrapper,
                        Parameters params) {
        super(dataWrapper, mmWrapper.getClusters(), params);
        setClusters(mmWrapper.getClusters());
        params.set("clusters", mmWrapper.getClusters());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */
    public void execute() {
        Object source = getData();

        TetradTest test;


        System.out.println("Clusters " + getParams().get("clusters", null));

        if (source instanceof ICovarianceMatrix covMatrix) {
            CorrelationMatrix corrMatrix = new CorrelationMatrix(covMatrix);
            double alpha = getParams().getDouble("alpha", 0.001);
            BpcTestType sigTestType = (BpcTestType) getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART);
            test = new TetradTestContinuous(covMatrix, sigTestType, alpha);
//            sextadTest = new DeltaSextadTest(covMatrix);
        } else if (source instanceof DataSet data) {
            double alpha = getParams().getDouble("alpha", 0.001);
            BpcTestType sigTestType = (BpcTestType) getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART);
            test = new TetradTestContinuous(data, sigTestType, alpha);
//            sextadTest = new DeltaSextadTest(data);
        } else {
            throw new RuntimeException(
                    "Data source for Purify of invalid type!");
        }

        List<List<Node>> inputPartition = ClusterUtils.clustersToPartition((Clusters) getParams().get("clusters", null), test.getVariables());

        IPurify purify = new PurifyTetradBased(test);


        List<List<Node>> partition = purify.purify(inputPartition);

        Clusters outputClusters = ClusterUtils.partitionToClusters(partition);

        List<int[]> partitionAsInts = ClusterUtils.convertListToInt(partition, test.getVariables());
        setResultGraph(ClusterUtils.convertSearchGraph(partitionAsInts, test.getVarNames()));
        LayoutUtil.fruchtermanReingoldLayout(getResultGraph());

        setClusters(outputClusters);
    }

    /**
     * <p>getClusters.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Clusters} object
     */
    public Clusters getClusters() {
        return super.getClusters();
    }

    /**
     * {@inheritDoc}
     */
    protected void setClusters(Clusters clusters) {
        super.setClusters(clusters);
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public java.util.List<Node> getVariables() {
        List<Node> latents = new ArrayList<>();

        for (String name : getVariableNames()) {
            Node node = new ContinuousVariable(name);
            node.setNodeType(NodeType.LATENT);
            latents.add(node);
        }

        return latents;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        List<List<Node>> partition = ClusterUtils.clustersToPartition(getClusters(),
                getData().getVariables());
        return ClusterUtils.generateLatentNames(partition.size());
    }

}




