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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.InverseCorrelation;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author Joseph Ramsey
 */
public class InverseCorrelationRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public InverseCorrelationRunner(DataWrapper dataWrapper) {
        super(dataWrapper, new GlassoSearchParams(), null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static InverseCorrelationRunner serializableInstance() {
        return new InverseCorrelationRunner(DataWrapper.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        Object dataModel = getDataModel();

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            GlassoSearchParams params = (GlassoSearchParams) getParams();

            InverseCorrelation search = new InverseCorrelation(dataSet, params.getThr());
            Graph graph = search.search();

            setResultGraph(graph);
        }
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getAlgorithmName() {
        return "Inverse-Correlation";
    }
}





