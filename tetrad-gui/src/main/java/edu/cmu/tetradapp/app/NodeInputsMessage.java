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

package edu.cmu.tetradapp.app;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SessionNode;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the "this box needs different inputs" dialog text for a session box
 * whose model cannot be constructed from its current parents.
 *
 * <p>Historically this dialog was a hand-written message stored per box type in
 * the configuration XML. Those messages drifted out of sync with the actual
 * model constructors, which are the ground truth: a box can build a model if
 * and only if the parent models fill some public constructor of one of the
 * box's model classes. This class derives the message from those constructors
 * directly, so the dialog cannot drift.
 *
 * <p>The message has three parts. First, if any parent box has no model yet,
 * those parents are named, since that alone prevents construction and is by
 * far the most common cause of this dialog. Second, the models the current
 * parents actually provide are listed, so the user can compare. Third, the
 * input combinations that would work are listed, each derived from a
 * constructor of one of the box's model classes and described using the same
 * names the interface itself shows under boxes.
 *
 * @author josephramsey
 */
final class NodeInputsMessage {

    /**
     * Constructor parameter types with no matching session model anywhere in
     * the configuration are rendered by simple class name; constructors
     * containing such a type (other than Parameters) can never be satisfied
     * by parents and are skipped entirely.
     */
    private NodeInputsMessage() {
    }

    /**
     * Builds the dialog message for the given session node.
     *
     * @param node   the session node whose model could not be built.
     * @param config the configuration for the node's box type.
     * @return an HTML message describing what inputs would work.
     */
    static String build(SessionNode node, SessionNodeConfig config) {
        TetradApplicationConfig appConfig = TetradApplicationConfig.getInstance();

        StringBuilder b = new StringBuilder();
        b.append("<html><body style='width: 440px'>");
        b.append("A model can't be built in this box from its current parent boxes.");

        // Part 1: parents with no models yet. This alone blocks construction.
        List<String> parentsWithoutModels = new ArrayList<>();
        List<String> parentDescriptions = new ArrayList<>();

        for (SessionNode parent : node.getParents()) {
            SessionModel model = parent.getModel();

            if (model == null) {
                parentsWithoutModels.add(parent.getDisplayName());
            } else {
                String label = labelForExactModel(appConfig, model.getClass());
                parentDescriptions.add(parent.getDisplayName()
                        + (label == null ? "" : " (" + label + ")"));
            }
        }

        if (!parentsWithoutModels.isEmpty()) {
            b.append("<br><br><b>These parent boxes have no models yet: ")
                    .append(String.join(", ", parentsWithoutModels))
                    .append(".</b><br>Double-click each of them first to build its model, ")
                    .append("then try this box again.");
        }

        // Part 2: what the parents currently provide.
        if (node.getParents().isEmpty()) {
            b.append("<br><br>This box currently has no parent boxes.");
        } else if (!parentDescriptions.isEmpty()) {
            b.append("<br><br>Parents with models currently provide: ")
                    .append(String.join(", ", parentDescriptions))
                    .append(".");
        }

        // Part 3: input combinations that would work, from the constructors.
        Map<String, Set<String>> combos = collectCombinations(appConfig, config);

        if (!combos.isEmpty()) {
            boolean showModels = config.getModels().length > 1;

            b.append("<br><br>This box can build a model from any of these ")
                    .append("input combinations:<ul>");

            int count = 0;
            int max = 15;

            for (Map.Entry<String, Set<String>> entry : combos.entrySet()) {
                if (++count > max) {
                    b.append("<li>... and ").append(combos.size() - max)
                            .append(" more combinations (see the manual).</li>");
                    break;
                }

                b.append("<li>").append(entry.getKey());

                if (showModels) {
                    List<String> models = new ArrayList<>(entry.getValue());
                    String suffix = "";

                    if (models.size() > 4) {
                        models = models.subList(0, 4);
                        suffix = ", ...";
                    }

                    b.append(" &nbsp;<i>(builds: ")
                            .append(String.join(", ", models))
                            .append(suffix).append(")</i>");
                }

                b.append("</li>");
            }

            b.append("</ul>");
        }

        b.append("Names in the combinations are the model names shown under boxes; ")
                .append("plain box-type names mean any model of that box type will do. ")
                .append("See the manual for details.");
        b.append("</body></html>");

        return b.toString();
    }

