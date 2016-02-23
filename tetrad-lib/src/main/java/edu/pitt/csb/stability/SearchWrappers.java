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

package edu.pitt.csb.stability;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.IndTestMultinomialLogisticRegression;
import edu.cmu.tetrad.search.PcStable;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.csb.mgm.MixedUtils;

/**
 * Created by ajsedgewick on 9/4/15.
 */
public class SearchWrappers {
    public static class PcStableWrapper extends DataGraphSearch {
        //should be one param for the alpha level of the independance test
        public PcStableWrapper(double... params) {
            super(params);
        }

        public PcStableWrapper copy(){return new PcStableWrapper(searchParams);}

        public Graph search(DataSet ds) {
            IndTestMultinomialLogisticRegression indTest = new IndTestMultinomialLogisticRegression(ds, searchParams[0]);
            PcStable pcs = new PcStable(indTest);
            return pcs.search();
        }
    }

    public static class MGMWrapper extends DataGraphSearch {
        //should be array three parameters for lambdas of each edge type
        public MGMWrapper(double... params) {
            super(params);
        }

        public MGMWrapper copy() {return new MGMWrapper(searchParams);};

        public Graph search(DataSet ds) {
            MGM m = new MGM(ds, searchParams);
            return m.search();
        }
    }

    public static class FgsWrapper extends DataGraphSearch{
        public FgsWrapper(double...params){
            super(params);
        }

        public FgsWrapper copy() {return new FgsWrapper(searchParams);}

        public Graph search(DataSet ds){
            Fgs fg = new Fgs(MixedUtils.makeContinuousData(ds));
            fg.setPenaltyDiscount(searchParams[0]);
            return fg.search();
        }
    }


}

