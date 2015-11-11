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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * Runs GES on the (hopefully small) list of variables, containing the given target, and trims the result to a Markov
 * blanket DAG for the target.
 * <p/>
 * All of the variables must be in the specified data set.
 * <p/>
 * The intended use for this is to filter the results of some other method of estimating the Markov blanket of thet
 * target, which includes the target, hopefully almost all (if not all) of the actual Markov blanket variables, and
 * possibly a few extra variables, to produce a good estimate of the Markov blanket DAG.
 *
 * @author Joseph Ramsey
 */
public class GesMbFilter {

    private DataSet dataSet;
    private Ges search;

    public GesMbFilter(DataSet dataSet) {
        this.dataSet = dataSet;
        search = new Ges(dataSet);
        search.setSamplePrior(10.0);
        search.setStructurePrior(0.01);
    }

    public Graph filter(List<Node> variable, Node target) {
        List<Node> dataVars = new LinkedList<Node>();

        for (Node node1 : variable) {
            dataVars.add(dataSet.getVariable(node1.getName()));
        }

        Graph mbPattern = search.search(new LinkedList<Node>(dataVars));
        Node dataTarget = dataSet.getVariable(target.getName());

        MbUtils.trimToMbNodes(mbPattern, dataTarget, false);
        MbUtils.trimEdgesAmongParents(mbPattern, dataTarget);
        MbUtils.trimEdgesAmongParentsOfChildren(mbPattern, dataTarget);

        return mbPattern;
    }
}



