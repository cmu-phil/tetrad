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

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.Score;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetradapp.editor.LoadDataSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes a py-tetrad script from a session description: a Python program that loads the data, sets up the knowledge
 * and runs each search with the settings the session had.
 * <p>
 * The script covers the chain from a data file, through knowledge, to a search. It calls Tetrad's own classes
 * through JPype, the same ones the interface uses, so that every algorithm, test and score the interface offers can
 * be written, under the parameter names a session description gives, with parameters that were not changed left to
 * Tetrad's defaults. The data are read with the readers the Load Data dialog uses, with the settings recorded when
 * the file was loaded.
 * <p>
 * What the script does not cover is said in the script and not passed over. A box the searches depend on that cannot
 * be written, such as a box that changes the data, becomes a comment giving the box's settings, followed by a line
 * that stops the script until the step is written by hand; a script that ran on without the step would be running a
 * different analysis. A search that cannot be written becomes a comment and a printed remark. Boxes no search
 * depends on are listed at the top and left out.
 * <p>
 * The script works from the description alone, so it can be written for a description read from a file as well as
 * for an open session.
 *
 * @author Claude (Anthropic)
 * @see SessionDescription
 */
public final class PyTetradScript {

    private static final String DATA_BOX = "Data";
    private static final String SIMULATION_BOX = "Simulation";
    private static final String KNOWLEDGE_BOX = "Knowledge";
    private static final String NOTE_BOX = "Note";

    /**
     * The data box models whose step is written as code: the box's own class, made from the data of the box above
     * and the box's settings. Each is one that takes its data from one data box, needs nothing but its settings,
     * and has been run from a script and found to give the data the session had, or, for those that draw random
     * numbers, to run. A model not listed here is left for the user to write.
     */
    static final Set<String> STEPS_WRITTEN = Set.of(
            "edu.cmu.tetradapp.model.DataWrapper",
            "edu.cmu.tetradapp.model.CorrMatrixConverter",
            "edu.cmu.tetradapp.model.CovarianceEstimateDataWrapper",
            "edu.cmu.tetradapp.model.AddMissingnessIndicatorsWrapper",
            "edu.cmu.tetradapp.model.DataCenterer",
            "edu.cmu.tetradapp.model.DataStandardizer",
            "edu.cmu.tetradapp.model.ExtraCategoryInterpolatorWrapper",
            "edu.cmu.tetradapp.model.GaussianCopulaCorrelationConverter",
            "edu.cmu.tetradapp.model.ImputeMissingDataWrapper",
            "edu.cmu.tetradapp.model.LinearizeTransform",
            "edu.cmu.tetradapp.model.LogData",
            "edu.cmu.tetradapp.model.LongToWideWrapper",
            "edu.cmu.tetradapp.model.MissingDataInjectorWrapper",
            "edu.cmu.tetradapp.model.ModeInterpolatorWrapper",
            "edu.cmu.tetradapp.model.NonparanormalTransform",
            "edu.cmu.tetradapp.model.datamanip.ArResidualsWrapper",
            "edu.cmu.tetradapp.model.datamanip.ConvertNumericalDiscreteToContinuousWrapper",
            "edu.cmu.tetradapp.model.datamanip.CovMatrixWrapper",
            "edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper",
            "edu.cmu.tetradapp.model.datamanip.ExpandColumnsWrapper",
            "edu.cmu.tetradapp.model.datamanip.FirstDifferencesWrapper",
            "edu.cmu.tetradapp.model.datamanip.InverseMatrixWrapper",
            "edu.cmu.tetradapp.model.datamanip.MeanInterpolatorWrapper",
            "edu.cmu.tetradapp.model.datamanip.PermuteRowsWrapper",
            "edu.cmu.tetradapp.model.datamanip.RemoveConstantColumnsWrapper",
            "edu.cmu.tetradapp.model.datamanip.RemoveMissingValueCasesWrapper",
            "edu.cmu.tetradapp.model.datamanip.ReorderColumnsWrapper",
            "edu.cmu.tetradapp.model.datamanip.SelectionWrapper",
            "edu.cmu.tetradapp.model.datamanip.SimulateFromCovWrapper",
            "edu.cmu.tetradapp.model.datamanip.SubsetContinuousVariablesWrapper",
            "edu.cmu.tetradapp.model.datamanip.SubsetDiscreteVariablesWrapper",
            "edu.cmu.tetradapp.model.datamanip.ThinRowsWrapper",
            "edu.cmu.tetradapp.model.datamanip.TimeSeriesWrapper",
            "edu.cmu.tetradapp.model.datamanip.TimeSeriesWrapper2");

    /**
     * Those of them that draw random numbers, so that the data the step gives in a script are not the data the
     * session had.
     */
    static final Set<String> STEPS_RANDOM = Set.of(
            "edu.cmu.tetradapp.model.MissingDataInjectorWrapper",
            "edu.cmu.tetradapp.model.datamanip.PermuteRowsWrapper",
            "edu.cmu.tetradapp.model.datamanip.ReorderColumnsWrapper",
            "edu.cmu.tetradapp.model.datamanip.SimulateFromCovWrapper");

    /**
     * Data box models that work from what was selected by hand in the box above, which a description does not
     * hold.
     */
    static final Set<String> STEPS_BY_SELECTION = Set.of(
            "edu.cmu.tetradapp.model.SelectedDataset",
            "edu.cmu.tetradapp.model.datamanip.SubsetSelectedVariablesWrapper",
            "edu.cmu.tetradapp.model.datamanip.RemoveSelectedVariablesWrapper");

    /**
     * Data box models that work behind a progress window, which a script does not have.
     */
    static final Set<String> STEPS_BEHIND_A_WINDOW = Set.of(
            "edu.cmu.tetradapp.model.datamanip.CausalUnmix");

    /**
     * Names the script uses itself, which a box's variable must not take.
     */
    private static final Set<String> RESERVED = Set.of("os", "jpype", "pytetrad", "read_tabular", "read_covariance",
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
            "match", "case", "type", "print", "java", "edu", "data_box", "save_graph");

    private final SessionDescription description;

    /**
     * The Python variable for each box, by box name.
     */
    private final Map<String, String> variables = new HashMap<>();
    private final Set<String> variablesTaken = new HashSet<>();

    /**
     * What each box written so far leaves for later boxes to use.
     */
    private final Map<String, Provides> provides = new HashMap<>();

    /**
     * The Java classes the script imports: package, then the simple names from it.
     */
    private final Map<String, Set<String>> javaImports = new TreeMap<>();

    /**
     * The name each imported algorithm, test or score class goes by in the script, by its full name. Two classes
     * of the same simple name in different packages are told apart by importing the second under another name.
     */
    private final Map<String, String> localNames = new LinkedHashMap<>();

    private final StringBuilder body = new StringBuilder();
    private final List<String> notWritten = new ArrayList<>();

