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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor.search;

import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.LatentStructureAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.annotation.*;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.util.DeprecationUtils;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The filtering and labeling rules behind {@link GuidedAlgorithmCard}, kept free of Swing so they can be unit tested.
 * <p>
 * Added 2026-8-24. The guided chooser asks the user two or three questions about their data and their willingness to
 * assume causal sufficiency, and narrows the algorithm list accordingly. Every facet used here is a field the
 * algorithm registry already carries ({@code algoType}, {@code dataType}, {@code @TimeSeries},
 * {@code AcceptsKnowledge}, {@code @Experimental}); no new metadata is needed.
 */
public final class AlgorithmChooserLogic {

    private AlgorithmChooserLogic() {
    }

    /**
     * The user's answer to "could something unmeasured cause two of your variables?"
     */
    public enum LatentChoice {
        /**
         * Show both families.
         */
        ANY,
        /**
         * Causal sufficiency assumed: only algorithms that forbid latent common causes.
         */
        NO,
        /**
         * Only algorithms that allow latent common causes.
         */
        YES;

        /**
         * Parses the persisted form; unknown or null strings give {@link #ANY}.
         *
         * @param s the persisted string.
         * @return the choice.
         */
        public static LatentChoice parse(String s) {
            if (s == null) return ANY;
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ANY;
            }
        }
    }

    /**
     * The set of answers that determine which algorithms are listed.
     *
     * @param latent       the latent-confounder answer.
     * @param timeSeries   true to show only time-series algorithms.
     * @param knowledge    true to show only algorithms that accept background knowledge.
     * @param experimental true to include algorithms annotated {@code @Experimental}.
     * @param query        a case-insensitive substring to match against name and command; may be null or blank.
     */
    public record Answers(LatentChoice latent, boolean timeSeries, boolean knowledge, boolean experimental,
                          String query) {

        /**
         * Returns a copy with the latent answer replaced.
         *
         * @param l the new latent answer.
         * @return the copy.
         */
        public Answers withLatent(LatentChoice l) {
            return new Answers(l, timeSeries, knowledge, experimental, query);
        }

        /**
         * Returns a copy with the time-series flag replaced.
         *
         * @param b the new flag.
         * @return the copy.
         */
        public Answers withTimeSeries(boolean b) {
            return new Answers(latent, b, knowledge, experimental, query);
        }

        /**
         * Returns a copy with the knowledge flag replaced.
         *
         * @param b the new flag.
         * @return the copy.
         */
        public Answers withKnowledge(boolean b) {
            return new Answers(latent, timeSeries, b, experimental, query);
        }

        /**
         * Returns a copy with the query cleared, for computing the counts shown next to each option.
         *
         * @return the copy.
         */
        public Answers withoutQuery() {
            return new Answers(latent, timeSeries, knowledge, experimental, null);
        }
    }

    /**
     * Returns every registered, non-deprecated algorithm as a model, sorted by name. Experimental algorithms are
     * included; callers filter them with {@link Answers#experimental()}.
     *
     * @return the models.
     */
    public static List<AlgorithmModel> allModels() {
        AlgorithmAnnotations anno = AlgorithmAnnotations.getInstance();
        List<AlgorithmModel> models = new ArrayList<>();
        for (AnnotatedClass<Algorithm> ac : anno.filterOutDeprecated(anno.getAnnotatedClasses())) {
            models.add(new AlgorithmModel(ac));
        }
        Collections.sort(models);
        return models;
    }

    /**
     * Applies the user's answers, the data type, and the runner's block-spec state to a list of models.
     *
     * @param models    the candidate models (typically {@link #allModels()}).
     * @param dataType  the data type of the connected data, or null if none.
     * @param blocks    true if the search runs over blocks (latent-structure runner), in which case only
     *                  {@link LatentStructureAlgorithm} classes are listed; false lists everything except
     *                  {@link ExtraLatentStructureAlgorithm} classes, matching the classic card.
     * @param answers   the user's answers.
     * @return the models that pass, in the order given.
     */
    public static List<AlgorithmModel> filter(List<AlgorithmModel> models, DataType dataType, boolean blocks,
                                              Answers answers) {
        List<AlgorithmModel> out = new ArrayList<>();
        String q = answers.query() == null ? "" : answers.query().trim().toLowerCase(Locale.ROOT);

        for (AlgorithmModel m : models) {
            Class<?> c = m.getAlgorithm().clazz();
            Algorithm a = m.getAlgorithm().annotation();
            if (DeprecationUtils.isClassDeprecated(c)) continue;
            if (!answers.experimental() && c.isAnnotationPresent(Experimental.class)) continue;
            if (!fitsData(a.dataType(), dataType)) continue;

            if (blocks) {
                if (!LatentStructureAlgorithm.class.isAssignableFrom(c)) continue;
            } else {
                if (ExtraLatentStructureAlgorithm.class.isAssignableFrom(c)) continue;
            }

            switch (answers.latent()) {
                case NO -> {
                    if (a.algoType() != AlgType.forbid_latent_common_causes) continue;
                }
                case YES -> {
                    if (a.algoType() != AlgType.allow_latent_common_causes) continue;
                }
                default -> {
                }
            }

            if (answers.timeSeries() && !c.isAnnotationPresent(TimeSeries.class)) continue;
            if (answers.knowledge() && !AcceptsKnowledge.class.isAssignableFrom(c)) continue;

            if (!q.isEmpty()) {
                String hay = (a.name() + " " + a.command()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }

            out.add(m);
        }
        return out;
    }

    /**
     * Data-type compatibility, matching {@code AlgorithmModels.filterInclusivelyByAllOrSpecificDataType}: an algorithm
     * fits if it declares {@code All} or the given type. A null data type (no data connected) fits only algorithms
     * declaring {@code All}.
     *
     * @param declared the algorithm's declared data types.
     * @param dataType the data's type, or null.
     * @return true if compatible.
     */
    public static boolean fitsData(DataType[] declared, DataType dataType) {
        if (dataType == DataType.All) return true;
        for (DataType d : declared) {
            if (d == DataType.All) return true;
            if (dataType != null && d == dataType) return true;
        }
        return false;
    }

    /**
     * One line saying what an algorithm of this type assumes and returns. Shown under each row so a user can rule an
     * algorithm in or out without opening the manual entry.
     *
     * @param type the algorithm type.
     * @return the sentence.
     */
    public static String role(AlgType type) {
        if (type == null) return "";
        return switch (type) {
            case forbid_latent_common_causes -> "No latent confounders assumed \u00b7 returns a CPDAG or DAG";
            case allow_latent_common_causes -> "Allows latent confounders \u00b7 returns a PAG";
            case search_for_Markov_blankets -> "Markov blanket around one target variable";
            case produce_undirected_graphs -> "Adjacencies only \u00b7 no orientations";
            case orient_pairwise -> "Orients an existing pair or edge set";
            case search_for_structure_over_latents -> "Structure over latent variables (measurement model)";
        };
    }

    /**
     * Short label for what the algorithm needs in addition to itself.
     *
     * @param m the model.
     * @return "test + score", "test", "score", or "no test or score".
     */
    public static String needs(AlgorithmModel m) {
        boolean t = m.isRequiredTest();
        boolean s = m.isRequiredScore();
        if (t && s) return "test + score";
        if (t) return "test";
        if (s) return "score";
        return "no test or score";
    }

    /**
     * Returns true if the manual has no real description for the model (the registry returns a "please add"
     * placeholder).
     *
     * @param description the description as returned by the model.
     * @return true if it is a placeholder.
     */
    public static boolean isPlaceholderDescription(String description) {
        if (description == null || description.isBlank()) return true;
        String d = description.trim().toLowerCase(Locale.ROOT);
        return d.startsWith("please add a description");
    }

    /**
     * The first sentence of a description, for the one-line summary under an algorithm's name. A placeholder
     * description gives an explicit "no description in the manual yet" so the gap is visible rather than hidden.
     *
     * @param description the description.
     * @return the first sentence.
     */
    public static String firstSentence(String description) {
        if (isPlaceholderDescription(description)) {
            return "No description in the manual yet.";
        }
        String d = description.trim().replaceAll("\\s+", " ");
        // Sentence boundary: a period, question mark, or exclamation followed by whitespace and an uppercase letter,
        // ignoring common abbreviations that would otherwise split early.
        int best = -1;
        for (int i = 0; i < d.length() - 1; i++) {
            char ch = d.charAt(i);
            if ((ch == '.' || ch == '?' || ch == '!') && Character.isWhitespace(d.charAt(i + 1))) {
                int j = i + 1;
                while (j < d.length() && Character.isWhitespace(d.charAt(j))) j++;
                if (j >= d.length() || Character.isUpperCase(d.charAt(j)) || d.charAt(j) == '"') {
                    String before = d.substring(0, i + 1);
                    if (before.matches(".*\\b(e\\.g\\.|i\\.e\\.|vs\\.|et al\\.|cf\\.|Fig\\.|No\\.)$")) continue;
                    best = i + 1;
                    break;
                }
            }
        }
        if (best < 0) return d;
        return d.substring(0, best).trim();
    }
}
