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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.search.score.Score;

import java.util.List;

/**
 * <p>Implements a score to average results over multiple scores. This is
 * used for the IMaGES algorithm. The idea is that one pick and algorithm
 * that takes (only) a score as input, such as FGES or GRaSP or BOSS,
 * and then constructs an ImagesScore (which class) with a list of
 * datasets as input, feeds this ImagesScore to this algorithm through
 * the contructor, and then runs the algorithm to get an estimate
 * of the structure.</p>
 * <p>Importantly, only the variables from the first score will be
 * returned from the getVariables method, so it is up to the user to
 * ensure that all of the scores share the same (object-identical)
 * variables.</p>
 *
 * @author josephramsey
 * @see Fges
 * @see Grasp
 * @see Boss
 */
public class ImagesScore implements Score {

    // The covariance matrix.
    private final List<Score> scores;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    /**
     * Constructs an IMaGES score using the given list of individual scores.
     * These scores will be be averaged to obtain the IMaGES score itself.
     *
     * @param scores The list of scores.
     */
    public ImagesScore(List<Score> scores) {
        if (scores == null) {
            throw new NullPointerException();
        }

        this.scores = scores;

        this.variables = scores.get(0).getVariables();
    }

    /**
     * Returns the average of the individual scores returned from each
     * component score from their localScoreDiff methods. Score differences
     * that are returned as undefined (NaN) are excluded from the
     * average.
     *
     * @return This average score.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        double sum = 0.0;
        int count = 0;

        for (Score score : this.scores) {
            double _score = score.localScoreDiff(x, y, z);

            if (Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    /**
     * Returns the (aggregate) local score for a variable given its parents,
     * which is obtained by averaging the local such scores obtained from each
     * individual score provided in the constructor, excluding scores that are
     * returned as undefined (which are left out of the average).
     *
     * @param i The variable whose score is needed.
     * @return This score.
     */
    public double localScore(int i, int[] parents) {
        double sum = 0.0;
        int count = 0;

        for (Score score : this.scores) {
            double _score = score.localScore(i, parents);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        double score = sum / count;

        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return Double.NaN;
        } else {
            return score;
        }
    }

    public double localScore(int i, int[] parents, int index) {
        return localScoreOneDataSet(i, parents, index);
    }

    /**
     * Returns the (aggregate) local score for a variable given one of its
     * parents, which is obtained by averaging the local such scores obtained
     * from each individual score provided in the constructor, excluding scores
     * that are returned as undefined (which are left out of the average).
     *
     * @param i The variable whose score is needed.
     * @return This score.
     */
    public double localScore(int i, int parent) {
        double sum = 0.0;
        int count = 0;

        for (Score score : this.scores) {
            double _score = score.localScore(i, parent);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    /**
     * Returns the (aggregate) local node score, which is obtained by
     * averaging the local scores obtained from each individual score
     * provided in the constructor, excluding scores that are returned
     * as undefined (which are left out of the average).
     *
     * @param i The variable whose score is needed.
     * @return This score.
     */
    public double localScore(int i) {
        double sum = 0.0;
        int count = 0;

        for (Score score : this.scores) {
            double _score = score.localScore(i);

            if (!Double.isNaN(_score)) {
                sum += _score;
                count++;
            }
        }

        return sum / count;
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return scores.get(0).isEffectEdge(bump);
    }

    /**
     * Returns the variables.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Returns the sample size from the first score.
     *
     * @return This size.
     */
    @Override
    public int getSampleSize() {
        return scores.get(0).getSampleSize();
    }

    /**
     * Returns the max degree from teh first score.
     *
     * @return This maximum.
     */
    @Override
    public int getMaxDegree() {
        return scores.get(0).getMaxDegree();
    }

    /**
     * Returns the 'determines' judgment from the first score.
     *
     * @return This judgment, true if the 'determine' relations holds.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        return scores.get(0).determines(z, y);
    }

    private double localScoreOneDataSet(int i, int[] parents, int index) {
        return this.scores.get(index).localScore(i, parents);
    }
}



