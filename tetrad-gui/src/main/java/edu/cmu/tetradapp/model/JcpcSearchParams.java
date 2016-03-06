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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Stub for serialization.
 *
 * @deprecated
 */

public final class JcpcSearchParams implements SearchParams {
    static final long serialVersionUID = 23L;

    public static PcRunner serializableInstance() {
        return new PcRunner(Dag.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    @Override
    public IKnowledge getKnowledge() {
        return null;
    }

    @Override
    public void setKnowledge(IKnowledge knowledge) {

    }

    @Override
    public IndTestParams getIndTestParams() {
        return null;
    }

    @Override
    public void setIndTestParams(IndTestParams params) {

    }

    @Override
    public List<String> getVarNames() {
        return null;
    }

    @Override
    public void setVarNames(List<String> varNames) {

    }

    @Override
    public Graph getSourceGraph() {
        return null;
    }

    @Override
    public void setSourceGraph(Graph graph) {

    }

    @Override
    public void setIndTestType(IndTestType testType) {

    }

    @Override
    public IndTestType getIndTestType() {
        return null;
    }

    @Override
    public void setIndependenceFacts(IndependenceFacts facts) {

    }
}





