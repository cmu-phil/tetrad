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

package edu.cmu.tetrad.sem;


/**
 * Created by IntelliJ IDEA.
 * User: mattheweasterday
 * Date: May 30, 2004
 * Time: 3:00:43 PM
 *
 * This class contains all the constants used in the xml representation of a SEM
 * graph.
 *
 */
public class SemXmlConstants {
    public static final String SEM                          = "sem";

    public static final String SEM_VARIABLES                = "semVariables";
    public static final String CONTINUOUS_VARIABLE          = "continuousVariable";
    public static final String NAME                         = "name";

    public static final String EDGES                        = "edges";
    public static final String EDGE                         = "edge";
    public static final String CAUSE_NODE                   = "causeNode";
    public static final String EFFECT_NODE                  = "effectNode";
    public static final String VALUE                        = "value";
    public static final String FIXED                        = "fixed";

    public static final String MARGINAL_ERROR_DISTRIBUTION  = "marginalErrorDistribution";
    public static final String NORMAL                       = "normal";
    public static final String VARIABLE                     = "variable";
    public static final String MEAN                         = "mean";
    public static final String VARIANCE                     = "variance";
    public static final String IS_LATENT                    = "latent";
    public static final String X                            = "x";
    public static final String Y                            = "y";

    public static final String JOINT_ERROR_DISTRIBUTION     = "jointErrorDistribution";
    public static final String NODE_1                       = "node1";
    public static final String NODE_2                       = "node2";
    public static final String COVARIANCE                   = "covariance";
}



