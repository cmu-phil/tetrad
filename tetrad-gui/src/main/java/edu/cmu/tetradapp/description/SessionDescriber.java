/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.description;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Version;
import edu.cmu.tetradapp.app.SessionNodeConfig;
import edu.cmu.tetradapp.app.SessionNodeModelConfig;
import edu.cmu.tetradapp.app.TetradApplicationConfig;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.KnowledgeBoxModel;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.TimeLagGraphWrapper;
import edu.cmu.tetradapp.session.SessionNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes a {@link SessionDescription} of a session: its boxes, their connections and locations, and the choices and
 * non-default parameter settings made in each box. Data sets and results are not described.
 * <p>
 * Every box gets its type, model, parents and location. What else is said about a box depends on its model. A
 * {@link ModelDescriber} registered for the model's class, or for the nearest superclass that has one, describes
 * the model; a model with no describer of its own gets the general one, which lists the parameters of the box whose
 * values differ from their defaults. So every box is described to some degree, and more can be said about a kind of
 * model by registering a describer for it.
 * <p>
 * Describing a session reads it and changes nothing in it.
 *
 * @author josephramsey
 * @author Claude
 */
public final class SessionDescriber {

    /**
     * The describers, by model class.
     */
    private final Map<Class<?>, ModelDescriber> describers = new LinkedHashMap<>();

    /**
     * Constructs a describer with the standard model describers registered.
     */
    public SessionDescriber() {
        register(GeneralAlgorithmRunner.class, StandardDescribers::describeSearch);
        register(DataWrapper.class, StandardDescribers::describeData);
        register(KnowledgeBoxModel.class, StandardDescribers::describeKnowledge);
        register(NoteModel.class, StandardDescribers::describeNote);
        register(GraphWrapper.class, (model, node, out)
                -> StandardDescribers.describeGraph(((GraphWrapper) model).getGraph(), model, node, out));
        register(DagWrapper.class, (model, node, out)
                -> StandardDescribers.describeGraph(((DagWrapper) model).getGraph(), model, node, out));
        register(SemGraphWrapper.class, (model, node, out)
                -> StandardDescribers.describeGraph(((SemGraphWrapper) model).getGraph(), model, node, out));
        register(TimeLagGraphWrapper.class, (model, node, out)
                -> StandardDescribers.describeGraph(((TimeLagGraphWrapper) model).getGraph(), model, node, out));
    }

    /**
     * Registers a describer for models of the given class and of its subclasses that have no describer of their
     * own.
     *
     * @param modelClass the model class.
     * @param describer  the describer.
     */
    public void register(Class<?> modelClass, ModelDescriber describer) {
        this.describers.put(modelClass, describer);
    }

    /**
     * Describes the given session, dating the description today.
     *
     * @param sessionWrapper the session, as the session workbench holds it.
     * @return the description.
     */
    public SessionDescription describe(SessionWrapper sessionWrapper) {
        return describe(sessionWrapper, LocalDate.now());
    }

    /**
     * Describes the given session.
     *
     * @param sessionWrapper the session, as the session workbench holds it.
     * @param written        the date to give the description.
     * @return the description.
     */
    public SessionDescription describe(SessionWrapper sessionWrapper, LocalDate written) {
        List<BoxDescription.Field> header = new ArrayList<>();
        header.add(new BoxDescription.Field(SessionDescription.TETRAD_VERSION,
                String.valueOf(Version.currentViewableVersion())));
        header.add(new BoxDescription.Field(SessionDescription.SESSION, sessionWrapper.getName()));
        header.add(new BoxDescription.Field(SessionDescription.WRITTEN, String.valueOf(written)));

        List<SessionNodeWrapper> ordered = inReadingOrder(sessionWrapper);
        Map<SessionNode, Integer> position = new HashMap<>();

        for (int i = 0; i < ordered.size(); i++) {
            position.put(ordered.get(i).getSessionNode(), i);
        }

        List<BoxDescription> boxes = new ArrayList<>();

        for (SessionNodeWrapper wrapper : ordered) {
            boxes.add(describeBox(wrapper, position));
        }

        return new SessionDescription(header, boxes);
    }

