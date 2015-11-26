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

package edu.cmu.tetrad.session;

import edu.cmu.tetrad.util.*;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.MarshalledObject;
import java.util.*;

/**
 * <p>Represents a node in a session for a model in a particular class. The sets
 * of possible model classes for this node are given in the constructors of the
 * model classes for the node. Parents (also SessionNodes) may be added to this
 * node provided some combination of the parents' model classes serves a partial
 * argument set to some constructor of the one of this node's model classes. To
 * put it slightly differently, parents can be added to this node one at a time,
 * though at any step along the way it ought to be possible (perhaps by adding
 * more parent nodes) to use the parent models to construct a model of one of
 * the legal types for this node.</p> <p>To retrieve the list of classes for
 * which models may be created, call the <code>getConsistentModelClasses
 * </code>. To construct a model for a particular model choice, call
 * <code>createModel</code> method for the desired class. If the model has a
 * parameterizing object, this object may be passed in using the
 * <code>createParameterizedModel</code> method. For parameterized models, the
 * model object is treated simply as an additional parent to the model and
 * therefore must appear as an argument to one of the constructors of the
 * model.</p> <p>This node keeps track of its parents and its children and keeps
 * these two sets of SessionNodes in sync.</p>
 *
 * @author Joseph Ramsey
 * @see Session
 * @see SessionListener
 * @see SessionAdapter
 * @see SessionEvent
 */
