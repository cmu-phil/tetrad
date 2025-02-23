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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.search.utils.SepsetMap;

import java.io.PrintStream;
import java.util.List;

/**
 * Gives an interface for fast adjacency searches (i.e., PC adjacency searches).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface IFas extends IGraphSearch {

    /**
     * Sets the knowledge for the search.
     *
     * @param knowledge This knowledge.
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * Returns the sepset map discovered during search--that is, the map from node pairs to the sepsets used in the
     * search to remove the corresponding edges from the complete graph.
     *
     * @return This map.
     */
    SepsetMap getSepsets();

    /**
     * Sets the depth of the search--that is, the maximum number of variables conditioned on in the search.
     *
     * @param depth This maximum.
     */
    void setDepth(int depth);

    /**
     * Returns the elapsed time of the search.
     *
     * @return This time.
     */
    long getElapsedTime();

    /**
     * Returns the nodes searched over.
     *
     * @return This list.
     */
    List<Node> getNodes();

    /**
     * Returns the list of ambiguous triples found for a given node.
     *
     * @param node The node
     * @return The list.
     * @see Cpc
     * @see Cfci
     */
    List<Triple> getAmbiguousTriples(Node node);

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True, if so.
     */
    void setVerbose(boolean verbose);

    /**
     * sets the print stream to send text to.
     *
     * @param out This print stream.
     */
    void setOut(PrintStream out);

    /**
     * Sets the start time for the search process.
     *
     * @param startTime The start time in milliseconds.
     */
    void setStartTime(long startTime);

    /**
     * Sets the maximum time in milliseconds that a search process is allowed to run.
     *
     * @param timeout The timeout duration in milliseconds.
     */
    void setTimeout(long timeout);
}