    /**
     * What becomes of each box, in the order the boxes are met.
     */
    private final List<Box> plan = new ArrayList<>();
    private boolean readsTabular;
    private boolean readsCovariance;
    private boolean usesJInt;
    private boolean savesGraphs;
    private boolean makesDataBoxes;

    private PyTetradScript(SessionDescription description) {
        this.description = description;
    }

    /**
     * Writes the script for a session description.
     *
     * @param description the description.
     * @return the script, and what in the session it does not cover.
     */
    public static Result render(SessionDescription description) {
        if (description == null) {
            throw new NullPointerException("The description is null.");
        }

        return new PyTetradScript(description).render();
    }

    //=================================== THE WHOLE SCRIPT ===================================//

    private Result render() {
        List<BoxDescription> boxes = this.description.getBoxes();
        Set<String> searches = new LinkedHashSet<>();

        for (BoxDescription box : boxes) {
            if (box.get(StandardDescribers.ALGORITHM) != null) {
                searches.add(box.getName());
            }
        }

        // The boxes some search depends on, directly or through other boxes.
        Set<String> needed = new HashSet<>();

        for (String search : searches) {
            collectAncestors(search, needed);
        }

        List<String> leftOut = new ArrayList<>();

        // A description lists parents before children, so each box is written after what it uses.
        for (BoxDescription box : boxes) {
            String type = box.get(BoxDescription.TYPE);

            if (NOTE_BOX.equals(type)) {
                note(box);
            } else if (searches.contains(box.getName())) {
                search(box);
            } else if (!needed.contains(box.getName())) {
                leftOut.add(title(box));
                this.plan.add(new Box(title(box), Fate.LEFT_OUT,
                        PendingDescription.NO_MODEL.equals(box.get(BoxDescription.MODEL)) ? "The box is empty."
                                : ""));
            } else if (KNOWLEDGE_BOX.equals(type)) {
                knowledge(box);
            } else if (DATA_BOX.equals(type) && this.description.getParentNames(box).isEmpty()) {
                loadedData(box);
            } else if (DATA_BOX.equals(type)) {
                derivedData(box);
            } else if (SIMULATION_BOX.equals(type)) {
                dataToWriteByHand(box, "A simulation draws new data each time it is run, so the data the session "
                                       + "had cannot be made again from the box's settings.");
            } else {
                // A box of another kind that a search takes as input; the search says so where it stands.
                leftOut.add(title(box));
                this.plan.add(new Box(title(box), Fate.LEFT_OUT,
                        "A search takes it as input, and that search is not written."));
            }
        }

        if (searches.isEmpty()) {
            this.notWritten.add("The session has no search box with an algorithm chosen, so the script runs nothing.");
        }

        StringBuilder script = new StringBuilder();
        header(script, leftOut);
        imports(script);
        helpers(script);
        script.append(this.body);

        List<String> notes = new ArrayList<>(this.notWritten);

        if (!leftOut.isEmpty()) {
            notes.add("Left out, since no search that is written depends on them: " + String.join("; ", leftOut)
                      + ".");
        }

        return new Result(script.toString(), notes, List.copyOf(this.plan));
    }

    private void collectAncestors(String boxName, Set<String> into) {
        BoxDescription box = this.description.getBox(boxName);

        if (box == null) {
            return;
        }

        for (String parent : this.description.getParentNames(box)) {
            if (into.add(parent)) {
                collectAncestors(parent, into);
            }
        }
    }

    private void header(StringBuilder script, List<String> leftOut) {
        String session = this.description.getHeader(SessionDescription.SESSION);
        String version = this.description.getHeader(SessionDescription.TETRAD_VERSION);
        String written = this.description.getHeader(SessionDescription.WRITTEN);

        script.append("# A py-tetrad script written by Tetrad from the session ")
                .append(session == null ? "(unnamed)" : quoted(session)).append(".\n");

        if (version != null || written != null) {
            script.append("#");
            if (version != null) script.append(" Tetrad version: ").append(oneLine(version)).append(".");
            if (written != null) script.append(" Session described: ").append(oneLine(written)).append(".");
            script.append("\n");
        }

        script.append("""
                #
                # It loads the data, runs the steps that change the data, sets up the knowledge, and runs
                # each search, with the settings the session had. A parameter that is not set here is
                # left at Tetrad's default.
                #
                # To run it, install py-tetrad, which needs Java 21 or later:
                #     pip install git+https://github.com/cmu-phil/py-tetrad
                # Then put this file in the folder the data files are in, or set DATA_DIR below.
                #
                # Each graph is printed, and saved as a text file that a Graph box in Tetrad can load.
                # The files go in the folder the script is run from, unless OUTPUT_DIR is set below.
                # Running the script again replaces them.
                #
                # The Tetrad that py-tetrad brings with it may be older or newer than the Tetrad the
                # session was made in. If Python cannot find a class named here, that is the likely
                # reason.
                """);

        if (!this.notWritten.isEmpty()) {
            script.append("#\n# Not turned into code; see the comments at each place below:\n");

            for (String line : this.notWritten) {
                comment(script, "#     ", "#         ", line);
            }
        }

        if (!leftOut.isEmpty()) {
            script.append("#\n# Boxes left out, since no search written here depends on them:\n");

            for (String line : leftOut) {
                comment(script, "#     ", "#         ", line);
            }
        }

        script.append("\n");
    }

    private void imports(StringBuilder script) {
        if (this.readsTabular || this.readsCovariance || this.savesGraphs) {
            script.append("import os\n\n");
        }

        script.append("""
                # Importing this starts Java with the Tetrad jar that comes with py-tetrad. It must come
                # before the Java imports below.
                import pytetrad.tools.translate  # noqa: F401
                import jpype.imports  # noqa: F401
                """);

        List<String> fromJpype = new ArrayList<>();
        if (this.readsTabular) fromJpype.add("JArray");
        if (this.readsTabular || this.usesJInt) fromJpype.add("JInt");

        if (!fromJpype.isEmpty()) {
            script.append("from jpype import ").append(String.join(", ", fromJpype)).append("\n");
        }

        script.append("\n");

        for (Map.Entry<String, Set<String>> entry : this.javaImports.entrySet()) {
            script.append("from ").append(entry.getKey()).append(" import ")
                    .append(String.join(", ", entry.getValue())).append("\n");
        }

        script.append("\n");
    }

