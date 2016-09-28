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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.util.TetradSerializableExcluded;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Represents a forbidden or required edge in a graph of knowledge facts. The
 * edge is always directed, X--&gt;Y, and is either required or forbidden as
 * indicated in the constructor.
 *
 * @author Joseph Ramsey
 */
public class KnowledgeModelEdge extends Edge
        implements TetradSerializableExcluded {
    static final long serialVersionUID = 23L;

    /**
     * Edge types.
     */
    public static final int FORBIDDEN_EXPLICITLY = 0;
    public static final int FORBIDDEN_BY_TIERS = 1;
    public static final int REQUIRED = 2;
    public static final int FORBIDDEN_BY_GROUPS = 3;
    public static final int REQUIRED_BY_GROUPS = 4;

    /**
     * The type of the node, FORBIDDEN or REQUIRED.
     *
     * @serial
     */
    private int type;

    //=============================CONSTRUCTORS============================//

    /**
     * Constructs a new edge by specifying the nodes it connects and the
     * endpoint types.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @param type  one of FORBIDDEN or REQUIRED.           _
     */
    public KnowledgeModelEdge(KnowledgeModelNode node1,
            KnowledgeModelNode node2, int type) {
        super(node1, node2, Endpoint.TAIL, Endpoint.ARROW);

        if (node1 == null || node2 == null) {
            throw new NullPointerException();
        }
        if(this.type != FORBIDDEN_EXPLICITLY && this.type != FORBIDDEN_BY_TIERS
                && this.type != REQUIRED && this.type != FORBIDDEN_BY_GROUPS && this.type != REQUIRED_BY_GROUPS){
            throw new IllegalArgumentException("The given type is not known");
        }

        this.type = type;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static Edge serializableInstance() {
        return new KnowledgeModelEdge(new KnowledgeModelNode("X"),
                new KnowledgeModelNode("Y"), KnowledgeModelEdge.REQUIRED);
    }

    //==============================PUBLIC METHODS========================//

    public int getType() {
        return type;
    }
}





