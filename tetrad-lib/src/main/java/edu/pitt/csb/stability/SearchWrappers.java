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

package edu.pitt.csb.stability;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.work_in_progress.IndTestMultinomialLogisticRegression;
import edu.pitt.csb.mgm.Mgm;
import edu.pitt.csb.mgm.MixedUtils;

/**
 * Created by ajsedgewick on 9/4/15.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SearchWrappers {

    /**
     * Abstract class for search algorithm wrappers.
     */
    private SearchWrappers() {
    }

    /**
     * Abstract class for search algorithm wrappers.
     */
    public static class PcStableWrapper extends DataGraphSearch {
        /**
         * Constructor.
         *
         * @param params Parameters. Should be one param for the alpha level of the independence test.
         */
        public PcStableWrapper(double... params) {
            super(params);
        }

        /**
         * Copy constructor.
         */
        public PcStableWrapper copy() {
            return new PcStableWrapper(this.searchParams);
        }

        /**
         * Search method.
         */
        public Graph search(DataSet ds) {
            IndTestMultinomialLogisticRegression indTest = new IndTestMultinomialLogisticRegression(ds, this.searchParams[0]);
            Pc pcs = new Pc(indTest);
            pcs.setStable(true);
            try {
                return pcs.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Abstract class for search algorithm wrappers.
     */
    public static class MGMWrapper extends DataGraphSearch {
        /**
         * should be array three parameters for lambdas of each edge type
         *
         * @param params parameters
         */
        public MGMWrapper(double... params) {
            super(params);
        }

        /**
         * Copy constructor.
         */
        public MGMWrapper copy() {
            return new MGMWrapper(this.searchParams);
        }

        /**
         * Search method.
         */
        public Graph search(DataSet ds) {
            Mgm m = new Mgm(ds, this.searchParams);
            return m.search();
        }
    }

    /**
     * Wrapper for the Fges search algorithm.
     */
    public static class FgesWrapper extends DataGraphSearch {

        /**
         * Constructor.
         *
         * @param params parameters
         */
        public FgesWrapper(double... params) {
            super(params);
        }

        /**
         * Copy constructor.
         *
         * @return a copy of the wrapper
         */
        public FgesWrapper copy() {
            return new FgesWrapper(this.searchParams);
        }

        /**
         * Search method.
         *
         * @param ds data set
         * @return a graph
         */
        public Graph search(DataSet ds) throws InterruptedException {
            SemBicScore score = new SemBicScore(new CovarianceMatrix(MixedUtils.makeContinuousData(ds)));
            score.setPenaltyDiscount(this.searchParams[0]);
            Fges fg = new Fges(score);
            return fg.search();
        }
    }
}