    private void helpers(StringBuilder script) {
        if (this.readsTabular || this.readsCovariance) {
            script.append("""
                    # The folder the data files are in.
                    DATA_DIR = "."

                    """);
        }

        if (this.readsTabular) {
            script.append("""

                    def read_tabular(file_name, delimiter, kind, max_categories=0, has_header=True,
                                     exclude_first_column=False, exclude_column=None, comment_marker="",
                                     quote_character=None, missing_marker=""):
                        \"""Reads a data file the way Tetrad's Load Data dialog does.

                        kind is "continuous", "discrete" or "mixed". For mixed data, a column with at most
                        max_categories distinct values is read as discrete.
                        \"""
                        path = Paths.get(os.path.join(DATA_DIR, file_name))
                        discrete = kind == "discrete"

                        column_reader = TabularColumnFileReader(path, delimiter)
                        column_reader.setCommentMarker(comment_marker)
                        if quote_character is not None:
                            column_reader.setQuoteCharacter(quote_character)

                        if exclude_column is not None:
                            excluded = HashSet()
                            excluded.add(exclude_column)
                            columns = column_reader.readInDataColumns(excluded, discrete)
                        else:
                            excluded = JArray(JInt)([1] if exclude_first_column else [])
                            if has_header:
                                columns = column_reader.readInDataColumns(excluded, discrete)
                            else:
                                columns = column_reader.generateColumns(excluded, discrete)

                        data_reader = TabularDataFileReader(path, delimiter)
                        data_reader.setCommentMarker(comment_marker)
                        data_reader.setMissingDataMarker(missing_marker)
                        if quote_character is not None:
                            data_reader.setQuoteCharacter(quote_character)
                        if kind == "mixed":
                            data_reader.determineDiscreteDataColumns(columns, max_categories, has_header)

                        data = DataConvertUtils.toDataModel(data_reader.read(columns, has_header))
                        data.setName(file_name)
                        return data

                    """);
        }

        if (this.readsCovariance) {
            script.append("""

                    def read_covariance(file_name, delimiter, lower_triangle=True, comment_marker="",
                                        quote_character=None):
                        \"""Reads a covariance matrix the way Tetrad's Load Data dialog does.\"""
                        path = Paths.get(os.path.join(DATA_DIR, file_name))
                        if lower_triangle:
                            reader = LowerCovarianceDataFileReader(path, delimiter)
                        else:
                            reader = FullCovarianceDataFileReader(path, delimiter)
                        reader.setCommentMarker(comment_marker)
                        if quote_character is not None:
                            reader.setQuoteCharacter(quote_character)

                        data = DataConvertUtils.toDataModel(reader.readInData())
                        data.setName(file_name)
                        return data

                    """);
        }

        if (this.makesDataBoxes) {
            script.append("""

                    def data_box(data):
                        \"""A Tetrad data box holding the given data, for a box's own step to be run on.\"""
                        box = DataWrapper(Parameters())
                        box.setDataModel(data)
                        return box

                    """);
        }

        if (this.savesGraphs) {
            script.append("""
                    # The folder the graphs are saved in.
                    OUTPUT_DIR = "."


                    def save_graph(graph, file_name):
                        \"""Saves a graph in Tetrad's text format, which a Graph box in Tetrad can load.

                        To use the graph in Python instead, pytetrad.tools.translate has graph_to_matrix(graph),
                        which gives a pandas table of its endpoints.\"""
                        os.makedirs(OUTPUT_DIR, exist_ok=True)
                        path = os.path.abspath(os.path.join(OUTPUT_DIR, file_name))
                        GraphSaveLoadUtils.saveGraph(graph, File(path), False)
                        print("Saved to " + path)

                    """);
        }
    }

    //=================================== THE BOXES ===================================//

    /**
     * A note box: its text, as a comment.
     */
    private void note(BoxDescription box) {
        List<String> lines = box.getBlock(StandardDescribers.NOTE_BLOCK);

        if (lines == null || lines.stream().allMatch(String::isBlank)) {
            this.plan.add(new Box(title(box), Fate.LEFT_OUT, "The note is empty."));
            return;
        }

        this.plan.add(new Box(title(box), Fate.WRITTEN, "Its text, as a comment."));
        heading(box);

        for (String line : lines) {
            this.body.append("#     ").append(oneLine(line)).append("\n");
        }
    }

    /**
     * A data box with no parents: data loaded from a file.
     */
    private void loadedData(BoxDescription box) {
        int dataSets = box.getAll(StandardDescribers.DATA_SET).size();
        Map<String, Map<String, String>> allSettings = PendingDescription.describedLoadSettings(box);

        if (dataSets != 1) {
            dataToWriteByHand(box, dataSets == 0 ? "This box held no data when the session was described."
                    : "This box holds " + dataSets + " data sets, and a script is written only for a box "
                      + "that holds one.");
            return;
        }

        if (allSettings.isEmpty()) {
            dataToWriteByHand(box, "The description does not say what file these data came from, or how the file "
                                   + "was read.");
            return;
        }

        Map<String, String> settings = allSettings.values().iterator().next();
        String file = settings.get(LoadDataSettings.KEY_FILE);
        String fileType = settings.get(LoadDataSettings.KEY_FILE_TYPE);
        String delimiter = delimiter(settings.get(LoadDataSettings.KEY_DELIMITER));
        String quote = settings.get(LoadDataSettings.KEY_QUOTE);
        String comment = settings.get(LoadDataSettings.KEY_COMMENT);

        if (delimiter == null) {
            dataToWriteByHand(box, "The value delimiter " + quoted(settings.get(LoadDataSettings.KEY_DELIMITER))
                                   + " is not one this script writer knows.");
            return;
        }

        if (settings.containsKey(LoadDataSettings.KEY_METADATA)) {
            dataToWriteByHand(box, "The file was read with a metadata file, which this script writer does not "
                                   + "cover.");
            return;
        }

        List<String> arguments = new ArrayList<>();
        arguments.add(python(file));
        arguments.add("delimiter=Delimiter." + delimiter);
        String function;

        if (LoadDataSettings.FILE_TYPE_TABULAR.equals(fileType)) {
            String kind = settings.get(LoadDataSettings.KEY_DATA_TYPE);
            String caseColumn = settings.get(LoadDataSettings.KEY_CASE_COLUMN);
            String missing = settings.get(LoadDataSettings.KEY_MISSING);
            boolean header = !"No".equals(settings.get(LoadDataSettings.KEY_FIRST_ROW));

            if ("Continuous".equals(kind)) {
                arguments.add("kind=\"continuous\"");
            } else if ("Discrete".equals(kind)) {
                arguments.add("kind=\"discrete\"");
            } else if (kind != null && kind.startsWith(LoadDataSettings.MIXED_PREFIX)) {
                String max = kind.substring(LoadDataSettings.MIXED_PREFIX.length()).strip();

                if (!max.matches("[0-9]+")) {
                    dataToWriteByHand(box, "The data type " + quoted(kind) + " could not be read.");
                    return;
                }

                arguments.add("kind=\"mixed\"");
                arguments.add("max_categories=" + max);
            } else {
                dataToWriteByHand(box, "The data type " + quoted(kind) + " is not one this script writer knows.");
                return;
            }

            arguments.add("has_header=" + (header ? "True" : "False"));

            if ("First column".equals(caseColumn)) {
                arguments.add("exclude_first_column=True");
            } else if (caseColumn != null && caseColumn.startsWith(LoadDataSettings.COLUMN_LABELED_PREFIX)) {
                if (!header) {
                    dataToWriteByHand(box, "A case column is left out by name, but the file has no row of names.");
                    return;
                }

                arguments.add("exclude_column="
                              + python(caseColumn.substring(LoadDataSettings.COLUMN_LABELED_PREFIX.length())));
            }

            arguments.add("comment_marker=" + python(comment == null ? "" : comment));
            arguments.add("quote_character=" + quoteCharacter(quote));
            arguments.add("missing_marker=" + python(missing == null || "None".equals(missing) ? "" : missing));

            function = "read_tabular";
            this.readsTabular = true;
            javaImport("java.util", "HashSet");
            javaImport("edu.pitt.dbmi.data.reader.tabular", "TabularColumnFileReader");
            javaImport("edu.pitt.dbmi.data.reader.tabular", "TabularDataFileReader");
        } else if (LoadDataSettings.FILE_TYPE_COVARIANCE_LOWER.equals(fileType)
                   || LoadDataSettings.FILE_TYPE_COVARIANCE_SQUARE.equals(fileType)) {
            arguments.add("lower_triangle="
                          + (LoadDataSettings.FILE_TYPE_COVARIANCE_LOWER.equals(fileType) ? "True" : "False"));
            arguments.add("comment_marker=" + python(comment == null ? "" : comment));
            arguments.add("quote_character=" + quoteCharacter(quote));

            function = "read_covariance";
            this.readsCovariance = true;
            javaImport("edu.pitt.dbmi.data.reader.covariance", "LowerCovarianceDataFileReader");
            javaImport("edu.pitt.dbmi.data.reader.covariance", "FullCovarianceDataFileReader");
        } else {
            dataToWriteByHand(box, "The data file type " + quoted(fileType)
                                   + " is not one this script writer knows.");
            return;
        }

        javaImport("java.nio.file", "Paths");
        javaImport("edu.cmu.tetrad.util", "DataConvertUtils");
        javaImport("edu.pitt.dbmi.data.reader", "Delimiter");

        String variable = variable(box);
        heading(box);

        String shape = box.get(StandardDescribers.SHAPE);

        if (shape != null) {
            this.body.append("# In the session these data were: ").append(oneLine(shape)).append(".\n");
        }

        call(variable + " = " + function, arguments);
        this.provides.put(box.getName(), Provides.DATA);
        this.plan.add(new Box(title(box), Fate.WRITTEN, "Reads the file " + quoted(file) + "."));
    }

