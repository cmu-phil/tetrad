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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CorrelationMatrixOnTheFly;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Created by josephramsey on 4/13/14.
 */
public class RecursivePartialCorrelation {

    private ICovarianceMatrix corr;
    private final Map<Node, Integer> nodesMap = new HashMap<>();

    public RecursivePartialCorrelation(List<Node> nodes, TetradMatrix cov, int sampleSize) {
        this.corr = new CorrelationMatrixOnTheFly(new CovarianceMatrix(nodes, cov, sampleSize));
        for (int i = 0; i < nodes.size(); i++) nodesMap.put(nodes.get(i), i);
    }

    public RecursivePartialCorrelation(ICovarianceMatrix cov) {
        this.corr = new CorrelationMatrixOnTheFly(cov);
        List<Node> nodes = corr.getVariables();
        for (int i = 0; i < nodes.size(); i++) nodesMap.put(nodes.get(i), i);
    }

    public double corr(Node x, Node y, List<Node> z) {
        if (z.isEmpty()) return  this.corr.getValue(nodesMap.get(x), nodesMap.get(y));
        Node z0 = z.get(0);
        List<Node> _z = new ArrayList<>(z);
        _z.remove(z0);
        double corr0 = corr(x, y, _z);
        double corr1 = corr(x, z0, _z);
        double corr2 = corr(z0, y, _z);
        return (corr0 - corr1 * corr2) / sqrt(1. - corr1 * corr1) * sqrt(1. - corr2 * corr2);
    }
}



