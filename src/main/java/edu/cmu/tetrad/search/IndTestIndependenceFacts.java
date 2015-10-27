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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks conditional independence against a list of conditional independence facts, manually entered.
 *
 * @author Joseph Ramsey
 * @see edu.cmu.tetrad.search.ChiSquareTest
 */
public final class IndTestIndependenceFacts implements IndependenceTest {

    private IndependenceFacts facts;

    public IndTestIndependenceFacts(IndependenceFacts facts) {
        this.facts = facts;

//        System.out.println("Independence Facts for test: ");
//        System.out.println(facts);
    }


    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    public boolean isIndependent(Node x, Node y, List<Node> z) {
        Node[] _z = new Node[z.size()];

        for (int i = 0; i < z.size(); i++) {
            _z[i] = z.get(i);
        }

        boolean independent = facts.isIndependent(x, y, _z);

        if (independent) {
            TetradLogger.getInstance().log("independencies",
                    SearchLogUtils.independenceFactMsg(x, y, z, Double.NaN));
//            System.out.println(SearchLogUtils.independenceFactMsg(x, y, z, Double.NaN));
        } else {
            TetradLogger.getInstance().log("dependencies",
                    SearchLogUtils.dependenceFactMsg(x, y, z, Double.NaN));
//            System.out.println(SearchLogUtils.dependenceFactMsg(x, y, z, Double.NaN));
        }

        return independent;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zz = new ArrayList<Node>();

        for (Node node : z) {
            zz.add(node);
        }

        return isIndependent(x, y, zz);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        return !isIndependent(x, y, z);
    }

    public double getPValue() {
        return Double.NaN;
    }

    public List<Node> getVariables() {
        return facts.getVariables();
    }

    public Node getVariable(String name) {
        if (name == null) throw new NullPointerException();

        List<Node> variables = facts.getVariables();

        for (Node node : variables) {
            if (name.equals(node.getName())) {
                return node;
            }
        }

        return null;
    }

    public List<String> getVariableNames() {
        return facts.getVariableNames();
    }

    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    public double getAlpha() {
        return Double.NaN;
    }

    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException();
    }

    public DataModel getData() {
        return facts;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

}