    /**
     * A data box that takes its data from another box: the box's own class, made from the data of the box above and
     * the settings the box had, where that is all the box needs; and otherwise left to be written by hand, with the
     * reason.
     */
    private void derivedData(BoxDescription box) {
        List<String> parents = this.description.getParentNames(box);
        Class<?> model = PendingDescription.modelClass(DATA_BOX, box.get(BoxDescription.MODEL));
        int dataSets = box.getAll(StandardDescribers.DATA_SET).size();
        String why = null;

        if (model == null) {
            why = "The description does not say what kind of data box this is, or it is a kind this version of "
                  + "Tetrad does not have.";
        } else if (STEPS_BEHIND_A_WINDOW.contains(model.getName())) {
            why = "This kind of box works behind a progress window, which a script does not have.";
        } else if (STEPS_BY_SELECTION.contains(model.getName())) {
            why = "This kind of box works from what was selected by hand in the box it takes its data from, which "
                  + "a description does not hold.";
        } else if (parents.size() != 1) {
            why = "It takes input from " + parents.size() + " boxes, and a step is written only for a box that "
                  + "takes its data from one data box.";
        } else if (this.provides.get(parents.getFirst()) != Provides.DATA) {
            BoxDescription parentBox = this.description.getBox(parents.getFirst());
            why = "It takes input from " + (parentBox == null ? quoted(parents.getFirst()) : title(parentBox))
                  + ", which is not a data box.";
        } else if (!STEPS_WRITTEN.contains(model.getName()) || !importable(model) || !takesOneDataBox(model)) {
            why = "This kind of box is not turned into code.";
        } else if (dataSets != 1) {
            why = dataSets == 0 ? "This box held no data when the session was described."
                    : "This box holds " + dataSets + " data sets, and a script is written only for a box that "
                      + "holds one.";
        }

        // The box's settings, as lines of Python. A setting of a kind that is not written leaves the whole step
        // to be written by hand, since the step run without it would not be the step the session ran.
        List<String> settings = new ArrayList<>();
        List<String> discretize = new ArrayList<>();
        List<String> select = new ArrayList<>();

        for (BoxDescription.Item item : box.getItems()) {
            if (why != null) {
                break;
            }

            if (item instanceof BoxDescription.Block block) {
                if (block.name().startsWith(StandardDescribers.TEXT_BLOCK)) {
                    settings.add("_parameters.set("
                                 + python(block.name().substring(StandardDescribers.TEXT_BLOCK.length()).strip())
                                 + ", " + python(String.join("\n", block.lines())) + ")");
                } else if (block.name().startsWith(StandardDescribers.LIST_BLOCK)
                           || block.name().startsWith(StandardDescribers.VALUES_BLOCK)) {
                    why = "Its setting " + quoted(block.name()) + " is a list, which is not written as code.";
                }

                continue;
            }

            BoxDescription.Field field = (BoxDescription.Field) item;

            switch (field.key()) {
                case BoxDescription.CHANGED -> {
                    BoxDescription.ChangedParameter changed = BoxDescription.ChangedParameter.parse(field.value());
                    String literal = changed == null ? null : parameterLiteral(changed.name(), changed.value());

                    if (literal == null) {
                        why = "Its setting " + quoted(field.value()) + " could not be read, or is not known to "
                              + "this version of Tetrad.";
                    } else {
                        settings.add("_parameters.set(" + python(changed.name()) + ", " + literal + ")  # default "
                                     + oneLine(changed.defaultValue()));
                    }
                }
                case StandardDescribers.SETTING -> {
                    int equals = field.value().indexOf(" = ");
                    String literal = equals <= 0 ? null : settingLiteral(field.value().substring(equals + 3));

                    if (literal == null) {
                        why = "Its setting " + quoted(field.value()) + " is of a kind that is not written as code.";
                    } else {
                        settings.add("_parameters.set(" + python(field.value().substring(0, equals).strip()) + ", "
                                     + literal + ")");
                    }
                }
                case StandardDescribers.DISCRETIZE -> discretize.add(field.value());
                case StandardDescribers.SELECT -> select.add(field.value());
                case StandardDescribers.SPLIT, StandardDescribers.UNMIX, StandardDescribers.VALUES ->
                        why = "Its setting " + quoted(field.key() + ": " + field.value()) + " is of a kind that is "
                              + "not written as code.";
                case StandardDescribers.NOT_DESCRIBED ->
                        why = "Its setting " + quoted(field.value()) + " is of a kind a description cannot hold.";
                default -> {
                    // What the box says of its data, which is a result and not a setting.
                }
            }
        }

        if (why != null) {
            dataToWriteByHand(box, why);
            return;
        }

        String variable = variable(box);
        String parent = this.variables.get(parents.getFirst());
        boolean random = STEPS_RANDOM.contains(model.getName());
        String modelName = oneLine(String.valueOf(box.get(BoxDescription.MODEL)));

        heading(box);
        comment(this.body, "# ", "# ", "The box's own step, " + modelName + ", run on the data from "
                                       + quoted(parents.getFirst()) + " with the settings the box had.");

        if (random) {
            comment(this.body, "# ", "# ", "This step draws random numbers, so the data it gives here are not the "
                                           + "data the session had.");
        }

        javaImport("edu.cmu.tetrad.util", "Parameters");
        javaImport("edu.cmu.tetradapp.model", "DataWrapper");
        this.makesDataBoxes = true;
        this.body.append(variable).append("_parameters = Parameters()\n");

        for (String setting : settings) {
            this.body.append(variable).append(setting).append("\n");
        }

        choices(variable, "discretizationSpecs", "discretization", discretize);
        choices(variable, "selectionSpecs", "selection", select);

        this.body.append(variable).append(" = ").append(local(model)).append("(data_box(").append(parent)
                .append("), ").append(variable).append("_parameters).getSelectedDataModel()\n");
        this.provides.put(box.getName(), Provides.DATA);
        this.plan.add(new Box(title(box), Fate.WRITTEN, "Runs the box's own step on the data from "
                                                        + quoted(parents.getFirst())
                                                        + (random ? ". It draws random numbers, so its data will "
                                                                    + "not be the data the session had." : ".")));
    }

