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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.List;


/**
 * For serialization compatibility only. The algorithm has been moved.
 *
 * @author Joseph Ramsey
 */
public class AfciRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;

    public AfciRunner(DataWrapper dataWrapper, SearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public AfciRunner(DataWrapper dataWrapper, SearchParams params) {
        super(dataWrapper, params, null);
    }

   
    public AfciRunner(Graph sourceGraph, SearchParams params) {
        super(sourceGraph, params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static AfciRunner serializableInstance() {
        Dag dag = new Dag();
        GraphNode node1 = new GraphNode("X");
        dag.addNode(node1);
        return new AfciRunner(DataWrapper.serializableInstance(), FciSearchParams.serializableInstance(), KnowledgeBoxModel.serializableInstance());
    }

    public void execute() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Graph getGraph() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<String> getTriplesClassificationTypes() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<List<Triple>> getTriplesLists(Node node) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public IndependenceTest getIndependenceTest() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}


