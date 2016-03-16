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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.Ccd;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.TetradVector;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the CCD algorithm.
 *
 * @author Frank Wimberly after Shane Harwood's PcRunner
 */

public class CcdRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public CcdRunner(DataWrapper dataWrapper, BasicSearchParams params) {
        super(dataWrapper, params, null);
    }

    public CcdRunner(DataWrapper dataWrapper, KnowledgeBoxModel knowledgeBoxModel,  BasicSearchParams params) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public CcdRunner(DataWrapper dataWrapper, GraphWrapper initialGraph, BasicSearchParams params) {
        super(dataWrapper, params);
        setInitialGraph(initialGraph.getGraph());
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public CcdRunner(GraphSource graphWrapper, PcSearchParams params) {
        super(graphWrapper.getGraph(), params, null);
    }
    
   
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public CcdRunner(GraphWrapper graphWrapper, BasicSearchParams params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public CcdRunner(GraphWrapper graphWrapper, KnowledgeBoxModel knowledgeBoxModel, BasicSearchParams params) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }
    
    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public CcdRunner(DagWrapper dagWrapper, BasicSearchParams params) {
        super(dagWrapper.getDag(), params);
    }
    
    public CcdRunner(SemGraphWrapper dagWrapper, BasicSearchParams params) {
        super(dagWrapper.getGraph(), params);
    }

    public CcdRunner(IndependenceFactsModel model, BasicSearchParams params) {
        super(model, params, null);
    }

    public CcdRunner(IndependenceFactsModel model, BasicSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

	/**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static CcdRunner serializableInstance() {
        return new CcdRunner(DataWrapper.serializableInstance(), BasicSearchParams.serializableInstance());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        Ccd ccd = new Ccd(getIndependenceTest());
        ccd.setDepth(getParams().getIndTestParams().getDepth());
        ccd.setKnowledge(getParams().getKnowledge());
        Graph graph = ccd.search();

        setResultGraph(graph);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        }
        else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        IndTestType testType = (getParams()).getIndTestType();
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }


    /**
     * @return the names of the triple classifications. Coordinates with <code>getTriplesList</code>
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<String>();
        names.add("Underlines");
        names.add("Dotted Underlines");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code> for the given
     * node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        return triplesList;
    }

    @Override
    public String getAlgorithmName() {
        return "CCD";
    }
}





