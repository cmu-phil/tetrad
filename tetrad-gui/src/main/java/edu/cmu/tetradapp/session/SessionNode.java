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

package edu.cmu.tetradapp.session;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.NodeVariableType;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.Simulation;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.MarshalledObject;
import java.util.*;

/**
 * <p>
 * Represents a node in a session for a model in a particular class. The sets of possible model classes for this node
 * are given in the constructors of the model classes for the node. Parents (also SessionNodes) may be added to this
 * node provided some combination of the parents' model classes serves a partial argument set to some constructor of the
 * one of this node's model classes. To put it slightly differently, parents can be added to this node one at a time,
 * though at any step along the way it ought to be possible (perhaps by adding more parent nodes) to use the parent
 * models to construct a model of one of the legal types for this node.&gt; 0
 * <p>
 * To retrieve the list of classes for which models may be created, call the <code>getConsistentModelClasses
 * </code>. To construct a model for a particular model choice, call
 * <code>createModel</code> method for the desired class. If the model has a
 * parameterizing object, this object may be passed in using the
 * <code>createParameterizedModel</code> method. For parameterized models, the
 * model object is treated simply as an additional parent to the model and therefore must appear as an argument to one
 * of the constructors of the model.&gt; 0
 * <p>
 * This node keeps track of its parents and its children and keeps these two sets of SessionNodes in sync.&gt; 0
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Session
 * @see SessionListener
 * @see SessionAdapter
 * @see SessionEvent
 */
public class SessionNode implements Node {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * A map from model classes to parameter objects.
     */
    private final Map<Class, Parameters> paramMap = new HashMap<>();

    /**
     * The parameters for this node.
     */
    private final Parameters parameters = new Parameters();

    /**
     * The attributes of this node.
     */
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * The (optional) name of this session node.
     */
    private String boxType;

    /**
     * The display name of the session node.
     */
    private String displayName;

    /**
     * The possible classes this SessionNode can use to construct models.
     */
    private Class[] modelClasses;

    /**
     * The class of the last model created.
     */
    private Class lastModelClass;

    /**
     * When a model is created, we keep a reference to its param types in order to determine, should the need arise,
     * whether one of the objects used to create the model has been destroyed.
     */
    private Class[] modelParamTypes;

    /**
     * The model itself. Once this is created, another model cannot be created until this one is explicitly destroyed.
     */
    private SessionModel model;

    /**
     * Stores a reference to the previous model so that information from it can be used to initialize a new model.
     */
    private SessionModel oldModel;

    /**
     * Stores a clone of the model being edited, in case the user wants to cancel.
     */
    private transient SessionModel savedModel;

    /**
     * The set of parents of this node--a Set of SessionNodes. Must be kept in sync with sets of children in the parent
     * nodes.
     */
    private Set<SessionNode> parents = new HashSet<>();

    /**
     * The set of children of this node--a Set of SessionNodes. Must be kept in sync with sets of parents in the child
     * nodes.
     */
    private Set<SessionNode> children = new HashSet<>();

    /**
     * True iff the next edge should not be added. (Included for GUI user control.) Reset to true every time an edge is
     * added; edge adds must be disallowed individually. To disallow the next edge add, set to false.
     */
    private boolean nextEdgeAddAllowed = true;

    /**
     * The number of times this session node should be executed (in depth first order) in a simulation
     * edu.cmu.tetrad.study.
     */
    private int repetition = 1;

    /**
     * Support for firing SessionEvent's.
     */
    private transient SessionSupport sessionSupport;

    /**
     * Handles incoming session events, basically by redirecting to any listeners of this session.
     */
    private transient SessionHandler sessionHandler;

    /**
     * The logger configuration for this node.
     */
    private TetradLoggerConfig loggerConfig;

    /**
     * Node variable type (domain, interventional status, interventional value..) of this node variable
     */
    private NodeVariableType nodeVariableType = NodeVariableType.DOMAIN;
    private boolean selectionBias;

    //==========================CONSTRUCTORS===========================//

    /**
     * Creates a new session node capable of implementing the given model class.
     *
     * @param modelClass a {@link java.lang.Class} object
     */
    public SessionNode(Class modelClass) {
        this("???", modelClass.getName(), new Class[]{modelClass});
    }

    /**
     * Creates a new session node with the given name, capable of implementing the given model class.
     *
     * @param boxType     The name of the box type--for instance, "Graph."
     * @param displayName The name of this particular session node. Any non-null string.
     * @param modelClass  A single model class associated with this session node.
     */
    public SessionNode(String boxType, String displayName, Class modelClass) {
        this(boxType, displayName, new Class[]{modelClass});
    }

    /**
     * Creates a new session node with the given name capable of implementing the given model classes.
     *
     * @param modelClasses an array of {@link java.lang.Class} objects
     */
    public SessionNode(Class[] modelClasses) {
        this("???", "???", modelClasses);
    }

    /**
     * Creates a new session node with the given name capable of implementing the given model classes. When models are
     * created, they will be of one of these classes. Reflection will be used to create the models by matching the
     * models of the parent Session Nodes to constructor arguments of the class given as argument to the
     * <code>createModel</code> method, which must itself be one of these model classes.
     *
     * @param boxType      The name of the box type--for instance, "Graph."
     * @param displayName  The name of this particular session node. Any non-null string.
     * @param modelClasses An array of model classes associated with this session node.
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
                        "Model class must implement SessionModel: "
                                + modelClasses[i]);
            }
        }

        this.boxType = boxType;
        this.displayName = displayName;
        this.modelClasses = modelClasses;
//        ModificationRegistery.registerModel(this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link SessionNode} object
     */
    public static SessionNode serializableInstance() {
        return new SessionNode(Type1.class);
    }

    //==========================PUBLIC METHODS============================//

