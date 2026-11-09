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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The <code>DiscretizationWrapper</code> class provides functionality for discretizing a
 * <code>DataModel</code> within a <code>DataWrapper</code> object. This class serves as a
 * specialized wrapper that processes tabular datasets by converting continuous variables to
 * discrete variables based on a specified set of discretization specifications.
 * Discretization is performed to transform data into a format suitable for some algorithms
 * that require discrete inputs.
 * <p>
 * This class extends {@link DataWrapper} and ensures compatibility between the parent
 * and child node data models for seamless re-discretization.
 */
public class DiscretizationWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the <code>DiscretizationWrapper</code> by discretizing the selected
     * <code>DataModel</code>.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DiscretizationWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

        if (!getDataModelList().isEmpty() && data.getDataModelList().size() != getDataModelList().size()) {
            throw new IllegalArgumentException("The number of data models in the parent node must match " +
                    "the number of data models in the child node.");
        }

        if (!getDataModelList().isEmpty()) {
            for (int i = 0; i < data.getDataModelList().size(); i++) {
                List<Node> variables1 = getDataModelList().get(i).getVariables();
                List<Node> variables2 = data.getDataModelList().get(i).getVariables();

                for (int j = 0; j < variables1.size(); j++) {
                    if (variables1.get(j) instanceof DiscreteVariable) {
                        if (!variables1.get(j).getName().equals(variables2.get(j).getName())) {
                            throw new IllegalArgumentException("Discrete variables in the parent node "
                                    + "must have the same categories to re-discretize automatically.");
                        }
                    }
                }
            }
        }

        // Build a name-keyed lookup from the specs so that node object identity
        // does not matter. The simulator may reorder or recreate columns, so the
        // Node references in the specs map may not match the Node references in
        // datasets other than the one that was selected when the editor ran.
        @SuppressWarnings("unchecked")
        Map<Node, DiscretizationSpec> discretizationSpecs = (Map<Node, DiscretizationSpec>)
                params.get("discretizationSpecs", new HashMap<Node, DiscretizationSpec>());

        if (discretizationSpecs.isEmpty()) {
            throw new IllegalArgumentException("No discretization specifications have been provided.");
        }

        Map<String, DiscretizationSpec> specsByName = new HashMap<>();
        for (Map.Entry<Node, DiscretizationSpec> entry : discretizationSpecs.entrySet()) {
            specsByName.put(entry.getKey().getName(), entry.getValue());
        }

        DataModelList dataSets = data.getDataModelList();
        DataModelList discretizedDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet originalData)) {
                throw new IllegalArgumentException("Only tabular data sets can be discretized.");
            }

            // Build a per-dataset specs map keyed by the actual Node objects in
            // this dataset, matched by name to the editor-produced specs.
            Map<Node, DiscretizationSpec> datasetSpecs = new HashMap<>();
            for (Node node : originalData.getVariables()) {
                DiscretizationSpec spec = specsByName.get(node.getName());
                if (spec != null) {
                    datasetSpecs.put(node, spec);
                }
            }

            if (datasetSpecs.isEmpty()) {
                throw new IllegalArgumentException("No discretization specifications matched any "
                        + "variables in one of the datasets. Check that variable names are consistent.");
            }

            // Record which variable names carried NodeType.SELECTION before
            // discretization, so we can restore that marking afterwards.
            // The Discretizer always produces new Node objects (typically
            // DiscreteVariable) whose NodeType defaults to MEASURED, so the
            // SELECTION marking would otherwise be silently lost.
            Map<String, NodeType> selectionNames = new HashMap<>();
            for (Node node : originalData.getVariables()) {
                if (node.getNodeType() == NodeType.SELECTION) {
                    selectionNames.put(node.getName(), NodeType.SELECTION);
                }
            }

            Discretizer discretizer = new Discretizer(originalData, datasetSpecs);
            discretizer.setVariablesCopied(Preferences.userRoot().getBoolean("copyUnselectedColumns", true));

            DataSet discretized = discretizer.discretize();

            // Restore NodeType.SELECTION on any output variable whose source
            // variable had that type.
            if (!selectionNames.isEmpty()) {
                for (Node node : discretized.getVariables()) {
                    if (selectionNames.containsKey(node.getName())) {
                        node.setNodeType(NodeType.SELECTION);
                    }
                }
            }

            discretizedDataSets.add(discretized);
        }

        boolean anyDiscretized = false;

        if (!discretizedDataSets.isEmpty()) {
            for (DataModel discretizedDataSet : discretizedDataSets) {
                List<Node> variables = discretizedDataSet.getVariables();

                for (Node variable : variables) {
                    if (variable instanceof DiscreteVariable) {
                        anyDiscretized = true;
                        break;
                    }
                }
            }
        }

        if (!anyDiscretized) {
            throw new IllegalArgumentException("No discretization has been done.");
        }

        setDataModel(discretizedDataSets);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Discretization of data in the parent node.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}