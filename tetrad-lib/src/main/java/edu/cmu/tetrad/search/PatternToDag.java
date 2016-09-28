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

import edu.cmu.tetrad.graph.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Given a pattern this class implements two algortithms for finding an associated directed acyclic graph (DAG).
 * <p>
 * The first algorithm (in patternToDagMeek) was described in Zhang and Spirtes (2005), "A Characterization of Markov
 * Equivalence Classes for Ancestral Graphical Models" on pp. 53-54.
 * <p>
 * The second algorithm (in patternToDagDorTarsi) was described by Chickering (2002) in "Optimal Structure
 * Identification with Greedy Search" in the Journal of Machine Learning Research.  The algorithm was proposed by Dor
 * and Tarsi (1992).
 *
 * @author Frank Wimberly
 */
public class PatternToDag {

    /**
     * The input pattern
     */
    private Graph pattern;

    //=============================CONSTRUCTORS==========================//

    public PatternToDag(Graph pattern) {
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Input pattern must not be null");
        }

        this.pattern = pattern;
    }

    /**
     * This algorithm is due to Meek (1995) and was described by Zhang and Spirtes (2005), "A Characterization of Markov
     * Equivalence Classes for Ancestral Graphical Models" on pp. 53-54.
     */
    public Graph patternToDagMeek() {
        return SearchGraphUtils.dagFromPattern(pattern);
    }
}