public class SessionNode implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The (optional) name of this session node.
     *
     * @serial Cannot be null.
     */
    private String boxType;

    /**
     * The display name of the session node.
     *
     * @serial Cannot be null.
     */
    private String displayName;

    /**
     * The possible classes this SessionNode can use to construct models.
     *
     * @serial Cannot be null.
     */
    private Class[] modelClasses;

    /**
     * The class of the last model created.
     *
     * @serial Can be null.
     */
    private Class lastModelClass;

    /**
     * When a model is created, we keep a reference to its param types in order
     * to determine, should the need arise, whether one of the objects used to
     * create the model has been destroyed.
     *
     * @serial Can be null.
     */
    private Class[] modelParamTypes;

    /**
     * The model itself. Once this is created, another model cannot be created
     * until this one is explicitly destroyed.
     *
     * @serial Can be null.
     */
    private SessionModel model;

    /**
     * Stores a reference to the previous model so that information
     * from it can be used to initialize a new model.
     *
     * @serial Can be null.
     */
    private SessionModel oldModel;

    /**
     * Stores a clone of the model being edited, in case the user wants to
     * cancel.
     */
    private transient SessionModel savedModel;

    /**
     * A map from model classes to parameter objects.
     *
     * @serial Cannot be null.
     */
    private Map<Class, Params> paramMap = new HashMap<Class, Params>();

    /**
     * The set of parents of this node--a Set of SessionNodes. Must be kept in
     * sync with sets of children in the parent nodes.
     *
     * @serial Cannot be null.
     */
    private Set<SessionNode> parents = new HashSet<SessionNode>();

    /**
     * The set of children of this node--a Set of SessionNodes. Must be kept in
     * sync with sets of parents in the child nodes.
     *
     * @serial Cannot be null.
     */
    private Set<SessionNode> children = new HashSet<SessionNode>();

    /**
     * True iff the next edge should not be added. (Included for GUI user
     * control.) Reset to true every time an edge is added; edge adds must be
     * disallowed individually. To disallow the next edge add, set to false.
     *
     * @serial Any value.
     */
    private boolean nextEdgeAddAllowed = true;

    /**
     * The number of times this session node should be executed (in depth first
     * order) in a simulation study.
     *
     * @serial Range > 0.
     */
    private int repetition = 1;

    /**
     * Support for firing SessionEvent's.
     */
    private transient SessionSupport sessionSupport;

    /**
     * Handles incoming session events, basically by redirecting to any
     * listeners of this session.
     */
    private transient SessionHandler sessionHandler;
    private TetradLoggerConfig loggerConfig = null;

    //==========================CONSTRUCTORS===========================//

    /**
     * Creates a new session node capable of implementing the given model
     * class.
     */
    public SessionNode(Class modelClass) {
        this("???", modelClass.getName(), new Class[]{modelClass});
    }

    /**
     * Creates a new session node with the given name, capable of implementing
     * the given model class.
     *
     * @param boxType     The name of the box type--for instance, "Graph."
     * @param displayName The name of this particular session node. Any non-null
     *                    string.
     * @param modelClass  A single model class associated with this session
     *                    node.
     */
    public SessionNode(String boxType, String displayName, Class modelClass) {
        this(boxType, displayName, new Class[]{modelClass});
    }

    /**
     * Creates a new session node with the given name capable of implementing
     * the given model classes.
     */
    public SessionNode(Class[] modelClasses) {
        this("???", "???", modelClasses);
    }

    /**
     * Creates a new session node with the given name capable of implementing
     * the given model classes. When models are created, they will be of one of
     * these classes. Reflection will be used to create the models by matching
     * the models of the parent Session Nodes to constructor arguments of the
     * class given as argument to the <code>createModel</code> method, which
     * must itself be one of these model classes.
     *
     * @param boxType      The name of the box type--for instance, "Graph."
     * @param displayName  The name of this particular session node. Any
     *                     non-null string.
     * @param modelClasses An array of model classes associated with this
     *                     session node.
     */
    public SessionNode(String boxType, String displayName,
            Class[] modelClasses) {
        setBoxType(boxType);
        setDisplayName(displayName);

        if (modelClasses == null) {
            throw new NullPointerException();
        }

        for (int i = 0; i < modelClasses.length; i++) {
            if (modelClasses[i] == null) {
                throw new NullPointerException(
                        "Model class null: index + " + i);
            }

            if (!(SessionModel.class.isAssignableFrom(modelClasses[i]))) {
                throw new ClassCastException(
                        "Model class must implement SessionModel: " +
                                modelClasses[i]);
            }
        }

        this.boxType = boxType;
        this.displayName = displayName;
        this.modelClasses = modelClasses;
//        ModificationRegistery.registerModel(this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SessionNode serializableInstance() {
        return new SessionNode(Type1.class);
    }

    //==========================PUBLIC METHODS============================//

    /**
     * Adds a parent to this node provided the resulting set of parents taken
     * together provides some combination of possible model classes that can be
     * used as a constructor to some one of the model classes for this node.
     */
    public boolean addParent(SessionNode parent) {
        if (this.parents.contains(parent)) {
            return false;
        }

        if (parent == this) {
            return false;
        }

        // Construct a list of the parents of this node
        // (SessionNode's) together with the new putative parent.
        List<SessionNode> newParents = new ArrayList<SessionNode>(this.parents);
        newParents.add(parent);

        for (Class modelClass : this.modelClasses) {
            // Put all of the model classes of the nodes into a
            // single two-dimensional array. At the same time,
            // construct an int[] array containing the number of
            // model classes for each node. Use this int[] array
            // to construct a generator for all the combinations
            // of model nodes.
            Class[][] parentClasses = new Class[newParents.size()][];
            int[] numModels = new int[newParents.size()];

            for (int j = 0; j < newParents.size(); j++) {
                SessionNode node = newParents.get(j);
                parentClasses[j] = node.getModelClasses();
                numModels[j] = parentClasses[j].length;
            }

            if (isConsistentModelClass(modelClass, parentClasses)) {
                if (this.getModel() == null) {
                    this.parents.add(parent);
                    parent.addChild(this);
                    parent.addSessionListener(getSessionHandler());
                    getSessionSupport().fireParentAdded(parent, this);
                    return true;
                }
                else {
                    this.parents.add(parent);
                    parent.addChild(this);
                    parent.addSessionListener(getSessionHandler());
                    getSessionSupport().fireParentAdded(parent, this);

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Same as addParent except tests if this has already been created. If so
     * the user is asked whether to add parent and update parent's desendents or
     * to cancel the operation.
     */
    public boolean addParent2(SessionNode parent) {
        if (this.parents.contains(parent)) {
            return false;
        }

        if (parent == this) {
            return false;
        }

        // Construct a list of the parents of this node
        // (SessionNode's) together with the new putative parent.
        List<SessionNode> newParents = new ArrayList<SessionNode>(this.parents);
        newParents.add(parent);

        for (Class modelClass : this.modelClasses) {
            // Put all of the model classes of the nodes into a
            // single two-dimensional array. At the same time,
            // construct an int[] array containing the number of
            // model classes for each node. Use this int[] array
            // to construct a generator for all the combinations
            // of model nodes.
            Class[][] parentClasses = new Class[newParents.size()][];
            int[] numModels = new int[newParents.size()];

            for (int j = 0; j < newParents.size(); j++) {
                SessionNode node = newParents.get(j);
                parentClasses[j] = node.getModelClasses();
                numModels[j] = parentClasses[j].length;
            }

            if (isConsistentModelClass(modelClass, parentClasses)) {
                if (this.getModel() == null) {
                    this.parents.add(parent);
                    parent.addChild(this);
                    parent.addSessionListener(getSessionHandler());
                    getSessionSupport().fireParentAdded(parent, this);
                    return true;
                }
                else {

                    // Allows nextEdgeAllowed to be set to false if the next
                    // edge should not be added.
                    sessionSupport.fireAddingEdge();

                    if (isNextEdgeAddAllowed()) {

                        // Must reset nextEdgeAllowed to true.
                        setNextEdgeAddAllowed(true);
                        this.parents.add(parent);
                        parent.addChild(this);
                        parent.addSessionListener(getSessionHandler());
                        getSessionSupport().fireParentAdded(parent, this);

                        // Destroys model & downstream models.
                        destroyModel();
                        return true;
                    }
                    else {
                        return false;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Removes a parent from the node.
     */
    public boolean removeParent(SessionNode parent) {
        if (this.parents.contains(parent)) {
            this.parents.remove(parent);
            parent.removeChild(this);
            parent.removeSessionListener(getSessionHandler());
            getSessionSupport().fireParentRemoved(parent, this);

            return true;
        }

        return false;
    }

    /**
     * @return the set of parents.
     */
    public Set<SessionNode> getParents() {
        return new HashSet<SessionNode>(this.parents);
    }

    /**
     * @return the number of parents.
     */
    public int getNumParents() {
        return parents.size();
    }

    /**
     * Adds a child to the node, provided this node can be added as a parent to
     * the child node.
     */
    public boolean addChild(SessionNode child) {
        if (!this.children.contains(child)) {
            child.addParent(this);

            if (child.containsParent(this)) {
                this.children.add(child);
                return true;
            }
        }

        return false;
    }

    /**
     * @return true iff the given node is child of this node.
     */
    public boolean containsChild(SessionNode child) {
        return this.children.contains(child);
    }

    /**
     * Removes a child from the node.
     */
    public boolean removeChild(SessionNode child) {
        if (this.children.contains(child)) {
            child.removeParent(this);

            if (!child.containsParent(this)) {
                this.children.remove(child);

                return true;
            }
        }

        return false;
    }

    /**
     * @return the set of children.
     */
    public Set<SessionNode> getChildren() {
        return new HashSet<SessionNode>(this.children);
    }

    /**
     * @return the number of children.
     */
    public int getNumChildren() {
        return this.children.size();
    }

    /**
     * Creates a model, provided the class of the model can be uniquely
     * determined without any further hints. If a model was created previously,
     * the previous model class is used. If there is only one consistent model
     * class, than that model class is used. Otherwise, an exception is thrown.
     *
     * @param simulation
     * @return true iff this node contains a model when this method completes.
     * @throws RuntimeException if the model could not be created.
     */
    public boolean createModel(boolean simulation) {
        if (getModel() == null) {
            if (lastModelClass != null) {
                try {
                    createModel(this.lastModelClass, simulation);
                }
                catch (Exception e) {

                    // Allows creation of models downstream to continue
                    // once BayesPM is changed to SemPm... jdramsey 3/30/2005
                    getSessionSupport().fireModelUnclear(this);
                }
            }
            else {
                getSessionSupport().fireModelUnclear(this);
            }
        }

        return getModel() != null;
    }

    /**
     * Creates a model of the given class using models of the parent
     * SessionNodes as constructor arguments. If no appropriate constructor is
     * available, no model is created, and the method returns false. If the
     * attempt to construct a model using reflection fails, the stack trace is
     * printed to System.err and an IllegalArgumentException is thrown. t
     *
     * @throws RuntimeException if the attempt to construct the model throws
     *                          either an IllegalAccessException, an
     *                          InstantiationException, or an InvocationTargetException.
     *                          In this case, a stack trace is printed to
     *                          System.err.
     */
    public void createModel(Class modelClass, boolean simulation)
            throws Exception {
        if (!Arrays.asList(this.modelClasses).contains(modelClass)) {
            throw new IllegalArgumentException("Class not among possible " +
                    "model classes: " + modelClass);
        }

        this.loggerConfig = getLoggerConfig(modelClass);
        TetradLogger.getInstance().setTetradLoggerConfig(loggerConfig);
        TetradLogger.getInstance().log("info", "\n========LOGGING " + getDisplayName()
            + "\n");

        // Collect up the parentModels from the parents. If any model is
        // null, throw an exception.
        List<Object> parentModels = listParentModels();

        // If param not null, add it to the list of parentModels.
        Object param = getParam(modelClass);
        this.model = null;

        List<Object> expandedModels = new ArrayList<Object>(parentModels);

        if (oldModel != null && (!(DoNotAddOldModel.class.isAssignableFrom(modelClass)))) {
            expandedModels.add(oldModel);
        }

        if (param != null) {
            expandedModels.add(param);
        }

        createModelUsingArguments(modelClass, expandedModels);

        if (this.model == null) {
            expandedModels = new ArrayList<Object>(parentModels);

            if (param != null) {
                expandedModels.add(param);
            }

            createModelUsingArguments(modelClass, expandedModels);
        }

//        if (this.model == null) {
//            if (!parentModels.isEmpty()) {
//                Class clazz = parentModels.get(0).getClass();
//
//                Object arr = Array.newInstance(clazz, parentModels.size());
//
//                for (int i = 0; i < parentModels.size(); i++) {
//                    Array.set(arr, i, parentModels.get(i));
//                }
//
//                List<Object> expandedModels = new ArrayList<Object>();
//
//                if (param != null) {
//                    expandedModels.add(param);
//                }
//
//                expandedModels.add(arr);
//
//                createModelUsingArguments(modelClass, expandedModels);
//            }
//        }

        if (this.model == null) {
            createModelUsingArguments(modelClass, parentModels);
        }

        if (this.model == null) {
            TetradLogger.getInstance().log("info", getDisplayName() + " was not created.");
            throw new CouldNotCreateModelException(modelClass);
        }

        // If we're running a simulation, try executing the model.
        if (this.model instanceof Executable) {
            Executable executable = (Executable) this.model;

            try {

                // This executes only in the context of a simulation.
                if (simulation) {
                    executable.execute();
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), e.getMessage());
            }
        }
    }

    public TetradLoggerConfig getLoggerConfig() {
        return this.loggerConfig;
    }

    public TetradLoggerConfig getLoggerConfig(Class modelClass) {
        TetradLoggerConfig oldConfig = this.loggerConfig;
        TetradLoggerConfig newConfig = TetradLogger.getInstance().getLoggerForClass(modelClass);

        if (oldConfig != null && newConfig == null) {
            return oldConfig;
        }

        // Copy event activations over.
        if (oldConfig != null && newConfig != null) {
            for (TetradLoggerConfig.Event event : newConfig.getSupportedEvents()) {
                for (TetradLoggerConfig.Event _event : oldConfig.getSupportedEvents()) {
                    if (event.getId().equals(_event.getId())) {
                        newConfig.setEventActive(_event.getId(), oldConfig.isEventActive(_event.getId()));
                    }
                }
            }
        }

        this.loggerConfig = newConfig;
        return newConfig;
    }

    /**
     * Sets the model to null. This step must be performed before a new model
     * can be created.
     */
    public void destroyModel() {
        if (model != null) {
            this.oldModel = model;
            this.model = null;
        }

        this.modelParamTypes = null;
        getSessionSupport().fireModelDestroyed(this);
    }

    /**
     * Forgets the old model so that it can't be used to recapture parameter
     * values.
     */
    public void forgetOldModel() {
        this.oldModel = null;
    }

    /**
     * @return the class of the model.
     */
    public Class[] getModelClasses() {
        return this.modelClasses;
    }

    /**
     * Sets the model classes to the new array of model classes.
     */
    public final void setModelClasses(Class[] modelClasses) {
        for (int i = 0; i < modelClasses.length; i++) {
            if (modelClasses[i] == null) {
                throw new NullPointerException(
                        "Model class null: index + " + i);
            }
        }

        this.modelClasses = modelClasses;
    }

    /**
     * @return those model classes among the possible model classes that are at
     * least consistent with the model class of the parent session nodes, in the
     * sense that possibly with the addition of more parent session nodes, and
     * assuming that the models of the parent session nodes are non-null, it is
     * possible to construct a model in one of the legal classes for this node
     * using the parent models as arguments to some constructor in that class.
     */
    public Class[] getConsistentModelClasses() {
        List<Class> classes = new ArrayList<Class>();
        List<SessionNode> parents = new ArrayList<SessionNode>(this.parents);
        Class[][] parentModelClasses = new Class[parents.size()][1];

        // Construct the parent model classes; they must all be
        // non-null.
        for (int i = 0; i < parents.size(); i++) {
            SessionNode sessionNode = parents.get(i);
            Object model = sessionNode.getModel();

            if (model != null) {
                parentModelClasses[i][0] = model.getClass();
            }
            else {
                return null;
            }
        }

        // Go through the model classes of this node and see which
        // ones are consistent with the array of parent classes just
        // constructed.
        for (int i = 0; i < this.modelClasses.length; i++) {

            // If this model class is consistent, add it to the list.
            if (isConsistentModelClass(this.modelClasses[i],
                    parentModelClasses)) {
                classes.add(modelClasses[i]);
            }
        }

        return classes.toArray(new Class[0]);
    }

    /**
     * @return the model, or null if no model has been created yet.
     */
    public SessionModel getModel() {
        return this.model;
    }

    /**
     * @return the class of the last model that was created, or null if no model
     * has been created yet.
     */
    public Class getLastModelClass() {
        return this.lastModelClass;
    }

    /**
     * Adds a session listener.
     */
    public void addSessionListener(SessionListener l) {
        getSessionSupport().addSessionListener(l);
    }

    /**
     * Removes a session listener.
     */
    public void removeSessionListener(SessionListener l) {
        getSessionSupport().removeSessionListener(l);
    }

    /**
     * @return true iff this node is in a freshly created state. A node that is
     * in a freshly created state has no model, no parents, no children, and no
     * listeners. It does, however, have the array of possible model classes
     * that it was constructed with, and it may or may not have a name.
     */
    public boolean isFreshlyCreated() {
        return (this.model == null) && (this.modelParamTypes == null) &&
                (this.parents.size() == 0) && (this.children.size() == 0) &&
                (this.sessionHandler == null) && (this.sessionSupport == null);
    }

    /**
     * Resets this sesion node to the state it was in when first constructed.
     * Removes any parents or children, destroys the model if there is one, and
     * resets all listeners. Fires an event for each action taken.
     */
    public void resetToFreshlyCreated() {
        if (!isFreshlyCreated()) {
            Set<SessionNode> _parents = new HashSet<SessionNode>(this.parents);
            Set<SessionNode> _children =
                    new HashSet<SessionNode>(this.children);

            for (SessionNode _parent : _parents) {
                removeParent(_parent);
            }

            for (SessionNode a_children : _children) {
                removeChild(a_children);
            }

            destroyModel();

            this.parents = new HashSet<SessionNode>();
            this.children = new HashSet<SessionNode>();
            this.sessionSupport = null;
            this.sessionHandler = null;
        }
    }

    /**
     * Removes any parents or children of the node that are not in the given
     * list.
     */
    public void restrictConnectionsToList(List sessionNodes) {

        // Remove any parents or children from any node if those parents
        // or children are not in the list.
        for (SessionNode sessionNode : getParents()) {
            if (!sessionNodes.contains(sessionNode)) {
                removeParent(sessionNode);
            }
        }

        for (SessionNode sessionNode1 : getChildren()) {
            if (!sessionNodes.contains(sessionNode1)) {
                removeChild(sessionNode1);
            }
        }
    }

    /**
     * Removes any listeners that are not SessionNodes.
     */
    public void restrictListenersToSessionNodes() {
        this.sessionSupport = null;
        this.sessionHandler = null;
    }

    /**
     * <p>Tests whether two session nodes that are not necessarily object
     * identical are nevertheless identical in structure. This method should not
     * be made to override <code>equals</code> since <code>equals</code> is used
     * in the Collections API to determine, for example, containment in an
     * ArrayList, and the sense of equality needed for that is object identity.
     * Nevertheless, for certain other purposes, such as checking serialization,
     * a looser sense of structural identity is helpful.</p> </p> <p>Two
     * SessionNodes are structurally identical just in case their possible model
     * classes are equal, the parameter type arrays used to construct their
     * models are equal, their models themselves are equal, and the model
     * classes of the parent and child SessionNodes are equal. We dare not check
     * equality of parents and children outright for fear of circularity.</p>
     */
    public boolean isStructurallyIdentical(SessionNode node) {
        if (node == null) {
            return false;
        }

        // Check equality of possible model classes.
        Set<Class> set1 = new HashSet<Class>(Arrays.asList(getModelClasses()));
        Set<Class> set2 =
                new HashSet<Class>(Arrays.asList(node.getModelClasses()));

        if (!set1.equals(set2)) {
            return false;
        }

        // Check equality of model parameter type arrays.
        Class[] arr1 = this.getModelParamTypes();
        Class[] arr2 = node.getModelParamTypes();

        if ((arr1 != null) && (arr2 != null)) {
            if (arr1.length != arr2.length) {
                return false;
            }

            for (int i = 0; i < arr1.length; i++) {
                if (!arr1[i].equals(arr2[i])) {
                    return false;
                }
            }
        }
        else if ((arr1 == null) && (arr2 != null)) {
            return false;
        }
//        else if ((arr1 != null) && (arr2 != null)) {
//            return false;
//        }

        // Check equality of models.
        Object model1 = getModel();
        Object model2 = node.getModel();

        if ((model1 == null) && (model2 != null)) {
            return false;
        }
        else if ((model1 != null) && (model2 == null)) {
            return false;
        }
        else
        if ((model1 != null) /*&& (model2 != null)*/ && !model1.equals(model2))
        {
            return false;
        }

        // Check equality of parent session model classes.
        set1.clear();

        for (SessionNode sessionNode : getParents()) {
            Object model = sessionNode.getModel();

            if (model != null) {
                set1.add(model.getClass());
            }
        }

        set2.clear();

        for (SessionNode sessionNode1 : node.getParents()) {
            Object model = sessionNode1.getModel();

            if (model != null) {
                set2.add(model.getClass());
            }
        }

        if (!set1.equals(set2)) {
            return false;
        }

        // Check equality of child session node model classes.
        set1.clear();

        for (SessionNode sessionNode2 : this.getChildren()) {
            Object model = sessionNode2.getModel();

            if (model != null) {
                set1.add(model.getClass());
            }
        }

        set2.clear();

        for (SessionNode sessionNode3 : node.getChildren()) {
            Object model = sessionNode3.getModel();

            if (model != null) {
                set2.add(model.getClass());
            }
        }

        return set1.equals(set2);
    }

    /**
     * Gets the (optional) name of this node. May be null.
     */
    public String getBoxType() {
        return this.boxType;
    }

    /**
     * Sets the (optional) name for this node. May be null.
     */
    public final void setBoxType(String boxType) {
        if (boxType == null) {
            throw new NullPointerException();
        }

        this.boxType = boxType;
    }

    /**
     * Sets the parameter object for the given model class to the given object.
     */
    public void putParam(Class modelClass, Params param) {
        if (param instanceof SessionListener) {
            SessionListener listener = (SessionListener) param;
            getSessionSupport().addSessionListener(listener);
        }

        paramMap.put(modelClass, param);
    }

    /**
     * Gets the parameter object for the givem model class.
     */
    public Params getParam(Class modelClass) {
        return paramMap.get(modelClass);
    }

    /**
     * Removes the parameter object for the given model class.
     */
    public void removeParam(Class modelClass) {
        Object param = paramMap.get(modelClass);

        if (param != null && param instanceof SessionListener) {
            SessionListener listener = (SessionListener) param;
            getSessionSupport().removeSessionListener(listener);
        }

        paramMap.remove(modelClass);
    }

    public Object[] getModelConstructorArguments(Class modelClass) {
        List<Object> parentModels = getParentModels();
        parentModels.add(getParam(modelClass));
        Constructor[] constructors = modelClass.getConstructors();

        for (Constructor constructor : constructors) {
            Class[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = assignParameters(parameterTypes, parentModels);

            if (arguments != null) {
                return arguments;
            }
        }

        return null;
    }

    /**
     * Prints out the name of the session node.
     */
    public String toString() {
        return this.getBoxType();
    }

    /**
     * True iff the next edge should not be added. (Included for GUI user
     * control.) Reset to true every time an edge is added; edge adds must be
     * disallowed individually. To disallow the next edge add, set to false.
     */
    public void setNextEdgeAddAllowed(boolean nextEdgeAddAllowed) {
        this.nextEdgeAddAllowed = nextEdgeAddAllowed;
    }

    public boolean existsParameterizedConstructor(Class modelClass) {
        Object param = getParam(modelClass);
        List parentModels = listParentModels();
        parentModels.add(param);

        try {
            Constructor[] constructors = modelClass.getConstructors();

            for (Constructor constructor : constructors) {
                Class[] parameterTypes = constructor.getParameterTypes();
                Object[] arguments =
                        assignParameters(parameterTypes, parentModels);

                if (arguments != null) {
                    return true;
                }
            }

            return false;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException("Could not construct model.", e);
        }
    }

    public int getRepetition() {
        return repetition;
    }

    public void setRepetition(int repetition) {
        if (repetition < 1) {
            throw new IllegalArgumentException("Repetition must be >= 1.");
        }

        this.repetition = repetition;
    }

    /**
     * @return true if the cloning operation was successful, false if not. If
     *         the cloning operation was not successful, the model will not have
     *         been altered.
     */
    public boolean useClonedModel() {
        try {
            if (model instanceof Unmarshallable) {
                return false;
            }

            SessionModel temp = model;
            this.model = new MarshalledObject<SessionModel>(model).get();
            this.model.setName(getDisplayName());

            if (this.model instanceof ParamsResettable &&
                    temp instanceof ParamsResettable) {
                Object resettableParams = ((ParamsResettable) temp).getResettableParams();
                ((ParamsResettable) this.model).resetParams(resettableParams);
            }

            this.savedModel = temp;
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    public void forgetSavedModel() {
        this.savedModel = null;
    }

    public void restoreOriginalModel() {
        this.model = savedModel;
        this.model.setName(getDisplayName());
        this.savedModel = null;
    }

    //=====================PACKAGE PROTECTED METHODS=====================//

    //===================================================================//
    // Note: Leave these method package protected for unit testing.      //
    //===================================================================//

    /**
     * Determines whether a given model class is consistent with the models
     * contained in the given List of nodes, in the sense that the model class
     * has a constructor that can take the models of the nodes as arguments.
     */
    public boolean isConsistentModelClass(Class<Type1> modelClass, List nodes) {

        // Put all of the model classes of the nodes into a single
        // two-dimensional array. At the same time, construct an int[]
        // array containing the number of model classes for each
        // node. Use this int[] array to construct a generator for all
        // the combinations of model nodes.
        Class[][] nodeClasses = new Class[nodes.size()][];

        for (int i = 0; i < nodes.size(); i++) {
            SessionNode node = (SessionNode) nodes.get(i);
            nodeClasses[i] = node.getModelClasses();
        }

        return isConsistentModelClass(modelClass, nodeClasses);
    }

    /**
     * <p>Tests whether the model class has an argument that takes all of the
     * given argument classes (or more) as arguments. The purpose of this is to
     * allow parent nodes to be added one at a time to this node, whether or not
     * any of the nodes in question have non-null models.</p>
     */
    public boolean existsConstructor(Class modelClass, Class[] argumentTypes) {
        for (Class argumentType1 : argumentTypes) {
            if (argumentType1 == null) {
                throw new IllegalArgumentException(
                        "Argument classes must be " + "non-null");
            }
        }

        Constructor[] constructors = modelClass.getConstructors();

        loop:
        for (Constructor constructor : constructors) {
            Class[] parameterTypes = constructor.getParameterTypes();
            List<Class> remainingParameterTypes =
                    new ArrayList<Class>(Arrays.asList(parameterTypes));

            loop2:
            for (Class argumentType : argumentTypes) {
                Class type =
                        findMatchingType(remainingParameterTypes, argumentType);

                if (type == null) {
                    continue loop;
                }
                else {
                    remainingParameterTypes.remove(type);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * <p>Returns the first class c in <code>classes</code> that
     * <code>clazz</code> is assignable to.</p>
     */
    public Class getAssignableClass(List classes, Class clazz) {
        for (Object aClass : classes) {
            Class assignableTo = (Class) aClass;
            if (assignableTo.isAssignableFrom(clazz)) {
                return assignableTo;
            }
        }

        return null;
    }

    /**
     * <p>Returns the objects in the List as an array in the same order as the
     * parameter types. If an exact match cannot be found, throws a
     * RuntimeException with an appropriate message. </p>
     *
     * @param parameterTypes a list of classes; if any of them is null, a
     *                       NullPointerException will be thrown.
     * @param objects        a List of objects. (The nulls will be automatically
     *                       thrown out for this one.)
     */
    public Object[] assignParameters(Class[] parameterTypes, List objects)
            throws RuntimeException {

        for (Class parameterType1 : parameterTypes) {
            if (parameterType1 == null) {
                throw new NullPointerException(
                        "Parameter types must all be " + "non-null.");
            }
        }

        // Create a new Object[] array the same size as the
        // parameterTypes array, try to fill it up with objects of the
        // corresponding types from the object List. If at any point
        // an object cannot be found, return null. If there are any
        // objects left over, return null. Otherwise, return the
        // constructed argument array.
        Object[] arguments = new Object[parameterTypes.length];
        List _objects = removeNulls(objects);

        loop:
        for (int i = 0; i < arguments.length; i++) {
            Class parameterType = parameterTypes[i];

            for (int j = 0; j < _objects.size(); j++) {
                Object _object = _objects.get(j);

                if (parameterType.isAssignableFrom(_object.getClass())) {
                    arguments[i] = _object;
                    _objects.remove(_object);
                    continue loop;
                }
            }

            return null;
        }

        return (_objects.size() > 0) ? null : arguments;
    }

    /**
     * @return an array with a combination of particular values for variables
     * given an array indicating the number of values for each variable.
     */
    public int[] getValueCombination(int index, int[] numValues) {

        int[] values = new int[numValues.length];

        for (int i = numValues.length - 1; i >= 0; i--) {
            values[i] = index % numValues[i];
            index /= numValues[i];
        }

        return values;
    }

    /**
     * @return the product of the entries in the given array.
     */
    public int getProduct(int[] arr) {
        int n = 1;

        for (int anArr : arr) {
            n *= anArr;
        }

        return n;
    }

    /**
     * @return the saved session handler if such exists; otherwise, creates one
     * and returns it.
     */
    SessionHandler getSessionHandler() {
        if (this.sessionHandler == null) {
            this.sessionHandler = new SessionHandler();
        }

        return this.sessionHandler;
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * @return true iff the given node is parent of this node.
     */
    private boolean containsParent(SessionNode parent) {
        return this.parents.contains(parent);
    }

    /**
     * @return the parameter types used to construct the model.
     */
    private Class[] getModelParamTypes() {
        return this.modelParamTypes;
    }

    /**
     * True iff the next edge should not be added. (Included for GUI user
     * control.) Reset to true every time an edge is added; edge adds must be
     * disallowed individually. To disallow the next edge add, set to false.
     */
    private boolean isNextEdgeAddAllowed() {
        return nextEdgeAddAllowed;
    }

    private List<Object> getParentModels() {
        List<Object> models = new ArrayList<Object>();

        for (SessionNode node : this.parents) {
            SessionModel model = node.getModel();

            if (model != null) {
                models.add(model);
            }
            else {
                return null;
            }
        }

        return models;
    }

    private List<Object> listParentModels() {
        List<Object> models = new ArrayList<Object>();

        for (SessionNode node : this.parents) {
            Object model = node.getModel();

            if (model == null) {
                throw new RuntimeException(
                        "One of the parents has an empty model.");
            }

            models.add(model);
        }

        return models;
    }

    /**
     * Creates model using the given arguments, if possible. If not possible,
     * the field this.model is unchanged.
     */
    private void createModelUsingArguments(Class modelClass, List models)
            throws Exception {
        if (!(SessionModel.class.isAssignableFrom(modelClass))) {
            throw new ClassCastException(
                    "Model class must implement SessionModel: " + modelClass);
        }

        // Try to find a constructor of the model class that exactly
        // matches the types of these models.
        Constructor[] constructors = modelClass.getConstructors();

        for (Constructor constructor : constructors) {
            Class[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = assignParameters(parameterTypes, models);

            if (arguments != null) {
                try {
                    this.model = (SessionModel) constructor.newInstance(arguments);
                    this.model.setName(getDisplayName());
                }
                catch (InstantiationException e) {
                    throw e;
                }
                catch (IllegalAccessException e) {
                    throw e;
                }
                catch (InvocationTargetException e) {
                    String packagePath = modelClass.getName();
                    int begin = packagePath.lastIndexOf('.') + 1;
                    String name =  packagePath.substring(begin, packagePath.length());

                    if (e.getTargetException() instanceof ThreadDeath) {
                        e.printStackTrace();
                        return;
                    }

                    e.printStackTrace();

                    throw new InvocationTargetException(e,
                            "Could not construct node; root cause: " + e.getCause().getMessage());
                }

                this.modelParamTypes = parameterTypes;
                this.lastModelClass = modelClass;

                getSessionSupport().fireModelCreated(this);
                break;
            }
        }
    }

    /**
     * Determines whether a given model class is consistent with some
     * combination of models parentClasses[i][a[i]], i = 0, 1, ...,
     * parentClasses.length - 1.
     */
    private boolean isConsistentModelClass1(Class modelClass,
            Class[][] parentClasses) {

        // Test whether for some combination of the model classes
        // for the parent nodes there is some model class for this
        // node that can take that can take that combination of
        // parent model classes as arguments to one of its
        // constructors.
        int[] combination;
        Class[] parameterTypes = new Class[parentClasses.length];

        // Calculate the number of models for each parent.
        int[] numModels = new int[parentClasses.length];

        for (int i = 0; i < parentClasses.length; i++) {
            numModels[i] = parentClasses[i].length;
        }

        // Iterate through each combination of parent models.
        for (int i = 0; i < getProduct(numModels); i++) {
            combination = getValueCombination(i, numModels);

            // Read off that combination of model classes from the
            // parent classes array.
            for (int j = 0; j < parentClasses.length; j++) {
                parameterTypes[j] = parentClasses[j][combination[j]];
            }

            if (existsConstructor(modelClass, parameterTypes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * A faster substitute for isConsistentModelClass1.
     * jdramsey 20150801.
     */
    private boolean isConsistentModelClass(Class modelClass,
                                           Class[][] parentClasses) {
        Constructor[] constructors = modelClass.getConstructors();

        for (Constructor constructor : constructors) {
            List<Class> types2 = new ArrayList<>();

            Class[] types = constructor.getParameterTypes();

            TYPES:
            for (int i = 0; i < types.length; i++) {
                for (int j = 0; j < parentClasses.length; j++) {
                    for (int k = 0; k < parentClasses[j].length; k++) {
                            if (types[i].isAssignableFrom(parentClasses[j][k])) {
                            types2.add(parentClasses[j][k]);
                            continue TYPES;
                        }
                    }
                }
            }

            if (types2.size() == parentClasses.length) {
                return true;
            }
        }

        return false;
    }

    private Class findMatchingType(List<Class> parameterTypes,
            Class argumentType) {
        for (Class type : parameterTypes) {
            if (type.isAssignableFrom(argumentType)) {
                return type;
            }
        }

        return null;
    }

    private List removeNulls(List objects) {
        List<Object> _objects = new ArrayList<Object>();

        for (Object o : objects) {
            if (o != null) {
                _objects.add(o);
            }
        }
        return _objects;
    }

    /**
     * Reassesses whether the getModel model is permitted in light of the
     * destruction of one of the parent models.
     */
    private void reassessModel() {
        if (this.modelParamTypes == null) {
            return;
        }

        // Collect up the model types from the parents.
        List<Class<? extends Object>> list1 =
                new ArrayList<Class<? extends Object>>();

        for (SessionNode node : this.parents) {
            Object model = node.getModel();

            if (model != null) {
                list1.add(model.getClass());
            }
        }

        // Make a second list of the stored model param types.
        List<Class> list2 = Arrays.asList(this.modelParamTypes);

        // If the lists aren't equal, destroy the model.
        if (!list1.contains(list2) || !list2.contains(list1)) {
            destroyModel();
        }
    }

    /**
     * @return the saved session support if such exists; otherwise, creates a
     * new session support adding all of the child session nodes of this node as
     * listeners.
     */
    private SessionSupport getSessionSupport() {
        if (this.sessionSupport == null) {
            this.sessionSupport = new SessionSupport(this);

            for (SessionNode child : this.children) {
                this.sessionSupport.addSessionListener(
                        child.getSessionHandler());
            }
        }

        return this.sessionSupport;
    }

    public String getDisplayName() {
        return displayName;
    }

    public final void setDisplayName(String displayName) {
        if (displayName == null) {
            throw new NullPointerException();

        }

        this.displayName = displayName;

        if (getModel() != null) {
            getModel().setName(displayName);
        }
    }

    /**
     * Handles <code>SessionEvent</code>s. Hides the handling of these from the
     * API.
     */
    private class SessionHandler extends SessionAdapter {

        /**
         * When a model is destroyed from a node this is listening to and this
         * destroys one of the arguments used to create the model, then the
         * model of this node has to be destroyed.
         */
        public void modelDestroyed(SessionEvent event) {
            reassessModel();
        }

        /**
         * When a new execution is begun of a simulation study, this event is
         * sent downstream so that certain parameter objects can reset
         * themselves.
         */
        public void executionStarted(SessionEvent event) {

            // Restart the getModel param object if necessary.
            Object model = getModel();

            if (model != null) {
                Object param = getParam(model.getClass());

                if (param instanceof ExecutionRestarter) {
                    ExecutionRestarter restarter = (ExecutionRestarter) param;
                    restarter.newExecution();
                }
            }

            // Pass the message along.
            getSessionSupport().fireSessionEvent(event);
        }
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

        if (boxType == null) {
            throw new NullPointerException();
        }

        if (displayName == null) {
            throw new NullPointerException();
        }

        if (modelClasses == null) {
            throw new NullPointerException();
        }

        if (paramMap == null) {
            throw new NullPointerException();
        }

        if (parents == null) {
            throw new NullPointerException();
        }

        if (children == null) {
            throw new NullPointerException();
        }

        if (repetition < 1) {
            throw new IllegalStateException();
        }
    }
}





