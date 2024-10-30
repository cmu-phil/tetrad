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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Interface for a score suitable for FGES
 */
public interface ISScore {
    double localScore(int node, int[] parents_is, int[] parents_pop, int[] children_population);

    double localScoreDiff(int x, int y, int[] parentsY_is, int[] parentsY_pop, int[] childrenY_pop);

    List<Node> getVariables();

    boolean isEffectEdge(double bump);

    int getSampleSize();

    Node getVariable(String targetName);

    int getMaxDegree();

    boolean determines(List<Node> z, Node y);

    // same as localSCire but this one doesn't use structure prior
    double localScore1(int node, int[] parents_is, int[] parents_pop, int[] children_pop);

    double getStructurePrior();

    double getSamplePrior();

    void setStructurePrior(double structurePrior);

    void setSamplePrior(double samplePrior);


    DataSet getDataSet();
}

