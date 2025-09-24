///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataFilter;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Tyler was lazy and didn't document this....
 *
 * @author Tyler Gibson
 */
class RemoveConstantColumnsDataFilter implements DataFilter {


    /**
     * <p>getNodes.</p>
     *
     * @param wrappers a {@link java.util.List} object
     * @return a {@link java.util.List} object
     */
    public static List<Node> getNodes(List<NodeWrapper> wrappers) {
        List<Node> nodes = new ArrayList<>(wrappers.size());
        for (NodeWrapper wrapper : wrappers) {
            nodes.add(wrapper.node);
        }
        return nodes;
    }

    //==================== Private Methods ========================================//

    /**
     * {@inheritDoc}
     * <p>
     * Removes any constant columns from the given dataset.
     */
    public DataSet filter(DataSet dataSet) {
        return DataTransforms.removeConstantColumns(dataSet);
    }

    //================================ Inner classes ===============================//

    /**
     * Stores a node and the original column, so that the column index doesn't need to be looked up again (which is
     * slow)
     */
    public static class NodeWrapper {
        private final int column;
        private final Node node;

        public NodeWrapper(int column, Node node) {
            this.column = column;
            this.node = node;
        }

        public int getColumn() {
            return this.column;
        }
    }


}




