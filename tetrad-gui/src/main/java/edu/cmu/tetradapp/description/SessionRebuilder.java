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

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.app.SessionNodeConfig;
import edu.cmu.tetradapp.app.TetradApplicationConfig;
import edu.cmu.tetradapp.editor.LoadDataSettings;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.NoteModel;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a skeletal session from a {@link SessionDescription}: the boxes, in their places, connected as described,
 * each holding the settings the description gives it. The session has no data. The user loads the data and runs
 * the searches again.
 * <p>
 * What is restored, and when:
 * <ul>
 * <li>Boxes, their names, types, locations and connections: at once.</li>
 * <li>Parameters whose values were changed, and settings that have no registered default: at once, into each box's
 * parameter object, where the box's model will find them when it is made.</li>
 * <li>Notes, and graphs drawn by hand: at once, since these need no data.</li>
 * <li>Which model a box holds, a search's algorithm, test and score, and knowledge: kept on the box, and applied
 * when the user gives the box a model; see {@link PendingDescription}.</li>
 * </ul>
 * Anything in the description that cannot be restored is reported, not passed over in silence.
 *
 * @author josephramsey
 * @author Claude
 */
public final class SessionRebuilder {

    /**
     * Where the first box goes if the description gives no locations, and the spacing of the grid such boxes are
     * laid out on.
     */
    private static final int GRID_ORIGIN = 100;
    private static final int GRID_STEP_X = 160;
    private static final int GRID_STEP_Y = 140;
    private static final int GRID_COLUMNS = 5;

    private SessionRebuilder() {
    }

    /**
     * Builds a session from a description.
     *
     * @param description the description.
     * @param sessionName the name to give the session.
     * @return the session, with an account of what remains for the user to do and of what could not be restored.
     */
    public static Result rebuild(SessionDescription description, String sessionName) {
        SessionWrapper session = new SessionWrapper(new Session(sessionName));
        List<String> toDo = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Map<String, SessionNodeWrapper> wrappers = new LinkedHashMap<>();
        Map<String, BoxDescription> boxes = new LinkedHashMap<>();
        int unplaced = 0;

        // The boxes.
        for (BoxDescription box : description.getBoxes()) {
            String name = box.getName();
            String boxType = box.get(BoxDescription.TYPE);
            SessionNodeConfig nodeConfig = boxType == null ? null
                    : TetradApplicationConfig.getInstance().getSessionNodeConfig(boxType);

            if (name.isEmpty() || wrappers.containsKey(name)) {
                problems.add("A second box named \"" + name + "\" was left out; box names must differ.");
                continue;
            }

            if (nodeConfig == null) {
                problems.add(name + ": left out, since \"" + boxType + "\" is not a kind of box this version of "
                             + "Tetrad has.");
                continue;
            }

            SessionNode node = new SessionNode(boxType, name, nodeConfig.getModels());
            SessionNodeWrapper wrapper = new SessionNodeWrapper(node);
            wrapper.setButtonType(boxType);

            int[] location = box.getLocation();

            if (location == null) {
                location = new int[]{GRID_ORIGIN + GRID_STEP_X * (unplaced % GRID_COLUMNS),
                        GRID_ORIGIN + GRID_STEP_Y * (unplaced / GRID_COLUMNS)};
                unplaced++;
            }

            wrapper.setCenter(location[0], location[1]);

            if (!session.addNode(wrapper)) {
                problems.add(name + ": could not be added to the session.");
                continue;
            }

            wrappers.put(name, wrapper);
            boxes.put(name, box);
        }

        // The connections. A box must be freshly made when it is added to a session, so these come after.
        for (BoxDescription box : boxes.values()) {
            SessionNodeWrapper child = wrappers.get(box.getName());

            for (String parentName : description.getParentNames(box)) {
                SessionNodeWrapper parent = wrappers.get(parentName);

                if (parent == null) {
                    problems.add(box.getName() + ": its parent \"" + parentName + "\" is not in the session, so "
                                 + "that connection was left out.");
                } else if (!session.addEdge(new Edge(parent, child, Endpoint.TAIL, Endpoint.ARROW))) {
                    problems.add(box.getName() + ": the connection from \"" + parentName + "\" could not be made.");
                }
            }
        }

        // The settings.
        for (BoxDescription box : boxes.values()) {
            String name = box.getName();
            SessionNode node = wrappers.get(name).getSessionNode();
            String modelText = box.get(BoxDescription.MODEL);
            Class<?> modelClass = PendingDescription.modelClass(node.getBoxType(), modelText);

            if (modelClass == null) {
                if (modelText != null && !PendingDescription.NO_MODEL.equals(modelText)) {
                    problems.add(name + ": \"" + modelText + "\" is not a model this version of Tetrad offers for "
                                 + "a " + node.getBoxType() + " box, so the box was left empty.");
                }

                continue;
            }

            node.putParam(modelClass, parameters(name, box, problems));
            PendingDescription.set(node, box);

            if (modelClass == GeneralAlgorithmRunner.class) {
                problems.addAll(PendingDescription.checkSearch(name, box));
            }

            // Notes and hand-drawn graphs need nothing from other boxes, so they are made now.
            boolean standsAlone = node.getParents().isEmpty()
                                  && (modelClass == NoteModel.class || modelClass == GraphWrapper.class
                                      || modelClass == DagWrapper.class || modelClass == SemGraphWrapper.class);

            if (standsAlone) {
                try {
                    node.createModel(modelClass, false);
                    problems.addAll(PendingDescription.apply(node));
                } catch (Exception e) {
                    problems.add(name + ": its model could not be made now (" + e + "); double click the box to "
                                 + "make it.");
                }
            } else {
                toDo.add(toDo(name, box, node));
            }
        }

        return new Result(session, List.copyOf(toDo), List.copyOf(problems));
    }

