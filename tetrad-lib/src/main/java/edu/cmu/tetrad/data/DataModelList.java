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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores a list of data models and keeps track of which one is selected.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see DataModel
 */
public final class DataModelList extends AbstractList<DataModel>
        implements DataModel, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The list of models.
     *
     * @serial
     */
    private List<DataModel> modelList = new LinkedList<>();

    /**
     * The selected model (may be null).
     *
     * @serial
     */
    private DataModel selectedModel;

    /**
     * The name of the DataModelList.
     *
     * @serial
     */
    private String name;

    /**
     * The knowledge for this data.
     *
     * @serial
     */
    private IKnowledge knowledge = new Knowledge2();

    //===========================CONSTRUCTORS============================//

    public DataModelList() {
        super();
    }

    public DataModelList(DataModelList dataModelList) {
        super();

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
     */
    public static DataModelList serializableInstance() {
        return new DataModelList();
    }

    //===========================PUBLIC METHODS (see AbstractList too)===//

    /**
     * @return this model, as an Object.
     */
    public DataModel get(int index) {
        return modelList.get(index);
    }

    /**
     * @return the size of the getModel list. Required for AbstractList.
     */
    public int size() {
        return modelList.size();
    }

    public List<Node> getVariables() {
        if (getSelectedModel() == null) throw new NullPointerException();
        return getSelectedModel().getVariables();
    }

    public IKnowledge getKnowledge() {
        return this.knowledge.copy();
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * @return the list of variable names for columns, in order.
     */
    public List<String> getVariableNames() {
        if (getSelectedModel() == null) throw new NullPointerException();
        return getSelectedModel().getVariableNames();
    }

    /**
     * Adds the given DataModel to the list at the given index. Required for
     * AbstractList.
     *
     * @param index   the index at which the DataModel is to be added.
     * @param element the DataModel to be added. (Note that this must be a
     *                DataModel.)
     */
    public void add(int index, DataModel element) {
        modelList.add(index, element);
    }

    /**
     * Removes the DataModel at the given index. Required for AbstractList.
     * Required for AbstractList.
     *
     * @param index the index of the DataModel to remove.
     * @return the DataModel just removed.
     */
    public DataModel remove(int index) {
        DataModel removedObject = this.modelList.remove(index);

        if (removedObject == this.selectedModel) {
            this.selectedModel = null;
        }

        return removedObject;
    }

    /**
     * @return the model that is currently selected. The default is the first
     * model. If there are no models in the list, null is returned.
     */
    public DataModel getSelectedModel() {
        if (this.selectedModel != null) {
            return this.selectedModel;
        } else if (this.modelList.size() > 0) {
            return this.modelList.get(0);
        } else {
            return null;
        }
    }

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
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the data model list..
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return a string representation of the data model list.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Data Model List <");
        for (Object aModelList : modelList) {
            buf.append(aModelList).append(", ");
        }
        buf.append(">");
        return buf.toString();
    }

    public int hashCode() {
        int hashcode = 17;
        hashcode += 17 * name.hashCode();
        return hashcode;
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof DataModelList)) {
            return false;
        }

        DataModelList list = (DataModelList) o;

        return name.equals(list.name) && modelList.equals(list.modelList) && knowledge.equals(list.knowledge) && selectedModel.equals(list.selectedModel);

    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (modelList == null) {
            throw new NullPointerException();
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }
    }
}





