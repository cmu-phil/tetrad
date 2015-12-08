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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 */
public class RemoveConstantColumnsDataFilter implements DataFilter {


    /**
     * Removes any constant columns from the given dataset.
     * @return - new dataset with constant columns removed.
     */
    public DataSet filter(DataSet dataSet) {
        return DataUtils.removeConstantColumns(dataSet);
    }

    //==================== Private Methods ========================================//


    public static List<Node> getNodes(List<NodeWrapper> wrappers) {
        List<Node> nodes = new ArrayList<Node>(wrappers.size());
        for (NodeWrapper wrapper : wrappers) {
            nodes.add(wrapper.node);
        }
        return nodes;
    }

    //================================ Inner classes ===============================//

    /**
     * Stores a node and the original column, so that the column index doesn't need to be looked
     * up again (which is slow)
     */
    public static class NodeWrapper {
        private int column;
        private Node node;

        public NodeWrapper(int column, Node node) {
            this.column = column;
            this.node = node;
        }

        public int getColumn() {
            return column;
        }
    }


}



