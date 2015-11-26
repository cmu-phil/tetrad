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

import org.apache.commons.collections4.map.MultiKeyMap;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores a map from (variable, parents) to score.
 *
 * @author Joseph Ramsey
 */
public class LocalScoreCache {
    private MultiKeyMap map;

    public LocalScoreCache() {
        map = new MultiKeyMap();
    }

    public void add(int variable, int[] parents, double score) {
        Set<Integer> _parents = new HashSet<Integer>(parents.length);

        for (int parent : parents) {
            _parents.add(parent);
        }

        map.put(variable, _parents, score);
    }

    public double get(int variable, int[] parents) {
        Set<Integer> _parents = new HashSet<Integer>(parents.length);

        for (int parent : parents) {
            _parents.add(parent);
        }

        Double _score = (Double) map.get(variable, _parents);
        return _score == null ? Double.NaN : (_score);
    }

    public void clear() {
        map.clear();
    }
}



