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

package edu.cmu.tetrad.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Calculates a ROC curve and AUC (area under curve) for a list of scored cases
 * whose inclusion in category C is known.
 *
 * @author Joseph Ramsey and Frank Wimberly
 */
public class RocCalculator {
    public static final int ASCENDING = 0;
    private static final int DESCENDING = 1;

    private int direction = ASCENDING;

    private int[][] points;
    private ScoreCategoryPair[] scoreCatPairs;

    /**
     * Constructs a calculator using the parameter information below.
     *
     * @param scores     The scores to be plotted.
     * @param inCategory Whether each score is in the category in question or
     *                   not. Must be an array of the same length as scores.
     * @param direction  Either RocCalculator.ASCENDING or RocCalculator.DESCENDING.
     */
    public RocCalculator(double[] scores, boolean[] inCategory, int direction) {
        if (scores == null) {
            throw new NullPointerException();
        }

        if (inCategory == null) {
            throw new NullPointerException();
        }

        if (scores.length != inCategory.length) {
            throw new IllegalArgumentException("Scores array must have same " +
                    "number of items as inCategory array.");
        }

        if (direction != ASCENDING && direction != DESCENDING) {
            throw new IllegalArgumentException(
                    "Direction must be ASCENDING or " + "DESCENDING.");
        }

        this.direction = direction;

        //Create an array of scores and inCategory pairs for sorting.
        this.scoreCatPairs = new ScoreCategoryPair[scores.length];

        for (int i = 0; i < scores.length; i++) {
            scoreCatPairs[i] = new ScoreCategoryPair(scores[i], inCategory[i]);
        }
    }

    /**
     * Calculates the area under the ROC curve using a very clever Ramsey idea.
     *
     * @return the area under the ROC curve (AUC).
     */
    public double getAuc() {

        if (points == null) {
            getUnscaledRocPlot();
        }

        int lastPoint = points.length - 1;

        int height = 0;
        int area = 0;

        for (int i = 1; i < points.length; i++) {

            if (points[i][1] > points[i - 1][1]) {
                height += 1;
            } else if (points[i][0] > points[i - 1][0]) {
                area += height;
            }
        }

        return ((double) area) / (points[lastPoint][0] * points[lastPoint][1]);
    }

    /**
     * Produces a doubly subscripted array of ints representing the points in
     * the unscaled ROC Curve plot.  The first subscript of the array is the
     * index of the point and the second subscript is 0 for x values and 1 for y
     * values.
     *
     * @return Ibid.
     */
    private void getUnscaledRocPlot() {
        sortCases();

        OrderedPairInt p0 = new OrderedPairInt(0, 0);
        OrderedPairInt pPrime;
        List<OrderedPairInt> plot = new LinkedList<>();
        plot.add(p0);
        int numPairs = scoreCatPairs.length;

        for (int i = numPairs - 1; i >= 0; i--) {
            ScoreCategoryPair tPrime = scoreCatPairs[i];

            if (tPrime.getHasProperty()) {
                pPrime = new OrderedPairInt(p0.getFirst(), p0.getSecond() + 1);
                plot.add(pPrime);
                p0 = pPrime;
            } else {
                pPrime = new OrderedPairInt(p0.getFirst() + 1, p0.getSecond());
                plot.add(pPrime);
                p0 = pPrime;
            }
        }

        this.points = new int[plot.size()][2];

        for (int i = 0; i < plot.size(); i++) {
            OrderedPairInt point = plot.get(i);
            points[i][0] = point.getFirst();
            points[i][1] = point.getSecond();
        }
    }

    public double[][] getScaledRocPlot() {
        if (points == null) {
            getUnscaledRocPlot();
        }

        int numPoints = points.length;
        double[][] pointsDouble = new double[numPoints][2];

        for (int i = 0; i < numPoints; i++) {
            //System.out.println(plot[i][0] + " " + plot[i][1]);
            pointsDouble[i][0] = ((double) points[i][0]) /
                    ((double) points[numPoints - 1][0]);
            pointsDouble[i][1] = ((double) points[i][1]) /
                    ((double) points[numPoints - 1][1]);
        }

        return pointsDouble;
    }

    private void sortCases() {
        //Sort the score-category pairs.  They will be in increasing order by score.
        Arrays.sort(scoreCatPairs);

        //If a higher score implies a lower probability that the case has property P
        //reverse the order.
        if (direction == DESCENDING) {
            int numPairs = scoreCatPairs.length;
            ScoreCategoryPair[] scpRev = new ScoreCategoryPair[numPairs];

            for (int i = 0; i < numPairs; i++) {
                scpRev[i] = scoreCatPairs[numPairs - i - 1];
            }

            System.arraycopy(scpRev, 0, scoreCatPairs, 0, numPairs);
        }
    }

    private static class OrderedPairInt {
        private int first;
        private int second;

        public OrderedPairInt(int first, int second) {
            this.first = first;
            this.second = second;
        }

        public int getFirst() {
            return first;
        }

        public void setFirst(int first) {
            this.first = first;
        }

        public int getSecond() {
            return second;
        }

        public void setSecond(int second) {
            this.second = second;
        }
    }

    private static class ScoreCategoryPair implements Comparable {
        private final double score;
        private final boolean hasProperty;

        public ScoreCategoryPair(double score, boolean hasProperty) {

            this.score = score;
            this.hasProperty = hasProperty;

        }

        public double getScore() {
            return score;
        }

        public boolean getHasProperty() {
            return hasProperty;
        }

        public int compareTo(Object other) {
            if (getScore() < ((ScoreCategoryPair) other).getScore()) {
                return -1;
            } else if (getScore() == ((ScoreCategoryPair) other).getScore()) {
                return 0;
            } else {
                return 1;
            }
        }
    }
}