    /**
     * Makes the parameter object for a box: defaults, with the changed parameters set as described. A value is read
     * as the kind of value the parameter's registered default is.
     */
    private static Parameters parameters(String name, BoxDescription box, List<String> problems) {
        Parameters parameters = new Parameters();

        for (BoxDescription.ChangedParameter changed : box.getChangedParameters()) {
            if (!ParamDescriptions.getInstance().getNames().contains(changed.name())) {
                problems.add(name + ": the parameter \"" + changed.name() + "\" is not known to this version of "
                             + "Tetrad, so its value " + changed.value() + " was not set.");
                continue;
            }

            Serializable defaultValue = ParamDescriptions.getInstance().get(changed.name()).getDefaultValue();

            try {
                String text = changed.value().strip();

                if (defaultValue instanceof Integer) {
                    parameters.set(changed.name(), (Object) Integer.valueOf(text));
                } else if (defaultValue instanceof Long) {
                    parameters.set(changed.name(), (Object) Long.valueOf(text));
                } else if (defaultValue instanceof Double) {
                    parameters.set(changed.name(), (Object) Double.valueOf(text));
                } else if (defaultValue instanceof Boolean) {
                    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                        throw new NumberFormatException();
                    }

                    parameters.set(changed.name(), (Object) Boolean.valueOf(text));
                } else if (defaultValue instanceof String) {
                    parameters.set(changed.name(), changed.value());
                } else {
                    problems.add(name + ": the parameter \"" + changed.name() + "\" has a kind of value that "
                                 + "cannot be read from text, so its value " + changed.value() + " was not set.");
                }
            } catch (NumberFormatException e) {
                problems.add(name + ": the value " + changed.value() + " of the parameter \"" + changed.name()
                             + "\" could not be read, so it was not set.");
            }
        }

        // Settings with no registered default: each says what kind of value it is.
        for (String setting : box.getAll(StandardDescribers.SETTING)) {
            int equals = setting.indexOf(" = ");

            if (equals <= 0) {
                problems.add(name + ": the setting \"" + setting + "\" could not be read, so it was not set.");
                continue;
            }

            try {
                parameters.set(setting.substring(0, equals).strip(), SettingLiteral.read(setting.substring(equals + 3)));
            } catch (IllegalArgumentException e) {
                problems.add(name + ": the setting \"" + setting + "\" was not set. " + e.getMessage());
            }
        }

        // Settings that take several lines.
        for (BoxDescription.Item item : box.getItems()) {
            if (!(item instanceof BoxDescription.Block block)) {
                continue;
            }

            if (block.name().startsWith(StandardDescribers.LIST_BLOCK)) {
                parameters.set(block.name().substring(StandardDescribers.LIST_BLOCK.length()).strip(),
                        (Object) new ArrayList<>(block.lines()));
            } else if (block.name().startsWith(StandardDescribers.TEXT_BLOCK)) {
                parameters.set(block.name().substring(StandardDescribers.TEXT_BLOCK.length()).strip(),
                        String.join("\n", block.lines()));
            } else if (block.name().startsWith(StandardDescribers.VALUES_BLOCK)) {
                parameters.set(block.name().substring(StandardDescribers.VALUES_BLOCK.length()).strip(),
                        block.lines().toArray(new String[0]));
            }
        }

        // What the description says it could not hold, the user has to set again.
        for (String setting : box.getAll(StandardDescribers.NOT_DESCRIBED)) {
            problems.add(name + ": the setting \"" + setting + "\" is of a kind a description cannot hold; set it "
                         + "again in the box.");
        }

        // How each variable is discretized, as the discretization box keeps it.
        List<String> discretize = box.getAll(StandardDescribers.DISCRETIZE);

        if (!discretize.isEmpty()) {
            Map<Node, DiscretizationSpec> specs = new HashMap<>();

            for (String line : discretize) {
                try {
                    StandardDescribers.readDiscretizeLine(line, specs);
                } catch (IllegalArgumentException e) {
                    problems.add(name + ": the discretization \"" + line + "\" was not set, since "
                                 + e.getMessage());
                }
            }

            parameters.set("discretizationSpecs", (Object) specs);
        }

        return parameters;
    }

    /**
     * Says what remains for the user to do for a box that could not be given a model yet.
     */
    private static String toDo(String name, BoxDescription box, SessionNode node) {
        List<String> files = box.getAll(LoadDataSettings.KEY_FILE);

        if (files.isEmpty()) {
            files = box.getAll(StandardDescribers.DATA_SET);
        }

        if ("Data".equals(node.getBoxType()) && node.getParents().isEmpty() && !files.isEmpty()) {
            return name + ": double click it and load " + String.join(", ", files) + ".";
        }

        if (node.getParents().isEmpty()) {
            return name + ": double click it to make its model.";
        }

        return name + ": double click it once the boxes above it have their models.";
    }

    /**
     * A session built from a description.
     *
     * @param session  the session.
     * @param toDo     one line for each box the user still has to give a model, saying how.
     * @param problems one line for each thing in the description that could not be restored; empty if all was.
     */
    public record Result(SessionWrapper session, List<String> toDo, List<String> problems) {
    }
}
