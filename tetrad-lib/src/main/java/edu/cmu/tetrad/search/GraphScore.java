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
import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Implements the continuous BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class GraphScore implements GesScore {

    private final Graph dag;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    /**
     * Constructs the score using a covariance matrix.
     */
    public GraphScore(Graph dag) {
        this.dag = dag;

        this.variables = new ArrayList<>();

        for (Node node : dag.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

//        Collections.shuffle(this.variables);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int[] parents) {
        throw new UnsupportedOperationException();
    }


    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }

    private Set<Node> getVariableSet(int[] indices) {
        Set<Node> variables = new HashSet<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }

        return variables;
    }


    @Override
    public synchronized double localScoreDiff(int i, int[] parents, int extra) {


        Node y = variables.get(i);
        Node x = variables.get(extra);
        List<Node> scoreParents = getVariableList(parents);
//        scoreParents.add(x);

//        if (scoreParents.contains(x)) throw new RuntimeException("Score parents containx x");
//
//        scoreParents.remove(x);

//        if (scoreParents.contains(x)) throw new IllegalArgumentException();
//
        double diff = score1(x, y, scoreParents);
//        double diff = score2(x, y, scoreParents);
//        double diff = score3(x, y, scoreParents);
//        double diff = score4(x, y, scoreParents);
//        double diff = score5(x, y, scoreParents);
//        double diff = score6(x, y, scoreParents);

//        System.out.println("Score diff for " + x + "-->" + y + " given " + scoreParents + " = " + diff);

        return diff;
    }

    private double score1(Node x, Node y, List<Node> scoreParents) {
        double diff;
//        scoreParents.add(x);
//        scoreParents.remove(x);

        if (dag.isDSeparatedFrom(x, y, scoreParents)) {
            diff  = -1;
        } else {
            diff = 1;
        }

//        System.out.println("dsep " + x + "_||_" + y + "|" + scoreParents + " = " + diff);


        return diff;
    }

    private double score2(Node x, Node y, List<Node> scoreParents) {
        double diff = 0;

        if (dag.isDConnectedTo(x, y, scoreParents)) {
            diff += 1;
        }

        List<Node> yUnionScoreParents = new ArrayList<>();
        yUnionScoreParents.add(y);
        yUnionScoreParents.addAll(scoreParents);

        for (Node z : scoreParents) {
            if (
                    dag.isDConnectedTo(x, y, scoreParents) &&
                            dag.isDConnectedTo(z, y, scoreParents) &&
//                            !dag.isDConnectedTo(x, z, scoreParents) &&
                            dag.isDConnectedTo(z, y, scoreParents) &&
                            dag.isDConnectedTo(x, z, yUnionScoreParents)
                    ) {
                diff += 1;
                break;
            }
        }


        return diff;
    }

    private double score3(Node x, Node y, List<Node> scoreParents) {

        List<Node> _scoreParents = new ArrayList<>(scoreParents);
        scoreParents = _scoreParents;

        List<Node> yUnionScoreParents = new ArrayList<>();
        yUnionScoreParents.add(y);
        yUnionScoreParents.addAll(scoreParents);
        int numDependencies = 0;
        int numIndependencies = 0;

        for (Node z : scoreParents) {
            List<Node> sepset = dag.getSepset(x, z);

            System.out.println("sepset for " + x + " and " + z + " is " + sepset + " contains " + y + " = " + (sepset != null ? sepset.contains(y) : ""));


            if (dag.isDConnectedTo(z, y, new ArrayList<>(scoreParents))) {
                numDependencies++;
            } else {
                numIndependencies++;
            }

            if (dag.isDSeparatedFrom(x, z, scoreParents)) {
                numIndependencies++;
            }

            if (dag.isDSeparatedFrom(x, z, Collections.EMPTY_LIST)) {
                numIndependencies++;
            }

            if (dag.isDConnectedTo(x, z, yUnionScoreParents)) {
                numDependencies++;
            } else {
                numIndependencies++;
            }
        }

        boolean xyConnected = dag.isDConnectedTo(x, y, scoreParents);

        if (xyConnected) {
            numDependencies++;
        } else {
            numIndependencies++;


        }

        if (xyConnected) {
            return numDependencies;
        } else {
            return -numIndependencies;
        }
    }

    private double score4(Node x, Node y, List<Node> scoreParents) {
        int count = 0;

        for (Node z : scoreParents) {
            List<Node> sepset = dag.getSepset(x, z);

            if (sepset != null) {
                if (!sepset.contains(y)) {
                    count++;
                } else {
                    count--;
                }
            }
        }

//        scoreParents.add(x);
//        if (dag.isDSeparatedFrom(x, y, scoreParents)) {
        if (dag.isAdjacentTo(x, y)) {
            return 1 + count;
        } else {
            return -1;
        }

//        if (dag.isDSeparatedFrom(x, y, scoreParents)) {
//            return -1 - count;
//        } else {
//            return 1 + count;
//        }
    }
    private double score5(Node x, Node y, List<Node> scoreParents) {
        List<Node> vars = new ArrayList<>(scoreParents);

        double score;

        if (dag.isDSeparatedFrom(x, y, vars)) {
//            score = -1 - scoreParents.size();
            score = -1 - Math.tanh(scoreParents.size()) / 1000.;
        } else {
            score = 1 + Math.tanh(scoreParents.size()) / 1000.;
        }

//        System.out.println( "x = " + x + " y = " + y + " scoreParents = " + scoreParents + " score = " + score);

        return score;
    }

    private double score6(Node x, Node y, List<Node> scoreParents) {
        int count = 0;

        for (Node z : scoreParents) {
            List<Node> sepset = dag.getSepset(x, z);

            if (sepset != null) {
                if (!sepset.contains(y)) {
                    count++;
                } else {
                    count--;
                }
            } else {
                count--;
            }
        }

//        scoreParents.add(x);
        if (!dag.isDSeparatedFrom(x, y, scoreParents)) {
//        if (dag.isAdjacentTo(x, y)) {
            return 1 + count;
        } else {
            return -1 - count;
        }

//        if (dag.isDSeparatedFrom(x, y, scoreParents)) {
//            return -1 - count;
//        } else {
//            return 1 + count;
//        }
    }


    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */

    public double localScore(int i, int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        return localScore(i, new int[]{});
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return true;
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }
}



