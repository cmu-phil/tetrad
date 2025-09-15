/// ////////////////////////////////////////////////////////////////////////////
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
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of data models and keeps track of which one is selected.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see DataModel
 */
public final class DataModelList extends AbstractList<DataModel>
        implements DataModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The list of models.
     */
    private List<DataModel> modelList = new LinkedList<>();

    /**
     * The selected model (may be null).
     */
    private DataModel selectedModel;

    /**
     * The name of the DataModelList.
     */
    private String name;

    /**
     * The knowledge for this data.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * <p>Constructor for DataModelList.</p>
     */
    public DataModelList() {
    }

    /**
     * <p>Constructor for DataModelList.</p>
     *
     * @param dataModelList a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public DataModelList(DataModelList dataModelList) {

        try {
            throw new NullPointerException();
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.modelList = new ArrayList<>(dataModelList);
        this.selectedModel = dataModelList.selectedModel;
        this.name = dataModelList.name;
        this.knowledge = dataModelList.knowledge.copy();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.DataModelList} object
     */
    public static DataModelList serializableInstance() {
        return new DataModelList();
    }

    /**
     * {@inheritDoc}
     */
    public DataModel get(int index) {
        return this.modelList.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return this.modelList.size();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        if (getSelectedModel() == null) {
            throw new NullPointerException();
        }
        return getSelectedModel().getVariables();
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * {@inheritDoc}
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return the list of variable names for columns, in order.
     */
    public List<String> getVariableNames() {
        if (getSelectedModel() == null) {
            throw new NullPointerException();
        }
        return getSelectedModel().getVariableNames();
    }

    /**
     * Adds the given DataModel to the list at the given index. Required for AbstractList.
     *
     * @param index   the index at which the DataModel is to be added.
     * @param element the DataModel to be added. (Note that this must be a DataModel.)
     */
    public void add(int index, DataModel element) {
        this.modelList.add(index, element);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Check if the modeList is empty Need to override this since this class is extending AbstractList.
     */
    @Override
    public boolean isEmpty() {
        return this.modelList.isEmpty();
    }

    /**
     * Use this to check if the dataModelList only contains the default empty dataset that is being used to populat the
     * empty spreadsheet - Added by Kevin
     *
     * @return a boolean
     */
    public boolean containsEmptyData() {
        if (this.modelList.isEmpty()) {
            return true;
        } else {
            return this.modelList.getFirst().getVariableNames().isEmpty();
        }
    }

    /**
     * <p>Getter for the field <code>modelList</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<DataModel> getModelList() {
        return this.modelList;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the DataModel at the given index. Required for AbstractList. Required for AbstractList.
     */
    public DataModel remove(int index) {
        DataModel removedObject = this.modelList.remove(index);

        if (removedObject == this.selectedModel) {
            this.selectedModel = null;
        }

        return removedObject;
    }

    /**
     * <p>Getter for the field <code>selectedModel</code>.</p>
     *
     * @return the model that is currently selected. The default is the first model. If there are no models in the list,
     * null is returned.
     */
    public DataModel getSelectedModel() {
        if (this.selectedModel != null) {
            return this.selectedModel;
        } else if (this.modelList.size() > 0) {
            return this.modelList.getFirst();
        } else {
            return null;
        }
    }

    /**
     * <p>Setter for the field <code>selectedModel</code>.</p>
     *
     * @param model a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public void setSelectedModel(DataModel model) {
        if (model == null) {
            throw new NullPointerException();
        }

        if (this.modelList.contains(model)) {
            this.selectedModel = model;
        }
    }

    /**
     * Gets the name of the data model list.
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the data model list..
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the data model list.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Data Model List <");
        for (Object aModelList : this.modelList) {
            buf.append(aModelList).append(", ");
        }
        buf.append(">");
        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDiscrete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMixed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getVariable(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return null;
    }

    /**
     * <p>hashCode.</p>
     *
     * @return a int
     */
    public int hashCode() {
        int hashcode = 17;
        hashcode += 17 * this.name.hashCode();
        return hashcode;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof DataModelList list)) {
            return false;
        }

        return this.name.equals(list.name) && this.modelList.equals(list.modelList) && this.knowledge.equals(list.knowledge) && this.selectedModel.equals(list.selectedModel);

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
