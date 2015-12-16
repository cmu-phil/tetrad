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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Created by josephramsey on 1/3/14.
 */
public class SemEstimatorParams implements Params {
    static final long serialVersionUID = 23L;

    private String semOptimizerType;
    private SemIm.ScoreType scoreType = SemIm.ScoreType.Fgls;
    private int numRestarts = 1;

    /**
     * Constructs a new parameters object. Must be a blank constructor.
     */
    public SemEstimatorParams() {
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SemEstimatorParams serializableInstance() {
        return new SemEstimatorParams();
    }

    public String getSemOptimizerType() {
        return this.semOptimizerType;
    }

    public void setSemOptimizerType(String semOptimizerType) {
        this.semOptimizerType = semOptimizerType;
    }

    public void setScoreType(SemIm.ScoreType scoreType) {
        this.scoreType = scoreType;
    }

    public SemIm.ScoreType getScoreType() {
        return scoreType;
    }

    public void setNumRestarts(int numRestarts) {
        if (numRestarts < 1) return;
        this.numRestarts = numRestarts;
    }

    public int getNumRestarts() {
        if (numRestarts < 1) numRestarts = 1;
        return numRestarts;
    }
}