    /**
     * <p>isConsistentParent.</p>
     *
     * @param parent a {@link SessionNode} object
     * @return a boolean
     */
    public boolean isConsistentParent(SessionNode parent) {
        return isConsistentParent(parent, null);
    }

    /**
     * <p>isConsistentParent.</p>
     *
     * @param parent        a {@link SessionNode} object
     * @param existingNodes a {@link java.util.List} object
     * @return a boolean
     */
    public boolean isConsistentParent(SessionNode parent, List<SessionNode> existingNodes) {
        if (this.parents.contains(parent)) {
            return false;
        }

        if (parent == this) {
            return false;
        }

        // Construct a list of the parents of this node
        // (SessionNode's) together with the new putative parent.
        List<SessionNode> newParents = new ArrayList<>(this.parents);
        newParents.add(parent);

        Class[] thisClass = new Class[1];

        if (getModel() != null) {
            thisClass[0] = getModel().getClass();
        }

        for (Class modelClass : getModel() != null ? thisClass : this.modelClasses) {
            // Put all of the model classes of the nodes into a
            // single two-dimensional array. At the same time,
            // construct an int[] array containing the number of
            // model classes for each node. Use this int[] array
            // to construct a generator for all the combinations
            // of model nodes.
            Class[][] parentClasses = new Class[newParents.size()][];

            for (int j = 0; j < newParents.size(); j++) {
                SessionNode node = newParents.get(j);
                parentClasses[j] = node.getModelClasses();
            }

            if (isConsistentModelClass(modelClass, parentClasses, false, existingNodes)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Same as addParent except that if a model already exists on this node,
     * the user is first asked (via a fired event) whether to proceed. If
     * allowed, the existing model and all downstream models are destroyed
     * before the parent is added.
     *
     * @param parent a {@link SessionNode} object
     * @return true iff the parent was successfully added.
     */
    public boolean addParent2(SessionNode parent) {
        if (this.parents.contains(parent)) return false;
        if (parent == this) return false;

        List<SessionNode> newParents = new ArrayList<>(this.parents);
        newParents.add(parent);

        for (Class modelClass : this.modelClasses) {
            Class[][] parentClasses = new Class[newParents.size()][];

            for (int j = 0; j < newParents.size(); j++) {
                parentClasses[j] = newParents.get(j).getModelClasses();
            }

            if (isConsistentModelClass(modelClass, parentClasses, false, null)) {
                if (this.getModel() == null) {
                    // No existing model — just add cleanly, same as addParent.
                    this.parents.add(parent);
                    parent.linkChild(this);
                    parent.addSessionListener(getSessionHandler());
                    getSessionSupport().fireParentAdded(parent, this);
                    return true;
                } else {
                    // Existing model — ask listeners whether to proceed.
                    this.sessionSupport.fireAddingEdge();

                    if (isNextEdgeAddAllowed()) {
                        setNextEdgeAddAllowed(true);
                        this.parents.add(parent);
                        parent.linkChild(this);
                        parent.addSessionListener(getSessionHandler());
                        getSessionSupport().fireParentAdded(parent, this);
                        destroyModel(); // destroys this model and downstream models
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }

        return false;
    }

    /**
     * <p>Getter for the field <code>parents</code>.</p>
     *
     * @return the set of parents.
     */
    public Set<SessionNode> getParents() {
        return new HashSet<>(this.parents);
    }

    /**
     * <p>getNumParents.</p>
     *
     * @return the number of parents.
     */
    public int getNumParents() {
        return this.parents.size();
    }

    /**
     * Adds a child to this node by delegating to addParent on the child.
     * All consistency checking, bookkeeping, and event firing is handled
     * there. This method exists purely as a convenience for callers who
     * think in terms of children rather than parents.
     *
     * @param child the node to add as a child.
     * @return true iff the child was successfully added.
     */
    public boolean addChild(SessionNode child) {
        return child.addParent(this);
    }

    /**
     * Adds a parent to this node provided the resulting set of parents
     * taken together provides some combination of possible model classes
     * that can be used as a constructor argument to some one of the model
     * classes for this node.
     *
     * @param parent the node to add as a parent.
     * @return true iff the parent was successfully added.
     */
    public boolean addParent(SessionNode parent) {
        if (this.parents.contains(parent)) return false;
        if (parent == this) return false;

        List<SessionNode> newParents = new ArrayList<>(this.parents);
        newParents.add(parent);

        for (Class modelClass : this.modelClasses) {
            Class[][] parentClasses = new Class[newParents.size()][];

            for (int j = 0; j < newParents.size(); j++) {
                parentClasses[j] = newParents.get(j).getModelClasses();
            }

            if (isConsistentModelClass(modelClass, parentClasses, false, null)) {
                // Update both sides directly without recursing.
                this.parents.add(parent);
                parent.linkChild(this);   // private: just adds to children set
                parent.addSessionListener(getSessionHandler());
                getSessionSupport().fireParentAdded(parent, this);
                return true;
            }
        }

        return false;
    }

    /**
     * Directly registers this node as a parent of {@code child} in the
     * children set, without any consistency checking or event firing.
     * Called only from {@link #addParent} to break the mutual recursion
     * that would otherwise occur between addParent and addChild.
     *
     * @param child the child node to register.
     */
    private void linkChild(SessionNode child) {
        this.children.add(child);
    }

    /**
     * <p>containsChild.</p>
     *
     * @param child a {@link SessionNode} object
     * @return true iff the given node is child of this node.
     */
    public boolean containsChild(SessionNode child) {
        return this.children.contains(child);
    }

    public boolean removeParent(SessionNode parent) {
        if (this.parents.contains(parent)) {
            this.parents.remove(parent);
            parent.unlinkChild(this);   // private: just removes from children set
            parent.removeSessionListener(getSessionHandler());
            getSessionSupport().fireParentRemoved(parent, this);
            return true;
        }
        return false;
    }

    public boolean removeChild(SessionNode child) {
        return child.removeParent(this);
    }

    private void unlinkChild(SessionNode child) {
        this.children.remove(child);
    }

    /**
     * <p>Getter for the field <code>children</code>.</p>
     *
     * @return the set of children.
     */
    public Set<SessionNode> getChildren() {
        return new HashSet<>(this.children);
    }

    /**
     * <p>getNumChildren.</p>
     *
     * @return the number of children.
     */
    public int getNumChildren() {
        return this.children.size();
    }

    /**
     * Creates a model, provided the class of the model can be uniquely determined without any further hints. If a model
     * was created previously, the previous model class is used. If there is only one consistent model class, than that
     * model class is used. Otherwise, an exception is thrown.
     *
     * @param simulation a boolean
     * @return true iff this node contains a model when this method completes.
     * @throws java.lang.RuntimeException if the model could not be created.
     */
    public boolean createModel(boolean simulation) {
        if (getModel() == null) {
            if (this.lastModelClass != null) {
                try {
                    createModel(this.lastModelClass, simulation);
                } catch (Exception e) {

                    // Allows creation of models downstream to continue
                    // once BayesPM is changed to SemPm... jdramsey 3/30/2005
                    getSessionSupport().fireModelUnclear(this);
                }
            } else {
                getSessionSupport().fireModelUnclear(this);
            }
        }

        return getModel() != null;
    }

    /**
     * Creates a model based on the specified model class and simulation flag.
     *
     * @param modelClass the class representing the model to be created
     * @param simulation a flag indicating if the model should be created for simulation
     * @throws Exception if the model class is not among the possible model classes or if the model cannot be created
     */
    public void createModel(Class<?> modelClass, boolean simulation)
            throws Exception {
        if (!Arrays.asList(this.modelClasses).contains(modelClass)) {
            throw new IllegalArgumentException("Class not among possible "
                    + "model classes: " + modelClass);
        }

        this.loggerConfig = getLoggerConfig(modelClass);
        TetradLogger.getInstance().setTetradLoggerConfig(this.loggerConfig);
        String message1 = "\n========LOGGING " + getDisplayName()
                + "\n";
        TetradLogger.getInstance().log(message1);

        // Collect up the parentModels from the parents. If any model is
        // null, throw an exception.
        List<Object> parentModels = listParentModels();

        // If param not null, add it to the list of parentModels.
        Object param = getParam(modelClass);
        this.model = null;

        List<Object> expandedModels = new ArrayList<>(parentModels);

        if (this.oldModel != null && (!(DoNotAddOldModel.class.isAssignableFrom(modelClass)))) {
            expandedModels.add(this.oldModel);
        }

        if (param != null) {
            expandedModels.add(param);
        }

        createModelUsingArguments(modelClass, expandedModels);

        if (this.model == null) {
            expandedModels = new ArrayList<>(parentModels);

            if (param != null) {
                expandedModels.add(param);
            }

            createModelUsingArguments(modelClass, expandedModels);
        }

        if (this.model == null) {
            createModelUsingArguments(modelClass, parentModels);
        }

        if (this.model == null) {
            String message = getDisplayName() + " was not created.";
            TetradLogger.getInstance().log(message);
            throw new CouldNotCreateModelException(modelClass);
        }

        // If we're running a simulation, try executing the model.
        if (this.model instanceof Executable executable) {

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

    /**
     * <p>Getter for the field <code>loggerConfig</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public TetradLoggerConfig getLoggerConfig() {
        return this.loggerConfig;
    }

    /**
     * <p>Getter for the field <code>loggerConfig</code>.</p>
     *
     * @param modelClass a {@link java.lang.Class} object
     * @return a {@link edu.cmu.tetrad.util.TetradLoggerConfig} object
     */
    public TetradLoggerConfig getLoggerConfig(Class<?> modelClass) {
        TetradLoggerConfig oldConfig = this.loggerConfig;
        TetradLoggerConfig newConfig = TetradLogger.getInstance().getLoggerForClass(modelClass);

        if (oldConfig != null && newConfig == null) {
            return oldConfig;
        }

        // Copy event activations over.
        if (oldConfig != null) {
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
     * Sets the model to null. This step must be performed before a new model can be created.
     */
    public void destroyModel() {
        if (this.model != null) {
            this.oldModel = this.model;
            this.model = null;
        }

        this.modelParamTypes = null;
        getSessionSupport().fireModelDestroyed(this);
    }

    /**
     * Forgets the old model so that it can't be used to recapture parameter values.
     */
    public void forgetOldModel() {
        this.oldModel = null;
    }

    /**
     * <p>Getter for the field <code>modelClasses</code>.</p>
     *
     * @return the class of the model.
     */
    public Class[] getModelClasses() {
        return this.modelClasses;
    }

    /**
     * Sets the model classes to the new array of model classes.
     *
     * @param modelClasses an array of {@link java.lang.Class} objects
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
     * Returns those model classes among the possible model classes that are
     * consistent with the current parent models. A model class is consistent
     * if the parent models (in their current state) can be used as arguments
     * to some constructor of that class.
     *
     * <p>If any parent has no model yet, no model class can be consistent,
     * so an empty array is returned rather than null. Callers should treat
     * an empty array as "nothing is constructible right now" and a non-empty
     * array as the set of currently constructible choices.
     *
     * @param exact if true, the parent model types must exactly fill the
     *              constructor; if false, additional parents may be added later.
     * @return a non-null (possibly empty) array of consistent model classes.
     */
    public Class[] getConsistentModelClasses(boolean exact) {
        List<SessionNode> parents = new ArrayList<>(this.parents);
        Class[][] parentModelClasses = new Class[parents.size()][1];

        for (int i = 0; i < parents.size(); i++) {
            Object model = parents.get(i).getModel();

            if (model == null) {
                // A parent with no model means nothing is constructible yet.
                // Return empty rather than null so callers can iterate safely.
                return new Class[0];
            }

            parentModelClasses[i][0] = model.getClass();
        }

        List<Class> consistent = new ArrayList<>();

        for (Class modelClass : this.modelClasses) {
            if (isConsistentModelClass(modelClass, parentModelClasses, exact, null)) {
                consistent.add(modelClass);
            }
        }

        return consistent.toArray(new Class[0]);
    }

    /**
     * <p>Getter for the field <code>model</code>.</p>
     *
     * @return the model, or null if no model has been created yet.
     */
    public SessionModel getModel() {
        return this.model;
    }

    /**
     * <p>Getter for the field <code>lastModelClass</code>.</p>
     *
     * @return the class of the last model that was created, or null if no model has been created yet.
     */
    public Class getLastModelClass() {
        return this.lastModelClass;
    }

    /**
     * Adds a session listener.
     *
     * @param l a {@link SessionListener} object
     */
    public void addSessionListener(SessionListener l) {
        getSessionSupport().addSessionListener(l);
    }

    /**
     * Removes a session listener.
     *
     * @param l a {@link SessionListener} object
     */
    public void removeSessionListener(SessionListener l) {
        getSessionSupport().removeSessionListener(l);
    }

    /**
     * <p>isFreshlyCreated.</p>
     *
     * @return true iff this node is in a freshly created state. A node that is in a freshly created state has no model,
     * no parents, no children, and no listeners. It does, however, have the array of possible model classes that it was
     * constructed with, and it may or may not have a name.
     */
    public boolean isFreshlyCreated() {
        return (this.model == null) && (this.modelParamTypes == null)
                && (this.parents.size() == 0) && (this.children.size() == 0)
                && (this.sessionHandler == null) && (this.sessionSupport == null);
    }

    /**
     * Resets this session node to the state it was in when first constructed.
     * Removes all parents and children (firing events for each), destroys the
     * model if there is one, and clears all listeners.
     */
    public void resetToFreshlyCreated() {
        if (!isFreshlyCreated()) {
            for (SessionNode parent : new HashSet<>(this.parents)) {
                removeParent(parent);
            }

            for (SessionNode child : new HashSet<>(this.children)) {
                removeChild(child);
            }

            destroyModel();

            // By this point parents and children are already empty from the
            // remove calls above; null out the transient listener fields.
            this.sessionSupport = null;
            this.sessionHandler = null;
        }
    }

    /**
     * Removes any parents or children of the node that are not in the given list.
     *
     * @param sessionNodes a {@link java.util.List} object
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
     * <p>
     * Tests whether two session nodes that are not necessarily object identical are nevertheless identical in
     * structure. This method should not be made to override <code>equals</code> since <code>equals</code> is used in
     * the Collections API to determine, for example, containment in an ArrayList, and the sense of equality needed for
     * that is object identity. Nevertheless, for certain other purposes, such as checking serialization, a looser sense
     * of structural identity is helpful.&gt; 0
     * <p>
     * Two SessionNodes are structurally identical just in case their possible model classes are equal, the parameter
     * type arrays used to construct their models are equal, their models themselves are equal, and the model classes of
     * the parent and child SessionNodes are equal. We dare not check equality of parents and children outright for fear
     * of circularity.&gt; 0
     *
     * @param node a {@link SessionNode} object
     * @return a boolean
     */
    public boolean isStructurallyIdentical(SessionNode node) {
        if (node == null) {
            return false;
        }

        // Check equality of possible model classes.
        Set<Class> set1 = new HashSet<>(Arrays.asList(getModelClasses()));
        Set<Class> set2 = new HashSet<>(Arrays.asList(node.getModelClasses()));

        if (!set1.equals(set2)) {
            return false;
        }

        // Check equality of model parameter type arrays.
        if (!modelParamTypesEqual(this.getModelParamTypes(), node.getModelParamTypes())) {
            return false;
        }

        // Check equality of models.
        Object model1 = getModel();
        Object model2 = node.getModel();

        if ((model1 == null) && (model2 != null)) {
            return false;
        } else if ((model1 != null) && (model2 == null)) {
            return false;
        } else if ((model1 != null) /*&& (model2 != null)*/ && !model1.equals(model2)) {
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
     * Compares the model parameter type arrays of this node and the given
     * node for structural equality. Handles all four null combinations
     * explicitly so no case falls through incorrectly.
     *
     * <p>Both null     → equal (neither node has been used to construct a model yet)
     * <p>One null      → not equal (asymmetric construction state)
     * <p>Both non-null → element-wise comparison after length check
     */
    private static boolean modelParamTypesEqual(Class[] arr1, Class[] arr2) {
        if (arr1 == null && arr2 == null) return true;
        if (arr1 == null || arr2 == null) return false;  // exactly one is null
        if (arr1.length != arr2.length)   return false;

        for (int i = 0; i < arr1.length; i++) {
            if (!arr1[i].equals(arr2[i])) return false;
        }

        return true;
    }

    /**
     * Gets the (optional) name of this node. May be null.
     *
     * @return a {@link java.lang.String} object
     */
    public String getBoxType() {
        return this.boxType;
    }

    /**
     * Sets the (optional) name for this node. May be null.
     *
     * @param boxType a {@link java.lang.String} object
     */
    public final void setBoxType(String boxType) {
        if (boxType == null) {
            throw new NullPointerException();
        }

        this.boxType = boxType;
    }

    /**
     * Sets the parameter object for the given model class to the given object.
     *
     * @param modelClass a {@link java.lang.Class} object
     * @param param      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void putParam(Class modelClass, Parameters param) {
        if (param instanceof SessionListener listener) {
            getSessionSupport().addSessionListener(listener);
        }

        this.paramMap.put(modelClass, param);
    }

    /**
     * Gets the parameter object for the givem model class.
     *
     * @param modelClass a {@link java.lang.Class} object
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParam(Class modelClass) {
        return this.paramMap.get(modelClass);
    }

    /**
     * Removes the parameter object for the given model class.
     *
     * @param modelClass a {@link java.lang.Class} object
     */
    public void removeParam(Class modelClass) {
        Object param = this.paramMap.get(modelClass);

        if (param != null && param instanceof SessionListener listener) {
            getSessionSupport().removeSessionListener(listener);
        }

        this.paramMap.remove(modelClass);
    }

    /**
     * Returns the constructor arguments that would be used to create a model
     * of the given class, or null if no matching constructor exists. Uses
     * listParentModels() rather than getParentModels() so
     * that parents with null models are silently skipped rather than causing
     * a NullPointerException.
     *
     * @param modelClass the model class to find constructor arguments for.
     * @return a matching argument array, or null if none exists.
     */
    public Object[] getModelConstructorArguments(Class modelClass) {
        List<Object> parentModels = listParentModels(); // never returns null
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
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setName(String name) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeType getNodeType() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodeType(NodeType nodeType) {

    }

    @Override
    public boolean getSelectionBias() {
        return selectionBias;
    }

    @Override
    public void setSelectionBias(boolean selectionBias) {
        this.selectionBias = selectionBias;
    }

    /**
     * Prints out the name of the session node.
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.getBoxType();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCenterX() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCenterX(int centerX) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCenterY() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCenterY(int centerY) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCenter(int centerX, int centerY) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node like(String name) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(Node node) {
        return 0;
    }

    /**
     * <p>existsParameterizedConstructor.</p>
     *
     * @param modelClass a {@link java.lang.Class} object
     * @return a boolean
     */
    public boolean existsParameterizedConstructor(Class modelClass) {
        if (modelClass == null) {

            // If the model class is null, then there is no constructor, so display a dialog to the users by
            // return false here.
            return false;
        }

        Object param = getParam(modelClass);
        List parentModels = listParentModels();
        parentModels.add(param);

        try {
            Constructor[] constructors = modelClass.getConstructors();

            for (Constructor constructor : constructors) {
                Class[] parameterTypes = constructor.getParameterTypes();
                Object[] arguments = assignParameters(parameterTypes, parentModels);

                if (arguments != null) {
                    return true;
                }
            }

            return false;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not construct model.", e);
        }
    }

    /**
     * <p>Getter for the field <code>repetition</code>.</p>
     *
     * @return a int
     */
    public int getRepetition() {
        return this.repetition;
    }

    /**
     * <p>Setter for the field <code>repetition</code>.</p>
     *
     * @param repetition a int
     */
    public void setRepetition(int repetition) {
        if (repetition < 1) {
            throw new IllegalArgumentException("Repetition must be >= 1.");
        }

        this.repetition = repetition;
    }

    /**
     * <p>useClonedModel.</p>
     *
     * @return true if the cloning operation was successful, false if not. If the cloning operation was not successful,
     * the model will not have been altered.
     */
    public boolean useClonedModel() {

        // turn off model canceling to allow data to be recreated from seeds.
        if (true) {
            return false;
        }

        try {
            if (this.model instanceof Unmarshallable) {
                return false;
            }

            SessionModel temp = this.model;
            this.model = new MarshalledObject<>(this.model).get();
            this.model.setName(getDisplayName());

            if (this.model instanceof ParamsResettable
                    && temp instanceof ParamsResettable) {
                Object resettableParams = ((ParamsResettable) temp).getResettableParams();
                ((ParamsResettable) this.model).resetParams(resettableParams);
            }

            this.savedModel = temp;
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    /**
     * <p>forgetSavedModel.</p>
     */
    public void forgetSavedModel() {
        this.savedModel = null;
    }

    /**
     * Restores the model to the saved clone created by {@link #useClonedModel()}.
     * Does nothing if no saved model exists, rather than throwing NPE.
     *
     * @throws IllegalStateException if called when no saved model is available.
     */
    public void restoreOriginalModel() {
        if (this.savedModel == null) {
            throw new IllegalStateException(
                    "restoreOriginalModel() called but no saved model exists. " +
                            "useClonedModel() must succeed before restoreOriginalModel() is called.");
        }

        this.model = this.savedModel;
        this.model.setName(getDisplayName());
        this.savedModel = null;
    }
    /**
     * Determines whether a given model class is consistent with the models contained in the given List of nodes, in the
     * sense that the model class has a constructor that can take the models of the nodes as arguments.
     *
     * @param modelClass a {@link java.lang.Class} object
     * @param nodes      a {@link java.util.List} object
     * @param exact      a boolean
     * @return a boolean
     */
    public boolean isConsistentModelClass(Class<Type1> modelClass, List nodes, boolean exact) {

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

        return isConsistentModelClass(modelClass, nodeClasses, exact, null);
    }

    //=====================PACKAGE PROTECTED METHODS=====================//
    //===================================================================//
    // Note: Leave these method package protected for unit testing.      //
    //===================================================================//

    /**
     * <p>
     * Tests whether the model class has an argument that takes all of the given argument classes (or more) as
     * arguments. The purpose of this is to allow parent nodes to be added one at a time to this node, whether or not
     * any of the nodes in question have non-null models.&gt; 0
     *
     * @param modelClass    a {@link java.lang.Class} object
     * @param argumentTypes an array of {@link java.lang.Class} objects
     * @return a boolean
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
            List<Class> remainingParameterTypes
                    = new ArrayList<>(Arrays.asList(parameterTypes));

            for (Class argumentType : argumentTypes) {
                Class type = findMatchingType(remainingParameterTypes, argumentType);

                if (type == null) {
                    continue loop;
                } else {
                    remainingParameterTypes.remove(type);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * <p>
     * Returns the first class c in <code>classes</code> that <code>clazz</code> is assignable to.&gt; 0
     *
     * @param classes a {@link java.util.List} object
     * @param clazz   a {@link java.lang.Class} object
     * @return a {@link java.lang.Class} object
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
     * Returns the objects in the List as an array in the same order as the
     * parameter types, or null if no valid assignment exists. Uses backtracking
     * with early pruning rather than exhaustive permutation enumeration, so it
     * is safe for large parent counts.
     *
     * @param parameterTypes a list of classes; nulls throw NullPointerException.
     * @param objects        a List of objects (nulls are removed automatically).
     * @return an argument array matching the parameter types, or null if none exists.
     */
    public Object[] assignParameters(Class[] parameterTypes, List objects)
            throws RuntimeException {

        for (Class parameterType : parameterTypes) {
            if (parameterType == null) {
                throw new NullPointerException("Parameter types must all be non-null.");
            }
        }

        List<Object> candidates = removeNulls(objects);

        if (parameterTypes.length != candidates.size()) {
            return null;
        }

        Object[] arguments = new Object[parameterTypes.length];
        boolean[] used     = new boolean[candidates.size()];

        if (matchByBacktrack(parameterTypes, candidates, arguments, used, 0)) {
            return arguments;
        }

        return null;
    }

    /**
     * Recursive backtracking helper. Assigns candidates to parameterTypes slots
     * one at a time, pruning branches as soon as a slot cannot be filled.
     * Respects thread interruption so it cannot hang indefinitely.
     */
    private boolean matchByBacktrack(Class[] parameterTypes,
                                     List<Object> candidates,
                                     Object[] arguments,
                                     boolean[] used,
                                     int slot) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        if (slot == parameterTypes.length) {
            return true; // all slots filled
        }

        Class<?> needed = parameterTypes[slot];

        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;

            if (needed.isAssignableFrom(candidates.get(i).getClass())) {
                arguments[slot] = candidates.get(i);
                used[i] = true;

                if (matchByBacktrack(parameterTypes, candidates, arguments, used, slot + 1)) {
                    return true;
                }

                // backtrack
                arguments[slot] = null;
                used[i] = false;
            }
        }

        return false; // no valid assignment found for this slot
    }

    /**
     * <p>assignClasses.</p>
     *
     * @param constructorTypes an array of {@link java.lang.Class} objects
     * @param modelTypes       an array of {@link java.lang.Class} objects
     * @param exact            a boolean
     * @param existingNodes    a {@link java.util.List} object
     * @return a boolean
     * @throws java.lang.RuntimeException if any.
     */
    public boolean assignClasses(Class[] constructorTypes, Class[] modelTypes, boolean exact, List<SessionNode> existingNodes)
            throws RuntimeException {
        for (Class parameterType1 : constructorTypes) {
            if (parameterType1 == null) {
                throw new NullPointerException(
                        "Parameter types must all be non-null.");
            }
        }

        // Is it the case that for this constructor, every argument type is a model class for
        // one of the existing session nodes? (You can skip Parameters classes.)
        if (existingNodes != null) {
            existingNodes.remove(this);

            for (Class<?> type : constructorTypes) {
                if (type.equals(Parameters.class)) {
                    continue;
                }
                boolean foundNode = false;

                FOR:
                for (SessionNode node : existingNodes) {
                    for (Class<?> clazz : node.getModelClasses()) {
                        if (clazz.equals(type)) {
                            foundNode = true;
                            break FOR;
                        }
                    }
                }

                if (!foundNode) {
                    return false;
                }
            }
        }

        if (exact) {
            if (modelTypes.length != constructorTypes.length) {
                return false;
            }
        } else {
            if (modelTypes.length > constructorTypes.length) {
                return false;
            }
        }

        if (numWithoutParams(modelTypes) == 0 && numWithoutParams(constructorTypes) > 0) {
            return false;
        }

        PermutationGenerator gen0 = new PermutationGenerator(constructorTypes.length);
        int[] paramPerm;

        while ((paramPerm = gen0.next()) != null) {
            PermutationGenerator gen = new PermutationGenerator(modelTypes.length);
            int[] modelPerm;

            while ((modelPerm = gen.next()) != null) {
                boolean allAssigned = true;

                for (int i = 0; i < modelPerm.length; i++) {
                    Class<?> constructorType = constructorTypes[paramPerm[i]];
                    Class<?> modelType = modelTypes[modelPerm[i]];

                    if (!constructorType.isAssignableFrom(modelType)) {
                        allAssigned = false;
                    }
                }

                if (allAssigned) {
                    return true;
                }
            }
        }

        return false;
    }

    private int numWithoutParams(Class[] modelTypes) {
        int n = 0;

        for (Class clazz : modelTypes) {
            if (clazz != Parameters.class) {
                n++;
            }
        }

        return n;
    }

    /**
     * <p>getValueCombination.</p>
     *
     * @param index     a int
     * @param numValues an array of  objects
     * @return an array with a combination of particular values for variables given an array indicating the number of
     * values for each variable.
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
     * <p>getProduct.</p>
     *
     * @param arr an array of  objects
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
     * @return the saved session handler if such exists; otherwise, creates one and returns it.
     */
    SessionHandler getSessionHandler() {
        if (this.sessionHandler == null) {
            this.sessionHandler = new SessionHandler();
        }

        return this.sessionHandler;
    }

    /**
     * @return true iff the given node is parent of this node.
     */
    private boolean containsParent(SessionNode parent) {
        return this.parents.contains(parent);
    }

    //==============================PRIVATE METHODS=======================//

    /**
     * @return the parameter types used to construct the model.
     */
    private Class[] getModelParamTypes() {
        return this.modelParamTypes;
    }

    /**
     * True iff the next edge should not be added. (Included for GUI user control.) Reset to true every time an edge is
     * added; edge adds must be disallowed individually. To disallow the next edge add, set to false.
     */
    private boolean isNextEdgeAddAllowed() {
        return this.nextEdgeAddAllowed;
    }

    /**
     * True iff the next edge should not be added. (Included for GUI user control.) Reset to true every time an edge is
     * added; edge adds must be disallowed individually. To disallow the next edge add, set to false.
     *
     * @param nextEdgeAddAllowed a boolean
     */
    public void setNextEdgeAddAllowed(boolean nextEdgeAddAllowed) {
        this.nextEdgeAddAllowed = nextEdgeAddAllowed;
    }

    private List<Object> listParentModels() {
        List<Object> models = new ArrayList<>();

        for (SessionNode node : this.parents) {
            Object model = node.getModel();

            if (model != null) {
                models.add(model);
            }
        }

        return models;
    }

    /**
     * Creates model using the given arguments, if possible. If not possible, the field this.model is unchanged.
     */
    private void createModelUsingArguments(Class modelClass, List<Object> models)
            throws Exception {
        if (!(SessionModel.class.isAssignableFrom(modelClass))) {
            throw new ClassCastException(
                    "Model class must implement SessionModel: " + modelClass);
        }

        // If the model class is a Simulation and there is exactly one parent
        // that is not itself a Simulation, use that parent's parameters instead
        // of this node's parameters.
        if (Simulation.class.isAssignableFrom(modelClass)) {
            Parameters parentParams = null;
            int nonSimulationParentCount = 0;

            for (SessionNode parent : this.parents) {
                Object parentModel = parent.getModel();
                if (parentModel != null && !(parentModel instanceof Simulation)) {
                    nonSimulationParentCount++;
                    // Get the parameters the parent is actually using for its current model.
                    if (parent.getLastModelClass() != null) {
                        Parameters p = parent.getParam(parent.getLastModelClass());
                        if (p != null) {
                            parentParams = p;
                        }
                    }
                }
            }

            if (nonSimulationParentCount == 1 && parentParams != null) {
                // Replace any existing Parameters in models with the parent's parameters.
                models.removeIf(o -> o instanceof Parameters);
                models.add(parentParams);
            }
        }

        // Try to find a constructor of the model class that exactly
        // matches the types of these models.
        Constructor[] constructors = modelClass.getConstructors();

        for (Constructor constructor : constructors) {
            Class[] constructorTypes = constructor.getParameterTypes();
            Object[] arguments = null;

            if (constructorTypes.length == 2 && constructorTypes[0].isArray()
                    && constructorTypes[1] == Parameters.class) {
                List<Object> _objects = new ArrayList<>();
                Class<?> c1 = constructorTypes[0].getComponentType();
                Parameters parameters = null;

                for (Object value : models) {
                    Class<?> c2 = value.getClass();

                    if ((c1.isAssignableFrom(c2))) {
                        _objects.add(value);
                    }

                    if (c2 == Parameters.class) {
                        parameters = (Parameters) value;
                    }
                }

                if (_objects.isEmpty()) {
                    return;
                }

                if (parameters != null) {
                    Object o = Array.newInstance(c1, _objects.size());

                    for (int i = 0; i < _objects.size(); i++) {
                        Array.set(o, i, _objects.get(i));
                    }

                    arguments = new Object[]{o, parameters};
                } else {
                    Object o = Array.newInstance(c1, _objects.size());
                    for (int i = 0; i < _objects.size(); i++) {
                        Array.set(o, i, _objects.get(i));
                    }

                    arguments = new Object[]{o};
                }
            }

            if (constructorTypes.length == 0) {
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), "UI models shouldn't have blank constructors. " +
                        "This one did: " + modelClass.getName());
                continue;
            }

            if (arguments == null) {
                arguments = assignParameters(constructorTypes, models);
            }

            if (arguments != null) {
                try {
                    this.model = (SessionModel) constructor.newInstance(arguments);
                    this.model.setName(getDisplayName());
                } catch (InstantiationException | IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                } catch (InvocationTargetException e) {
                    String packagePath = modelClass.getName();
                    int begin = packagePath.lastIndexOf('.') + 1;
                    String name = packagePath.substring(begin);

                    e.printStackTrace();

                    if (e.getCause().getMessage() != null && !e.getCause().getMessage().isEmpty()) {
                        throw new InvocationTargetException(e,
                                e.getCause().getMessage()
                        );

                    } else {
                        throw new InvocationTargetException(e,
                                "Could not construct node; root cause: " + e.getCause().getMessage()
                                        + " " + packagePath + " " + begin + " " + name
                        );
                    }
                }

                this.modelParamTypes = constructorTypes;
                this.lastModelClass = modelClass;

                getSessionSupport().fireModelCreated(this);
                break;
            }
        }
    }

    /**
     * New version 2015901.
     */
    private boolean isConsistentModelClass(Class modelClass, Class[][] parentClasses, boolean exact,
                                           List<SessionNode> existingNodes) {
        Constructor[] constructors = modelClass.getConstructors();

        // If the constructor takes the special form of an array followed by Parameters,
        // public Clazz(C1[] c1, Parameters paramters);
        // just check to make sure all models besides Parameters are of class C1.
        L:
        for (Constructor constructor : constructors) {
            Class<?>[] constructorTypes = constructor.getParameterTypes();

            boolean hasParameters = false;

            for (Class<?> type : constructorTypes) {
                if (type == Parameters.class) {
                    hasParameters = true;
                    break;
                }
            }

            if (constructorTypes.length == 2) {
                if (constructorTypes[0].isArray() && constructorTypes[1] == Parameters.class) {
                    if (this.parents != null && this.parents.size() == 0) {
                        return false;
                    }

                    for (Class[] parentClass : parentClasses) {
                        boolean found = false;

                        for (int j = 0; j < parentClass.length; j++) {
                            Class<?> c1 = constructorTypes[0].getComponentType();
                            Class<?> c2 = parentClass[j];

                            if (c2 == Parameters.class || c1.isAssignableFrom(c2)) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            return false;
                        }
                    }

                    return true;
                }
            }

            List<List<Class>> summary = new ArrayList<>();

            for (int i = 0; i < parentClasses.length; i++) {
                summary.add(new ArrayList<>());
            }

            for (int i = 0; i < parentClasses.length; i++) {
                for (int j = 0; j < parentClasses[i].length; j++) {
                    for (Class<?> constructorType : constructorTypes) {
                        if (constructorType.isAssignableFrom(parentClasses[i][j])) {
                            if (!summary.get(i).contains(constructorType)) {
                                summary.get(i).add(constructorType);
                            }
                        }
                    }
                }
            }

            int[] dims = new int[parentClasses.length];

            for (int i = 0; i < parentClasses.length; i++) {
                dims[i] = summary.get(i).size();
                if (dims[i] == 0) {
                    continue L;
                }
            }

            CombinationIterator iterator = new CombinationIterator(dims);

            while (iterator.hasNext()) {
                if (hasParameters) {
                    int[] comb = iterator.next();

                    Class[] modelTypes = new Class[comb.length + 1];

                    for (int i = 0; i < comb.length; i++) {
                        modelTypes[i] = summary.get(i).get(comb[i]);
                    }

                    modelTypes[comb.length] = Parameters.class;

                    if (assignClasses(constructorTypes, modelTypes, exact, existingNodes)) {
                        return true;
                    }
                } else {
                    int[] comb = iterator.next();

                    Class[] modelTypes = new Class[comb.length];

                    for (int i = 0; i < comb.length; i++) {
                        modelTypes[i] = summary.get(i).get(comb[i]);
                    }

                    if (assignClasses(constructorTypes, modelTypes, exact, existingNodes)) {
                        return true;
                    }
                }
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

    private List<Object> removeNulls(List objects) {
        List<Object> _objects = new ArrayList<>();

        for (Object o : objects) {
            if (o != null) {
                _objects.add(o);
            }
        }
        return _objects;
    }

    /**
     * Reassesses whether the current model is still valid given the current
     * parent models. Destroys the model if the multiset of current parent model
     * types no longer matches the multiset of types that were used to construct
     * it. Uses a frequency-count comparison so that duplicate types are handled
     * correctly (e.g. two DataModel parents are distinct from one).
     */
    private void reassessModel() {
        if (this.modelParamTypes == null) {
            return;
        }

        for (Class clazz : this.modelParamTypes) {
            if (clazz == null) {
                return;
            }
        }

        // Collect the types of models currently present in parent nodes,
        // excluding Parameters (which is not a parent model, it's a config object).
        List<Class<?>> currentTypes = new ArrayList<>();
        for (SessionNode node : this.parents) {
            Object model = node.getModel();
            if (model != null) {
                currentTypes.add(model.getClass());
            }
        }

        // Collect the non-Parameters types that were used to construct the model.
        List<Class<?>> constructedTypes = new ArrayList<>();
        for (Class clazz : this.modelParamTypes) {
            if (clazz != Parameters.class) {
                constructedTypes.add(clazz);
            }
        }

        // Compare as multisets via frequency maps. If they differ, the model
        // was built from a parent configuration that no longer exists.
        if (!sameMultiset(currentTypes, constructedTypes)) {
            destroyModel();
        }
    }

    /**
     * Returns true iff two lists contain the same elements with the same
     * frequencies, regardless of order.
     */
    private static boolean sameMultiset(List<Class<?>> a, List<Class<?>> b) {
        if (a.size() != b.size()) return false;

        Map<Class<?>, Integer> freq = new HashMap<>();
        for (Class<?> c : a) freq.merge(c, 1, Integer::sum);
        for (Class<?> c : b) {
            int count = freq.getOrDefault(c, 0);
            if (count == 0) return false;
            freq.put(c, count - 1);
        }
        return true;
    }

    /**
     * @return the saved session support if such exists; otherwise, creates a new session support adding all of the
     * child session nodes of this node as listeners.
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

    /**
     * <p>Getter for the field <code>displayName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * <p>Setter for the field <code>displayName</code>.</p>
     *
     * @param displayName a {@link java.lang.String} object
     */
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
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParameters() {
        return this.parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeVariableType getNodeVariableType() {
        return this.nodeVariableType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodeVariableType(NodeVariableType nodeVariableType) {
        this.nodeVariableType = nodeVariableType;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * Handles <code>SessionEvent</code>s. Hides the handling of these from the API.
     */
    private class SessionHandler extends SessionAdapter {

        /**
         * When a model is destroyed from a node this is listening to and this destroys one of the arguments used to
         * create the model, then the model of this node has to be destroyed.
         */
        public void modelDestroyed(SessionEvent event) {
            reassessModel();
        }

        /**
         * When a new execution is begun of a simulation edu.cmu.tetrad.study, this event is sent downstream so that
         * certain parameter objects can reset themselves.
         */
        public void executionStarted(SessionEvent event) {

            // Restart the getModel param object if necessary.
            Object model = getModel();

            for (Class clazz : SessionNode.this.modelClasses) {
                Object param = getParam(clazz);

                if (param instanceof ExecutionRestarter restarter) {
                    restarter.newExecution();
                }
            }

            // Pass the message along.
            getSessionSupport().fireSessionEvent(event);
        }
    }

}