    /**
     * Collects the distinct input combinations accepted by the box, keyed by
     * their rendered description, each mapped to the set of model names it can
     * build. Combinations are sorted by number of inputs and then
     * alphabetically.
     */
    private static Map<String, Set<String>> collectCombinations(
            TetradApplicationConfig appConfig, SessionNodeConfig config) {
        Map<String, Set<String>> combos = new TreeMap<>((a, c) -> {
            int na = countInputs(a);
            int nc = countInputs(c);
            if (na != nc) return Integer.compare(na, nc);
            return a.compareTo(c);
        });

        for (Class<?> modelClass : config.getModels()) {
            String modelName = modelDisplayName(config, modelClass);

            for (Constructor<?> constructor : modelClass.getConstructors()) {
                String rendered = renderConstructor(appConfig, constructor);

                if (rendered != null) {
                    combos.computeIfAbsent(rendered, k -> new LinkedHashSet<>())
                            .add(modelName);
                }
            }
        }

        return combos;
    }

    /**
     * Renders one constructor as an input combination, or returns null if the
     * constructor can never be satisfied by session box parents.
     */
    private static String renderConstructor(TetradApplicationConfig appConfig,
                                            Constructor<?> constructor) {
        Class<?>[] types = constructor.getParameterTypes();

        // The special array form C1[] + Parameters means "one or more C1 parents".
        if (types.length == 2 && types[0].isArray() && types[1] == Parameters.class) {
            String label = labelForType(appConfig, types[0].getComponentType());
            if (label == null) return null;
            return "one or more of: " + label;
        }

        List<String> labels = new ArrayList<>();

        for (Class<?> type : types) {
            if (type == Parameters.class) {
                continue;
            }

            String label = labelForType(appConfig, type);

            // A non-Parameters argument no session model can supply means the
            // session kernel can never use this constructor; skip it so we
            // don't advertise an impossible combination.
            if (label == null) {
                return null;
            }

            labels.add(label);
        }

        if (labels.isEmpty()) {
            return "(no inputs)";
        }

        // The session kernel doesn't care about argument order, so sort the
        // labels; this also merges constructors that differ only in order.
        labels.sort(String.CASE_INSENSITIVE_ORDER);

        return String.join(" + ", labels);
    }

    /**
     * Returns a user-facing label for a constructor parameter type: the model
     * name if the type is exactly a configured model class; otherwise the box
     * type name(s) whose models can fill it; or null if no configured session
     * model anywhere can fill it.
     */
    private static String labelForType(TetradApplicationConfig appConfig,
                                       Class<?> type) {
        String exact = labelForExactModel(appConfig, type);

        if (exact != null) {
            return exact;
        }

        // Not an exact model class; find every box type containing at least
        // one model that could fill this parameter.
        Set<String> boxIds = new LinkedHashSet<>();

        for (Map.Entry<String, SessionNodeConfig> entry
                : appConfig.getConfigs().entrySet()) {
            for (Class<?> model : entry.getValue().getModels()) {
                if (type.isAssignableFrom(model)) {
                    boxIds.add(entry.getKey());
                    break;
                }
            }
        }

        if (boxIds.isEmpty()) {
            return null;
        }

        List<String> ids = new ArrayList<>();

        for (String id : boxIds) {
            ids.add(id.replace('_', ' '));
        }

        // Name the parameter by its (interface) type but say which box types
        // can supply it, so it can't be confused with a specific model name.
        String suffix = "";

        if (ids.size() > 4) {
            ids = ids.subList(0, 4);
            suffix = ", ...";
        }

        return "any " + type.getSimpleName() + " (from a "
                + String.join(", ", ids) + suffix + " box)";
    }

    /**
     * Returns the configured display name for the given class if it is exactly
     * one of the configured model classes, else null.
     */
    private static String labelForExactModel(TetradApplicationConfig appConfig,
                                             Class<?> type) {
        SessionNodeConfig config = appConfig.getSessionNodeConfig(type);

        if (config == null) {
            return null;
        }

        return modelDisplayName(config, type);
    }

    /**
     * Returns the acronym for a model class in the given config, falling back
     * to the full name and then the simple class name.
     */
    private static String modelDisplayName(SessionNodeConfig config,
                                           Class<?> modelClass) {
        SessionNodeModelConfig modelConfig = config.getModelConfig(modelClass);

        if (modelConfig == null) {
            return modelClass.getSimpleName();
        }

        if (modelConfig.acronym() != null && !modelConfig.acronym().isEmpty()) {
            return modelConfig.acronym();
        }

        if (modelConfig.name() != null && !modelConfig.name().isEmpty()) {
            return modelConfig.name();
        }

        return modelClass.getSimpleName();
    }

    /**
     * Counts inputs in a rendered combination, used only for sorting.
     */
    private static int countInputs(String rendered) {
        if (rendered.equals("(no inputs)")) return 0;
        int count = 1;
        for (int i = 0; i < rendered.length() - 2; i++) {
            if (rendered.charAt(i) == ' ' && rendered.charAt(i + 1) == '+') count++;
        }
        return count;
    }
}
