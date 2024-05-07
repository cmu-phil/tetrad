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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Calculates partial correlation using the recursive method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class PartialCorrelation {

    private final ICovarianceMatrix corr;
    private final Map<Node, Integer> nodesMap = new HashMap<>();

    /**
     * Constructor.
     *
     * @param nodes      The lsit of nodes.
     * @param cov        The covariance matrix, as a Matrix.
     * @param sampleSize The sample size.
     */
    public PartialCorrelation(List<Node> nodes, Matrix cov, int sampleSize) {
        this.corr = new CorrelationMatrix(new CovarianceMatrix(nodes, cov, sampleSize));
        for (int i = 0; i < nodes.size(); i++) this.nodesMap.put(nodes.get(i), i);
    }

    /**
     * Constructor
     *
     * @param cov The covariance matrix, as an ICovariance object.
     */
    public PartialCorrelation(ICovarianceMatrix cov) {
        this.corr = new CorrelationMatrix(cov);
        List<Node> nodes = this.corr.getVariables();
        for (int i = 0; i < nodes.size(); i++) this.nodesMap.put(nodes.get(i), i);
    }

    /**
     * Calculates the partial correlation of x and y conditional on the nodes in z recursively.
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link java.util.List} object
     * @return this partial correlation.
     */
    public double corr(Node x, Node y, List<Node> z) {
        if (z.isEmpty()) return this.corr.getValue(this.nodesMap.get(x), this.nodesMap.get(y));
        Node z0 = z.get(0);
        List<Node> _z = new ArrayList<>(z);
        _z.remove(z0);
        double corr0 = corr(x, y, _z);
        double corr1 = corr(x, z0, _z);
        double corr2 = corr(z0, y, _z);
        return (corr0 - corr1 * corr2) / sqrt(1. - corr1 * corr1) * sqrt(1. - corr2 * corr2);
    }
}



