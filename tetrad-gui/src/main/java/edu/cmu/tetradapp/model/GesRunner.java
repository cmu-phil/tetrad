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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.IndependenceTest;

import java.util.List;


/**
 * Stub for serialization
 *
 * @deprecated
 */
public class GesRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, GraphSource {
    static final long serialVersionUID = 23L;
    private Graph trueGraph;
    private Graph sourceGraph;

    public static PcRunner serializableInstance() {
        return new PcRunner(Dag.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    public GesRunner(DataWrapper dataWrapper, SearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    @Override
    public String getAlgorithmName() {
        return "GES";
    }

    @Override
    public void execute() {

    }

    @Override
    public Graph getGraph() {
        return null;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return null;
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return null;
    }
}


