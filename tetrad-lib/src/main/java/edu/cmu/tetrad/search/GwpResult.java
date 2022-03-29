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

/**
 * The purpose of this class is to store evaluation results.
 *
 * @author Gustavo
 */
public class GwpResult {

    public interface PartialEvaluationResult {
        double[] values();
    }

    public static class AdjacencyEvaluationResult implements PartialEvaluationResult {
        public Integer errorsOfOmission;
        public Integer errorsOfCommission;

        public AdjacencyEvaluationResult(final Integer errorsOfOmission, final Integer errorsOfCommission) {
            super();
            this.errorsOfOmission = errorsOfOmission;
            this.errorsOfCommission = errorsOfCommission;
        }

        public double loss() {
            return this.errorsOfOmission + this.errorsOfCommission;
        }

        public double[] values() {
            return new double[]{this.errorsOfOmission, this.errorsOfCommission, loss()};
        }
    }

    public static class OrientationEvaluationResult implements PartialEvaluationResult {
        public Integer nCorrect;
        public Integer directedWrongWay;
        public Integer undirectedWhenShouldBeDirected;
        public Integer directedWhenShouldBeUndirected;


        public OrientationEvaluationResult(final Integer correct, final Integer directedWrongWay,
                                           final Integer undirectedWhenShouldBeDirected, final Integer directedWhenShouldBeUndirected) {
            super();
            this.nCorrect = correct;
            this.directedWrongWay = directedWrongWay;
            this.undirectedWhenShouldBeDirected = undirectedWhenShouldBeDirected;
            this.directedWhenShouldBeUndirected = directedWhenShouldBeUndirected;
        }

        public double[] values() {
            return new double[]{this.nCorrect, this.directedWrongWay, this.undirectedWhenShouldBeDirected, this.directedWhenShouldBeUndirected};
        }

//		public double loss(){
//
//		}

    }

    public static class CoefficientEvaluationResult implements PartialEvaluationResult {
        public Double totalCoeffErrorSq;
        public Integer nEdgesEvaluated;

        public CoefficientEvaluationResult(final Double totalCoeffErrorSq, final Integer edgesEvaluated) {
            super();
            this.totalCoeffErrorSq = totalCoeffErrorSq;
            this.nEdgesEvaluated = edgesEvaluated;
        }

        public double loss() {
            return this.totalCoeffErrorSq;
        }

        public double[] values() {
            return new double[]{this.totalCoeffErrorSq, this.nEdgesEvaluated, loss()};
        }

    }


    public AdjacencyEvaluationResult adj;
    public OrientationEvaluationResult ori;
    public CoefficientEvaluationResult coeffAll;
    public CoefficientEvaluationResult coeffSome;

    public CPDAGEvaluationResult pat;

    public String name = null;

    /**
     * Loss function for PC: * for adjacency errors, 1 pt (i.e. 1 for omission, 1 for commission) for orientation errors: *
     * undirected when it should be directed: 0.5 * directed when it should be undirected: 0.5 * directed the wrong way:
     * 1.0 (in other words, 0.5 for each arrow-head difference, for orientation errors)
     */
    public static class CPDAGEvaluationResult {

        public AdjacencyEvaluationResult adj;
        public OrientationEvaluationResult ori;

        public CPDAGEvaluationResult(final AdjacencyEvaluationResult adj, final OrientationEvaluationResult ori) {
            this.adj = adj;
            this.ori = ori;
        }

        public double loss() {
            final double oriLoss = this.ori.directedWrongWay + 0.5 * this.ori.undirectedWhenShouldBeDirected +
                    0.5 * this.ori.directedWhenShouldBeUndirected;

            final double adjLoss = 1.5 * this.adj.errorsOfOmission + 1.0 * this.adj.errorsOfCommission;

            //			System.out.println("adjLoss = " + adjLoss);
//			System.out.println("oriLoss = " + oriLoss);
            final double loss = adjLoss + oriLoss;
//			System.out.println("returning loss = " + loss);
            return loss;
        }

    }


    /**
     * constructor for evaluations where the method evaluated purports to give us the entire structure.
     */
    public GwpResult(final String methodName, final AdjacencyEvaluationResult adj, final OrientationEvaluationResult ori,
                     final CoefficientEvaluationResult coeffAll, final CoefficientEvaluationResult coeffSome) {
        super();
        this.name = methodName;
        this.adj = adj;
        this.ori = ori;
        this.coeffAll = coeffAll;
        this.coeffSome = coeffSome;

    }

    /**
     * constructor for evaluations where the method evaluated purports to give us the Markov-equivalence class, represented
     * by a CPDAG.
     * <p>
     * * @param methodName
     */
    public GwpResult(final String methodName, final CPDAGEvaluationResult pat) {
        super();
        this.name = methodName;
        this.pat = pat;
    }


}



