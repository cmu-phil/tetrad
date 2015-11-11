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

import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Created by josephramsey on 4/13/14.
 */
public class RecursivePartialCorrelation {

    private final TetradMatrix corr;
    private final Map<Node, Integer> nodesMap = new HashMap<Node, Integer>();
    private Map<IndependenceFact, Double> memory = new WeakHashMap<IndependenceFact, Double>();

    public RecursivePartialCorrelation(List<Node> nodes, TetradMatrix cov) {
        this.corr = MatrixUtils.convertCovToCorr(new TetradMatrix(cov));
        for (int i = 0; i < nodes.size(); i++) nodesMap.put(nodes.get(i), i);
    }

    public double corr(Node x, Node y, List<Node> z) {
        if (z.isEmpty()) return this.corr.get(nodesMap.get(x), nodesMap.get(y));
//        if (z.size() == 1) {
//            int _x = nodesMap.get(x);
//            int _y = nodesMap.get(y);
//            int _z = nodesMap.get(z.get(0));
//
//            double c1 = corr.get(_x, _y);
//            double c2 = corr.get(_y, _z);
//            double c3 = corr.get(_x, _z);
//            return (c1 - c2 * c3) / sqrt(1 - c2 * c2) * sqrt(1 - c3 * c3);
//        }
        IndependenceFact spec = spec(x, y, z);
        if (memory.containsKey(spec)) {
            Double corr = memory.get(spec);
            if (corr != null) return corr;
        }
        Node z0 = z.get(0);

//        for (Node _z0 : z) {
//            z0 = _z0;
//            List<Node> _z = new ArrayList<Node>(z);
//            _z.remove(_z0);
//            if (memory.containsKey(spec(x, y, _z))) {
//                break;
//            }
//        }

        List<Node> _z = new ArrayList<Node>(z);
        _z.remove(z0);

        double corr0 = corr(x, y, _z);
        double corr1 = corr(x, z0, _z);
        double corr2 = corr(z0, y, _z);
        double corr3 = (corr0 - corr1 * corr2) / sqrt(1. - corr1 * corr1) * sqrt(1. - corr2 * corr2);

        memory.put(spec, corr3);

        return corr3;
    }

    private IndependenceFact spec(Node x, Node y, List<Node> z) {
        return new IndependenceFact(x, y, z);
    }
}



