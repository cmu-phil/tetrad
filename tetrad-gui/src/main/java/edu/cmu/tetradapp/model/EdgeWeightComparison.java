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
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 *
 * @author Michael Freenor
 */
public class EdgeWeightComparison implements SessionModel {
    static final long serialVersionUID = 23L;

    private String name;
    private SemIm reference;
    private SemIm target;

    public EdgeWeightComparison(SemEstimatorWrapper reference, SemEstimatorWrapper target)
    {
        this.reference = reference.getEstimatedSemIm();
        this.target = target.getEstimatedSemIm();
    }

    public EdgeWeightComparison(SemImWrapper reference, SemEstimatorWrapper target)
    {
        this.reference = reference.getSemIm();
        this.target = target.getEstimatedSemIm();
    }

    public EdgeWeightComparison(SemImWrapper reference, SemImWrapper target)
    {
        this.reference = reference.getSemIm();
        this.target = target.getSemIm();
    }

    public String getDisplayString()
    {
        String displayString = "";

        SemIm ref = reference;
        TetradMatrix referenceMatrix = ref.getEdgeCoef();
        TetradMatrix targetMatrix = target.getEdgeCoef();

        if(targetMatrix.columns() != referenceMatrix.columns() || targetMatrix.rows() != referenceMatrix.rows())
            return "The SEM IM's you selected don't have the same number of variables!  No comparison is possible here.";

        double score = 0;
        for(int i = 0; i < ref.getEdgeCoef().rows(); i++)
        {
            for(int j = 0; j < ref.getEdgeCoef().columns(); j++)
            {
                score += (targetMatrix.get(i, j) - referenceMatrix.get(i, j))
                            * (targetMatrix.get(i, j) - referenceMatrix.get(i, j));    
            }
        }
        displayString += "Scheines Score: " + score + "\n\n";
        displayString += "(Calculated by summing the squared differences\n of each corresponding edge weight.)";
        return displayString;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static EdgeWeightComparison serializableInstance() {
        return new EdgeWeightComparison(SemImWrapper.serializableInstance(),
                SemImWrapper.serializableInstance());
    }
}