    /**
     * Writes choices a box keeps as objects, from the lines the description gives them in: the lines as text, read
     * by the reader a rebuilt session uses.
     */
    private void choices(String variable, String parameter, String reader, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }

        javaImport("edu.cmu.tetradapp.description", "DescribedChoices");
        this.body.append(variable).append("_parameters.set(").append(python(parameter))
                .append(", DescribedChoices.").append(reader).append("(\"\"\"\\\n");

        for (String line : lines) {
            this.body.append(line.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")).append("\n");
        }

        this.body.append("\"\"\"))\n");
    }

    /**
     * Whether a data box model can be made from one data box and a parameter object.
     */
    private static boolean takesOneDataBox(Class<?> model) {
        try {
            return java.lang.reflect.Modifier.isPublic(model.getConstructor(
                    edu.cmu.tetradapp.model.DataWrapper.class, edu.cmu.tetrad.util.Parameters.class).getModifiers());
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * The value of a setting that has no registered default, as Python that gives Tetrad a value of the same kind.
     * The text is the value as a "Setting" field writes it, which says what kind of value it is.
     *
     * @return the Python text, or null if the value cannot be read or is of a kind that is not written.
     */
    private String settingLiteral(String text) {
        Object value;

        try {
            value = SettingLiteral.read(text);
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (value instanceof String string) {
            return python(string);
        } else if (value instanceof Boolean truth) {
            return truth ? "True" : "False";
        } else if (value instanceof Integer whole) {
            this.usesJInt = true;
            return "JInt(" + whole + ")";
        } else if (value instanceof Long whole) {
            return String.valueOf(whole);
        } else if (value instanceof Double number) {
            if (number.isNaN()) {
                return "float(\"nan\")";
            } else if (number.isInfinite()) {
                return number > 0 ? "float(\"inf\")" : "float(\"-inf\")";
            }

            return String.valueOf(number);
        }

        return null;
    }

    /**
     * A box that gives data to a search and is not written as code: its settings as a comment, and a line that
     * stops the script until the step has been written by hand.
     */
    private void dataToWriteByHand(BoxDescription box, String why) {
        String variable = variable(box);
        heading(box);
        comment(this.body, "# ", "# ", why);
        settingsAsComment(box);
        this.body.append("# Write the step here, leaving its result in a Tetrad data set called ").append(variable)
                .append(",\n# and then delete the line below.\n");
        this.body.append("raise NotImplementedError(")
                .append(python("The step for box \"" + box.getName() + "\" has not been written."))
                .append(")\n");
        this.provides.put(box.getName(), Provides.DATA);
        this.notWritten.add(title(box) + ": to be written by hand. " + why);
        this.plan.add(new Box(title(box), Fate.BY_HAND, why));
    }

    /**
     * A knowledge box: the knowledge, in the text form Tetrad saves it in, read by Tetrad's own reader.
     */
    private void knowledge(BoxDescription box) {
        List<String> lines = box.getBlock(StandardDescribers.KNOWLEDGE_BLOCK);

        heading(box);

        if (lines == null) {
            this.body.append("# The knowledge in this box was empty, so the searches below are given none.\n");
            this.provides.put(box.getName(), Provides.NO_KNOWLEDGE);
            this.plan.add(new Box(title(box), Fate.WRITTEN, "Its knowledge is empty, so no search is given any."));
            return;
        }

        String variable = variable(box);
        javaImport("java.io", "StringReader");
        javaImport("edu.cmu.tetrad.data", "DelimiterType");
        javaImport("edu.cmu.tetrad.data", "SimpleDataLoader");

        this.body.append("# This is the text Tetrad saves knowledge in, so it can be edited here.\n");
        this.body.append(variable).append("_text = \"\"\"\\\n");

        for (String line : lines) {
            this.body.append(line.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")).append("\n");
        }

        this.body.append("\"\"\"\n");
        this.body.append(variable).append(" = SimpleDataLoader.loadKnowledge(StringReader(").append(variable)
                .append("_text), DelimiterType.WHITESPACE, \"//\")\n");
        this.provides.put(box.getName(), Provides.KNOWLEDGE);
        this.plan.add(new Box(title(box), Fate.WRITTEN, "Its knowledge, as text that can be edited."));
    }

    /**
     * A search box: the algorithm with its test and score, the parameters that were changed, the knowledge, and
     * the search itself.
     */
    private void search(BoxDescription box) {
        heading(box);

        List<String> dataParents = new ArrayList<>();
        List<String> knowledgeParents = new ArrayList<>();
        List<String> otherParents = new ArrayList<>();
        boolean knowledgeGiven = false;

        for (String parent : this.description.getParentNames(box)) {
            Provides provided = this.provides.get(parent);

            if (provided == Provides.DATA) {
                dataParents.add(parent);
            } else if (provided == Provides.KNOWLEDGE || provided == Provides.NO_KNOWLEDGE) {
                knowledgeParents.add(parent);
                knowledgeGiven |= provided == Provides.KNOWLEDGE;
            } else {
                otherParents.add(parent);
            }
        }

        String algorithmText = box.get(StandardDescribers.ALGORITHM);
        String testText = box.get(StandardDescribers.TEST);
        String scoreText = box.get(StandardDescribers.SCORE);
        String algorithmCommand = PendingDescription.command(algorithmText);
        String testCommand = PendingDescription.command(testText);
        String scoreCommand = PendingDescription.command(scoreText);

        AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> algorithm
                = algorithmCommand == null ? null : PendingDescription.findAlgorithm(algorithmCommand);
        AnnotatedClass<TestOfIndependence> test
                = testCommand == null ? null : PendingDescription.findTest(testCommand);
        AnnotatedClass<Score> score = scoreCommand == null ? null : PendingDescription.findScore(scoreCommand);

        // Why this search cannot be written, if it cannot.
        String why = null;

        if (algorithm == null) {
            why = algorithmCommand == null
                    ? "No algorithm had been chosen in this box."
                    : "The algorithm " + quoted(algorithmText) + " is not known to this version of Tetrad.";
        } else if (!otherParents.isEmpty()) {
            List<String> titles = new ArrayList<>();

            for (String parent : otherParents) {
                BoxDescription parentBox = this.description.getBox(parent);
                titles.add(parentBox == null ? quoted(parent) : title(parentBox));
            }

            why = "It takes input from " + String.join(" and ", titles)
                  + ", and a script is written only for a search on one data box, with or without a knowledge box.";
        } else if (dataParents.size() != 1) {
            why = "It has " + dataParents.size() + " data boxes as input, and a script is written only for a "
                  + "search on one.";
        } else if (knowledgeParents.size() > 1) {
            why = "It has " + knowledgeParents.size() + " knowledge boxes as input.";
        } else if (testText != null && test == null) {
            why = "The test " + quoted(testText) + " is not known to this version of Tetrad.";
        } else if (scoreText != null && score == null) {
            why = "The score " + quoted(scoreText) + " is not known to this version of Tetrad.";
        } else if (AlgorithmAnnotations.getInstance().requiresIndependenceTest(algorithm.clazz()) && test == null) {
            why = "The algorithm needs a test, and the description names none.";
        } else if (AlgorithmAnnotations.getInstance().requiresScore(algorithm.clazz()) && score == null) {
            why = "The algorithm needs a score, and the description names none.";
        } else if (MultiDataSetAlgorithm.class.isAssignableFrom(algorithm.clazz())) {
            why = "The algorithm is one that searches over several data sets at once.";
        } else if (!importable(algorithm.clazz()) || (test != null && !importable(test.clazz()))
                   || (score != null && !importable(score.clazz()))) {
            why = "One of its classes cannot be imported by name.";
        }

        List<String> settings = new ArrayList<>();

        if (why == null) {
            for (BoxDescription.ChangedParameter changed : box.getChangedParameters()) {
                String literal = parameterLiteral(changed.name(), changed.value());

                if (literal == null) {
                    why = "The value " + quoted(changed.value()) + " of the parameter " + quoted(changed.name())
                          + " could not be read, or the parameter is not known to this version of Tetrad.";
                    break;
                }

                settings.add("_parameters.set(" + python(changed.name()) + ", " + literal + ")  # default "
                             + oneLine(changed.defaultValue()));
            }
        }

        if (why != null) {
            comment(this.body, "# ", "# ", "This search is not turned into code. " + why);
            settingsAsComment(box);
            this.body.append("print(").append(python("Box \"" + box.getName() + "\" was not run: " + why))
                    .append(")\n");
            this.notWritten.add(title(box) + ": not run. " + why);
            this.plan.add(new Box(title(box), Fate.NOT_RUN, why, true));
            return;
        }

        String variable = variable(box);
        String data = this.variables.get(dataParents.getFirst());
        String knowledge = null;

        for (String parent : knowledgeParents) {
            if (this.provides.get(parent) == Provides.KNOWLEDGE) {
                knowledge = this.variables.get(parent);
            }
        }

        this.body.append("# Algorithm: ").append(oneLine(algorithmText)).append(".");
        if (testText != null) this.body.append(" Test: ").append(oneLine(testText)).append(".");
        if (scoreText != null) this.body.append(" Score: ").append(oneLine(scoreText)).append(".");
        this.body.append("\n");

        javaImport("edu.cmu.tetrad.util", "Parameters");
        this.body.append(variable).append("_parameters = Parameters()\n");

        for (String setting : settings) {
            this.body.append(variable).append(setting).append("\n");
        }

        if (box.get(StandardDescribers.SEED) != null) {
            this.body.append("# The seed was not fixed in the session, so if the search uses random numbers its "
                             + "result may\n# differ from run to run.\n");
        }

        boolean testTakesKnowledge = knowledge != null && test != null
                                     && AcceptsKnowledge.class.isAssignableFrom(test.clazz());
        boolean scoreTakesKnowledge = knowledge != null && score != null
                                      && AcceptsKnowledge.class.isAssignableFrom(score.clazz());

        this.body.append(variable).append("_algorithm = ").append(local(algorithm.clazz())).append("()\n");

        if (test != null && AlgorithmAnnotations.getInstance().requiresIndependenceTest(algorithm.clazz())) {
            if (testTakesKnowledge) {
                this.body.append(variable).append("_test = ").append(local(test.clazz())).append("()\n");
                this.body.append(variable).append("_test.setKnowledge(").append(knowledge).append(")\n");
                this.body.append(variable).append("_algorithm.setIndependenceWrapper(").append(variable)
                        .append("_test)\n");
            } else {
                this.body.append(variable).append("_algorithm.setIndependenceWrapper(")
                        .append(local(test.clazz())).append("())\n");
            }
        }

        if (score != null && AlgorithmAnnotations.getInstance().requiresScore(algorithm.clazz())) {
            if (scoreTakesKnowledge) {
                this.body.append(variable).append("_score = ").append(local(score.clazz())).append("()\n");
                this.body.append(variable).append("_score.setKnowledge(").append(knowledge).append(")\n");
                this.body.append(variable).append("_algorithm.setScoreWrapper(").append(variable)
                        .append("_score)\n");
            } else {
                this.body.append(variable).append("_algorithm.setScoreWrapper(").append(local(score.clazz()))
                        .append("())\n");
            }
        }

        if (knowledge != null) {
            if (AcceptsKnowledge.class.isAssignableFrom(algorithm.clazz())) {
                this.body.append(variable).append("_algorithm.setKnowledge(").append(knowledge).append(")\n");
            } else {
                this.body.append("# This algorithm does not take knowledge, so ").append(knowledge)
                        .append(" is not given to it.\n");
            }
        } else if (knowledgeGiven) {
            throw new IllegalStateException("Knowledge was given but has no variable.");
        }

        this.body.append(variable).append("_graph = ").append(variable).append("_algorithm.search(").append(data)
                .append(", ").append(variable).append("_parameters)\n");
        this.body.append("print(").append(python("Box \"" + box.getName() + "\", " + algorithmText + ":"))
                .append(")\n");
        this.body.append("print(").append(variable).append("_graph)\n");
        this.body.append("save_graph(").append(variable).append("_graph, ")
                .append(python(variable + "_graph.txt")).append(")\n");
        this.savesGraphs = true;
        javaImport("java.io", "File");
        javaImport("edu.cmu.tetrad.graph", "GraphSaveLoadUtils");

        StringBuilder remark = new StringBuilder("Runs " + oneLine(algorithmText));
        if (testText != null) remark.append(" with ").append(oneLine(testText));
        if (scoreText != null) remark.append(" with ").append(oneLine(scoreText));
        remark.append(" on ").append(quoted(dataParents.getFirst()));
        if (knowledge != null) remark.append(", with the knowledge");
        remark.append(". Its graph is saved as ").append(variable).append("_graph.txt");
        this.plan.add(new Box(title(box), Fate.WRITTEN, remark.append(".").toString(), true));
    }

    //=================================== PIECES ===================================//

    /**
     * What a box written so far leaves for the boxes after it.
     */
    private enum Provides {
        DATA, KNOWLEDGE, NO_KNOWLEDGE
    }

    /**
     * What becomes of a box when a script is written.
     */
    public enum Fate {

        /**
         * The box is written as code, or for a note as a comment.
         */
        WRITTEN,

        /**
         * The box gives data to a search and is not written; the script stops there until the step is written by
         * hand.
         */
        BY_HAND,

        /**
         * The box is a search that is not written; the script prints a remark in its place and goes on.
         */
        NOT_RUN,

        /**
         * The box is not in the script, since no search that is written depends on it.
         */
        LEFT_OUT
    }

    /**
     * What becomes of one box.
     *
     * @param title  the box's name in quotes, with its type and model.
     * @param fate   what becomes of it.
     * @param remark what is written for it, or why it is not written; may be empty.
     * @param search whether the box is a search box.
     */
    public record Box(String title, Fate fate, String remark, boolean search) {

        /**
         * A box that is not a search box.
         *
         * @param title  the box's name in quotes, with its type and model.
         * @param fate   what becomes of it.
         * @param remark what is written for it, or why it is not written; may be empty.
         */
        public Box(String title, Fate fate, String remark) {
            this(title, fate, remark, false);
        }
    }

    /**
     * What a script of this kind is and is not, whatever the session. Shown to the user before a script is saved.
     */
    public static final List<String> LIMITS = List.of(
            "It reruns searches, and nothing else. Estimators, checks, comparisons, regressions and the like are "
            + "not in it.",
            "It covers a search on one data box holding one data set, with or without one knowledge box.",
            "A box that changes data, such as one that discretizes, selects rows or standardizes, is written as "
            + "the box's own step, run on the data from the box above. That is done for a box that takes its data "
            + "from one data box, holds one data set, and needs nothing but its settings. A step that draws random "
            + "numbers will not give the data the session had.",
            "Other boxes that give data to a search are not written as code: a simulation, a box that takes "
            + "several data boxes as input or holds several data sets, and a box that works from what was "
            + "selected by hand. The script stops at such a step until you write it in Python yourself.",
            "The data are not in the script. It reads each data file by name, from the folder you say, with the "
            + "settings the file was loaded with here. Changes made to the data by hand in Tetrad are not in it.",
            "Each graph is printed, and saved as a text file that a Graph box can load. Running the script again "
            + "replaces those files.",
            "It runs with py-tetrad, on the Tetrad that comes with py-tetrad, which may be older or newer than "
            + "this one.");

    /**
     * A script, with what in the session it does not cover.
     *
     * @param script the Python text.
     * @param notes  what the script does not cover, one remark each; empty if it covers every box.
     * @param boxes  what becomes of each box of the session.
     */
    public record Result(String script, List<String> notes, List<Box> boxes) {

        /**
         * How many boxes meet the given fate.
         *
         * @param fate the fate.
         * @return the number of boxes.
         */
        public int count(Fate fate) {
            return (int) this.boxes.stream().filter(box -> box.fate() == fate).count();
        }

        /**
         * Says, in plain text for the user to read before saving, what the script will hold: which boxes are
         * written as code, which are left to be written by hand, which searches will not run, which boxes are
         * left out, and what a script of this kind is in general.
         *
         * @param sessionName the session's name; may be null.
         * @return the text.
         */
        public String preview(String sessionName) {
            StringBuilder out = new StringBuilder();
            int searches = (int) this.boxes.stream()
                    .filter(box -> box.fate() == Fate.WRITTEN && box.search()).count();
            int allSearches = (int) this.boxes.stream().filter(Box::search).count();

            out.append("A py-tetrad script is a Python program that reruns this session's searches outside "
                       + "Tetrad.\n");
            out.append("For session ").append(sessionName == null || sessionName.isBlank() ? "(unnamed)"
                    : "\"" + sessionName + "\"").append(" the script would hold ").append(searches)
                    .append(" of the session's ").append(allSearches)
                    .append(allSearches == 1 ? " search." : " searches.").append("\n");

            if (searches == 0) {
                out.append("So there is nothing in it to run.\n");
            }

            if (count(Fate.BY_HAND) > 0) {
                out.append("It would not run to the end as saved: ").append(count(Fate.BY_HAND))
                        .append(count(Fate.BY_HAND) == 1 ? " step is" : " steps are")
                        .append(" left for you to write in Python.\n");
            }

            section(out, Fate.WRITTEN, "WRITTEN AS CODE");
            section(out, Fate.BY_HAND, "LEFT FOR YOU TO WRITE BY HAND. The script stops at each of these until "
                                       + "you write the step.");
            section(out, Fate.NOT_RUN, "SEARCHES THE SCRIPT WILL NOT RUN");
            section(out, Fate.LEFT_OUT, "LEFT OUT, since no search in the script depends on them");

            out.append("\nWHAT A SCRIPT OF THIS KIND IS, FOR ANY SESSION\n");

            for (String limit : LIMITS) {
                out.append("  - ").append(limit).append("\n");
            }

            return out.toString();
        }

        private void section(StringBuilder out, Fate fate, String heading) {
            if (count(fate) == 0) {
                return;
            }

            out.append("\n").append(heading).append("\n");

            for (Box box : this.boxes) {
                if (box.fate() == fate) {
                    out.append("  - ").append(box.title());

                    if (!box.remark().isEmpty()) {
                        out.append(": ").append(box.remark());
                    }

                    out.append("\n");
                }
            }
        }
    }

    private void heading(BoxDescription box) {
        List<String> parents = this.description.getParentNames(box);
        this.body.append("\n# ---- Box ").append(title(box));

        if (!parents.isEmpty()) {
            this.body.append(", from ").append(oneLine(String.join(" and ", parents)));
        }

        this.body.append(" ----\n");
    }

    /**
     * The box's name in quotes, followed by its type and, where it says more, its model.
     */
    private static String title(BoxDescription box) {
        String type = box.get(BoxDescription.TYPE);
        String model = box.get(BoxDescription.MODEL);
        StringBuilder title = new StringBuilder(quoted(box.getName()));

        if (type != null) {
            title.append(" (").append(oneLine(type));

            if (model != null && !model.equals(type) && !PendingDescription.NO_MODEL.equals(model)) {
                title.append(": ").append(oneLine(model));
            }

            title.append(")");
        }

        return title.toString();
    }

    /**
     * The settings a description gives for a box, as comment lines.
     */
    private void settingsAsComment(BoxDescription box) {
        List<String> lines = new ArrayList<>();

        for (BoxDescription.Item item : box.getItems()) {
            if (item instanceof BoxDescription.Field field) {
                String key = field.key();

                if (!key.equals(BoxDescription.TYPE) && !key.equals(BoxDescription.MODEL)
                    && !key.equals(BoxDescription.PARENTS) && !key.equals(BoxDescription.LOCATION)) {
                    lines.add(key + ": " + field.value());
                }
            } else if (item instanceof BoxDescription.Block block) {
                lines.add(block.name() + ", " + block.lines().size()
                          + (block.lines().size() == 1 ? " line" : " lines") + "; see the session description");
            }
        }

        if (lines.isEmpty()) {
            return;
        }

        this.body.append("# What the session description says of it:\n");

        for (String line : lines) {
            comment(this.body, "#     ", "#         ", line);
        }
    }

    /**
     * Writes text as a comment, broken between words so that no line is much over ninety characters.
     *
     * @param first what the first line starts with.
     * @param rest  what each later line starts with.
     */
    private static void comment(StringBuilder out, String first, String rest, String text) {
        StringBuilder line = new StringBuilder(first);
        boolean empty = true;

        for (String word : oneLine(text).strip().split(" +")) {
            if (!empty && line.length() + 1 + word.length() > 92) {
                out.append(line).append("\n");
                line = new StringBuilder(rest);
                empty = true;
            }

            if (!empty) {
                line.append(" ");
            }

            line.append(word);
            empty = false;
        }

        out.append(line).append("\n");
    }

    /**
     * Writes a call with one argument to a line when they do not fit on one.
     */
    private void call(String start, List<String> arguments) {
        String oneLine = start + "(" + String.join(", ", arguments) + ")";

        if (oneLine.length() <= 100) {
            this.body.append(oneLine).append("\n");
            return;
        }

        this.body.append(start).append("(\n");

        for (String argument : arguments) {
            this.body.append("    ").append(argument).append(",\n");
        }

        this.body.append(")\n");
    }

    /**
     * The Python variable for a box: its name in lower case, with anything that cannot stand in a Python name
     * made an underscore, and made different from every name already taken.
     */
    private String variable(BoxDescription box) {
        String existing = this.variables.get(box.getName());

        if (existing != null) {
            return existing;
        }

        StringBuilder base = new StringBuilder();

        for (char c : box.getName().toLowerCase().toCharArray()) {
            base.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
        }

        String name = base.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");

        if (name.isEmpty() || Character.isDigit(name.charAt(0))) {
            name = "box_" + name;
        }

        if (RESERVED.contains(name)) {
            name = name + "_box";
        }

        String unique = name;

        for (int i = 2; this.variablesTaken.contains(unique); i++) {
            unique = name + "_" + i;
        }

        this.variablesTaken.add(unique);
        this.variables.put(box.getName(), unique);
        return unique;
    }

    private void javaImport(String javaPackage, String simpleName) {
        this.javaImports.computeIfAbsent(javaPackage, k -> new TreeSet<>()).add(simpleName);
    }

    /**
     * Whether a class can be imported in Python by its package and simple name.
     */
    private static boolean importable(Class<?> clazz) {
        return clazz.getEnclosingClass() == null && !clazz.getPackageName().isEmpty();
    }

    /**
     * The name an algorithm, test or score class goes by in the script, importing it if it has not been.
     */
    private String local(Class<?> clazz) {
        String existing = this.localNames.get(clazz.getName());

        if (existing != null) {
            return existing;
        }

        String simple = clazz.getSimpleName();
        String name = simple;

        for (int i = 2; this.localNames.containsValue(name); i++) {
            name = simple + "_" + i;
        }

        this.localNames.put(clazz.getName(), name);
        javaImport(clazz.getPackageName(), name.equals(simple) ? simple : simple + " as " + name);
        return name;
    }

    /**
     * A parameter's value as Python that gives Tetrad a value of the parameter's own kind. A whole-number
     * parameter is written with JInt, since a bare Python whole number reaches Java as a long.
     *
     * @return the Python text, or null if the parameter is not registered or the value cannot be read.
     */
    private String parameterLiteral(String name, String value) {
        ParamDescriptions registry = ParamDescriptions.getInstance();

        if (!registry.getNames().contains(name) || value == null) {
            return null;
        }

        Object defaultValue = registry.get(name).getDefaultValue();
        String text = value.strip();

        try {
            if (defaultValue instanceof Integer) {
                this.usesJInt = true;
                return "JInt(" + Integer.parseInt(text) + ")";
            } else if (defaultValue instanceof Long) {
                return String.valueOf(Long.parseLong(text));
            } else if (defaultValue instanceof Double) {
                double d = Double.parseDouble(text);

                if (Double.isNaN(d)) {
                    return "float(\"nan\")";
                } else if (Double.isInfinite(d)) {
                    return d > 0 ? "float(\"inf\")" : "float(\"-inf\")";
                }

                return text.matches("-?[0-9]+") ? text + ".0" : text;
            } else if (defaultValue instanceof Boolean) {
                if (text.equals("true")) return "True";
                if (text.equals("false")) return "False";
                return null;
            } else if (defaultValue instanceof String) {
                return python(value);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        return null;
    }

    /**
     * The name of the delimiter constant for a delimiter as the Load Data dialog names it.
     */
    private static String delimiter(String described) {
        if (described == null) {
            return null;
        }

        return switch (described) {
            case "Whitespace" -> "WHITESPACE";
            case "Comma" -> "COMMA";
            case "Space" -> "SPACE";
            case "Tab" -> "TAB";
            case "Colon" -> "COLON";
            case "Semicolon" -> "SEMICOLON";
            case "Pipe" -> "PIPE";
            default -> null;
        };
    }

    private static String quoteCharacter(String described) {
        return described == null || described.length() != 1 ? "None" : python(described);
    }

    /**
     * Text as a Python string: in double quotes, or in single quotes if the text has double quotes in it and no
     * single ones, which reads better.
     */
    static String python(String text) {
        String value = text == null ? "" : text;
        char quote = value.indexOf('"') >= 0 && value.indexOf('\'') < 0 ? '\'' : '"';
        StringBuilder out = new StringBuilder().append(quote);

        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append(quote == '"' ? "\\\"" : "\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\x%02x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        return out.append(quote).toString();
    }

    private static String quoted(String text) {
        return "\"" + oneLine(text) + "\"";
    }

    /**
     * Text made safe for a comment: on one line.
     */
    private static String oneLine(String text) {
        return text == null ? "" : text.replace("\r", " ").replace("\n", " ");
    }
}
