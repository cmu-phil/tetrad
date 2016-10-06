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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.ideker;


public class LTestPredictorSearch {
    public static void main(String[] argv) {

        int ngenes = 4;
        //int[][] exp = new int[4][4];
        String[] names = {"a0", "a1", "a2", "a3"};

        int[][] exp = {{1, 1, 1, 0}, {-1, 1, 0, 1}, {1, -1, 0, 0},
                {1, 1, -1, 1}, {1, 1, 1, 2}};

        ItkPredictorSearch ips = new ItkPredictorSearch(ngenes, exp, names);

        for (int gene = 0; gene < ngenes; gene++) {
            ips.predictor(gene);
        }
    }
}