    private BoxDescription describeBox(SessionNodeWrapper wrapper, Map<SessionNode, Integer> position) {
        SessionNode node = wrapper.getSessionNode();
        BoxDescription.Builder out = new BoxDescription.Builder(node.getDisplayName());
        Object model = node.getModel();

        String boxType = node.getBoxType() != null ? node.getBoxType() : wrapper.getButtonType();

        if (boxType != null) {
            out.field(BoxDescription.TYPE, boxType);
        }

        String modelName = modelName(boxType, model);

        if (modelName != null) {
            out.field(BoxDescription.MODEL, modelName);
        }

        // Parents in the same order as the boxes are listed.
        List<SessionNode> parents = new ArrayList<>(node.getParents());
        parents.sort(Comparator.comparingInt((SessionNode p) -> position.getOrDefault(p, Integer.MAX_VALUE))
                .thenComparing(SessionNode::getDisplayName));

        if (!parents.isEmpty()) {
            List<String> names = new ArrayList<>();

            for (SessionNode parent : parents) {
                names.add(parent.getDisplayName());
            }

            out.field(BoxDescription.PARENTS, String.join(", ", names));
        }

        out.field(BoxDescription.LOCATION, wrapper.getCenterX() + ", " + wrapper.getCenterY());

        if (model != null) {
            describerFor(model.getClass()).describe(model, node, out);
        }

        return out.build();
    }

    /**
     * Returns the describer registered for the given class or, failing that, for its nearest superclass that has
     * one or, failing that, the general describer.
     */
    private ModelDescriber describerFor(Class<?> modelClass) {
        for (Class<?> c = modelClass; c != null; c = c.getSuperclass()) {
            ModelDescriber describer = this.describers.get(c);

            if (describer != null) {
                return describer;
            }
        }

        return StandardDescribers::describeGeneral;
    }

    /**
     * Returns what to write for the model of a box: "none yet" for an empty box, nothing for a box type that
     * offers only one model (where naming it would say nothing), and otherwise the model's name as the model
     * chooser shows it. If two models of the box type share that name, the class name is added in parentheses.
     */
    private static String modelName(String boxType, Object model) {
        if (model == null) {
            return "none yet";
        }

        Class<?> modelClass = model.getClass();
        TetradApplicationConfig config = TetradApplicationConfig.getInstance();
        SessionNodeConfig nodeConfig = boxType == null ? null : config.getSessionNodeConfig(boxType);

        if (nodeConfig == null || nodeConfig.getModelConfig(modelClass) == null) {
            nodeConfig = config.getSessionNodeConfig(modelClass);
        }

        SessionNodeModelConfig modelConfig = nodeConfig == null ? null : nodeConfig.getModelConfig(modelClass);

        if (modelConfig == null || modelConfig.name() == null) {
            return modelClass.getSimpleName();
        }

        Class<?>[] models = nodeConfig.getModels();

        if (models.length == 1) {
            return null;
        }

        int sameName = 0;

        for (Class<?> other : models) {
            SessionNodeModelConfig otherConfig = nodeConfig.getModelConfig(other);

            if (otherConfig != null && modelConfig.name().equals(otherConfig.name())) {
                sameName++;
            }
        }

        return sameName > 1 ? modelConfig.name() + " (" + modelClass.getSimpleName() + ")" : modelConfig.name();
    }

    /**
     * Returns the boxes of the session in an order fit for reading: every box after its parents and, where that
     * leaves a choice, top to bottom, then left to right, then by name.
     */
    private static List<SessionNodeWrapper> inReadingOrder(SessionWrapper sessionWrapper) {
        List<SessionNodeWrapper> remaining = new ArrayList<>();

        for (Node node : sessionWrapper.getNodes()) {
            if (node instanceof SessionNodeWrapper wrapper) {
                remaining.add(wrapper);
            }
        }

        remaining.sort(Comparator.comparingInt(SessionNodeWrapper::getCenterY)
                .thenComparingInt(SessionNodeWrapper::getCenterX)
                .thenComparing(w -> w.getSessionNode().getDisplayName()));

        List<SessionNodeWrapper> ordered = new ArrayList<>();
        List<SessionNode> placed = new ArrayList<>();

        while (!remaining.isEmpty()) {
            SessionNodeWrapper next = null;

            for (SessionNodeWrapper candidate : remaining) {
                boolean parentsPlaced = true;

                for (SessionNode parent : candidate.getSessionNode().getParents()) {
                    if (!placed.contains(parent) && inSession(remaining, parent)) {
                        parentsPlaced = false;
                        break;
                    }
                }

                if (parentsPlaced) {
                    next = candidate;
                    break;
                }
            }

            // A cycle should not occur in a session; if one does, take the boxes as sorted.
            if (next == null) {
                next = remaining.get(0);
            }

            remaining.remove(next);
            ordered.add(next);
            placed.add(next.getSessionNode());
        }

        return ordered;
    }

    private static boolean inSession(List<SessionNodeWrapper> wrappers, SessionNode node) {
        for (SessionNodeWrapper wrapper : wrappers) {
            if (wrapper.getSessionNode() == node) {
                return true;
            }
        }

        return false;
    }
}
