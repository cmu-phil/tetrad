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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;

/**
 * Provides static methods for saving data to files.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class DataWriter {

    /**
     * Prevents instantiation.
     */
    private DataWriter() {
    }

    /**
     * Writes a dataset to file. The dataset may have continuous and/or discrete columns. Note that <code>out</code> is
     * not closed by this method, so the close method on <code>out</code> will need to be called externally.
     *
     * @param dataSet   The data set to save.
     * @param out       The writer to write the output to.
     * @param separator The character separating fields, usually '\t' or ','.
     * @throws java.io.IOException If there is some problem dealing with the writer.
     */
    public static void writeRectangularData(DataSet dataSet, Writer out, char separator) throws IOException {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        StringBuilder buf = new StringBuilder();

        for (int col = 0; col < dataSet.getNumColumns(); col++) {
            String name = dataSet.getVariable(col).getName();

            if (name.trim().equals("")) {
                name = "C" + (col - 1);
            }

            buf.append(name);

            if (col < dataSet.getNumColumns() - 1) {
                buf.append(separator);
            }
        }

        for (int row = 0; row < dataSet.getNumRows(); row++) {
            buf.append("\n");

            for (int col = 0; col < dataSet.getNumColumns(); col++) {
                Node variable = dataSet.getVariable(col);

                if (variable instanceof ContinuousVariable) {
                    double value = dataSet.getDouble(row, col);

                    if (ContinuousVariable.isDoubleMissingValue(value)) {
                        buf.append("*");
                    } else {
                        buf.append(nf.format(value));
                    }

                    if (col < dataSet.getNumColumns() - 1) {
                        buf.append(separator);
                    }
                } else if (variable instanceof DiscreteVariable) {
                    Object obj = dataSet.getObject(row, col);
                    String val = ((obj == null) ? "" : obj.toString());

                    buf.append(val);

                    if (col < dataSet.getNumColumns() - 1) {
                        buf.append(separator);
                    }
                }
            }
        }

        buf.append("\n");
        out.write(buf.toString());
        out.close();
    }


    /**
     * Writes the lower triangle of a covariance matrix to file.
     *
     * @param out       The writer to write the output to.
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param nf        a {@link java.text.NumberFormat} object
     */
    public static void writeCovMatrixLowerTriangle(ICovarianceMatrix covMatrix, PrintWriter out, NumberFormat nf) {
        out.println(covMatrix.getSampleSize());

        List<String> variables = covMatrix.getVariableNames();
        int numVars = variables.size();

        int varCount = 0;
        for (String variable : variables) {
            varCount++;
            if (varCount < numVars) {
                out.print(variable);
                out.print("\t");
            } else {
                out.println(variable);
            }
        }

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                double value = covMatrix.getValue(i, j);
                if (Double.isNaN(value)) {
                    out.print("*");
                } else {
                    out.print(nf.format(value));
                }

                out.print((i < j) ? "\t" : "\n");
            }
        }

        out.flush();
        out.close();
    }

    /**
     * Writes the covariance matrix to file as a square matrix.
     *
     * @param out       The writer to write the output to.
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @param nf        a {@link java.text.NumberFormat} object
     */
    public static void writeCovMatrixSquare(ICovarianceMatrix covMatrix, PrintWriter out, NumberFormat nf) {
        out.println(covMatrix.getSampleSize());

        List<String> variables = covMatrix.getVariableNames();
        int numVars = variables.size();

        int varCount = 0;
        for (String variable : variables) {
            varCount++;
            if (varCount < numVars) {
                out.print(variable);
                out.print("\t");
            } else {
                out.println(variable);
            }
        }

        // Now write a full numVars x numVars matrix, one row per line
        for (int i = 0; i < numVars; i++) {
            for (int j = 0; j < numVars; j++) {
                double value = covMatrix.getValue(i, j);
                if (Double.isNaN(value)) {
                    out.print("*");
                } else {
                    out.print(nf.format(value));
                }

                // Tab between entries, newline at end of row
                out.print((j < numVars - 1) ? "\t" : "\n");
            }
        }

        out.flush();
        out.close();
    }

    /**
     * Saves knowledge in the tetrad2 text format that {@code SimpleDataLoader.loadKnowledge}
     * reads. If every variable name mentioned by the knowledge is free of whitespace and commas,
     * the format is the original whitespace-delimited one, unchanged. If some mentioned name
     * contains whitespace -- which the whitespace-delimited format cannot represent -- the
     * comma-delimited variant is written instead; see
     * {@link #saveKnowledge(Knowledge, Writer, boolean)}.
     *
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge} object
     * @param out       a {@link java.io.Writer} object
     * @throws java.io.IOException if any.
     */
    public static void saveKnowledge(Knowledge knowledge, Writer out) throws IOException {
        saveKnowledge(knowledge, out, needsCommaDelimiter(knowledge));
    }

    /**
     * Saves knowledge in the tetrad2 text format, choosing the delimiter explicitly. In the
     * comma-delimited variant the header line is {@code /knowledge comma}, which the loader reads
     * as an instruction to split names on commas rather than whitespace, so names containing
     * spaces survive the round trip; in tier and group lines the names are separated by commas,
     * and in forbiddirect and requiredirect lines the from- and to-names are separated by a
     * comma. A name containing a comma is written in double quotes. Names may not contain the
     * double-quote character in either variant.
     *
     * @param knowledge         a {@link edu.cmu.tetrad.data.Knowledge} object
     * @param out               a {@link java.io.Writer} object
     * @param useCommaDelimiter true to write the comma-delimited variant
     * @throws java.io.IOException if any.
     */
    public static void saveKnowledge(Knowledge knowledge, Writer out, boolean useCommaDelimiter)
            throws IOException {
        StringBuilder buf = new StringBuilder();
        buf.append("/knowledge");

        if (useCommaDelimiter) {
            buf.append(" comma");
        }

        buf.append("\naddtemporal\n");

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            String forbiddenWithin = knowledge.isTierForbiddenWithin(i) ? "*" : "";
            String onlyCanCauseNextTier = knowledge.isOnlyCanCauseNextTier(i) ? "-" : "";
            List<String> tier = knowledge.getTier(i);

            if (!tier.isEmpty()) {
                buf.append("\n").append(i).append(forbiddenWithin).append(onlyCanCauseNextTier).append(" ");
                buf.append(" ");
                buf.append(joinNames(tier, useCommaDelimiter));
            }
        }

        for (KnowledgeTierStructure structure : knowledge.getTierStructures()) {
            buf.append("\n\ntierstructure ").append(structure.getName()).append("\n");

            for (int i = 0; i < structure.getNumTiers(); i++) {
                String forbiddenWithin = structure.isTierForbiddenWithin(i) ? "*" : "";
                List<String> tier = structure.getTier(i);

                if (!tier.isEmpty()) {
                    buf.append("\n").append(i).append(forbiddenWithin).append("  ");
                    buf.append(joinNames(tier, useCommaDelimiter));
                }
            }
        }

        appendGroups(buf, knowledge, KnowledgeGroup.FORBIDDEN, "forbiddengroup", useCommaDelimiter);
        appendGroups(buf, knowledge, KnowledgeGroup.REQUIRED, "requiredgroup", useCommaDelimiter);

        buf.append("\n\nforbiddirect");

        for (KnowledgeEdge pair : knowledge.getListOfExplicitlyForbiddenEdges()) {
            String from = pair.getFrom();
            String to = pair.getTo();

            if (knowledge.isForbiddenByTiers(from, to)) {
                continue;
            }

            appendEdge(buf, from, to, useCommaDelimiter);
        }

        buf.append("\n\nrequiredirect");

        for (Iterator<KnowledgeEdge> i = knowledge.requiredEdgesIterator(); i.hasNext(); ) {
            KnowledgeEdge pair = i.next();
            String from = pair.getFrom();
            String to = pair.getTo();

            if (knowledge.isRequiredByGroups(from, to)) {
                continue;
            }

            appendEdge(buf, from, to, useCommaDelimiter);
        }

        out.write(buf.toString());
        out.flush();
    }

    /**
     * True iff some variable name the knowledge mentions (in a tier, a tier structure, a group, or
     * an explicit rule) contains whitespace, so that the whitespace-delimited format cannot
     * represent it.
     */
    private static boolean needsCommaDelimiter(Knowledge knowledge) {
        List<String> names = new java.util.ArrayList<>();

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            names.addAll(knowledge.getTier(i));
        }

        for (KnowledgeTierStructure structure : knowledge.getTierStructures()) {
            names.addAll(structure.getVariables());
        }

        for (KnowledgeGroup group : knowledge.getKnowledgeGroups()) {
            names.addAll(group.getFromVariables());
            names.addAll(group.getToVariables());
        }

        for (KnowledgeEdge edge : knowledge.getListOfExplicitlyForbiddenEdges()) {
            names.add(edge.getFrom());
            names.add(edge.getTo());
        }

        for (Iterator<KnowledgeEdge> i = knowledge.requiredEdgesIterator(); i.hasNext(); ) {
            KnowledgeEdge edge = i.next();
            names.add(edge.getFrom());
            names.add(edge.getTo());
        }

        return names.stream().anyMatch(name -> name.chars().anyMatch(Character::isWhitespace));
    }

    /**
     * Appends the groups of the given type as a section: the section header, then for each group
     * its from-names on one line and its to-names on the next, which is how the loader reads them
     * back. Groups with an empty side impose nothing and are skipped, since a blank line cannot be
     * written (the loader skips blank lines, which would misalign the from/to pairing).
     */
    private static void appendGroups(StringBuilder buf, Knowledge knowledge, int type,
                                     String sectionName, boolean useCommaDelimiter) {
        List<KnowledgeGroup> groups = knowledge.getKnowledgeGroups().stream()
                .filter(g -> g.getType() == type)
                .filter(g -> !g.getFromVariables().isEmpty() && !g.getToVariables().isEmpty())
                .toList();

        if (groups.isEmpty()) {
            return;
        }

        buf.append("\n\n").append(sectionName).append("\n");

        for (KnowledgeGroup group : groups) {
            List<String> from = group.getFromVariables().stream().sorted().toList();
            List<String> to = group.getToVariables().stream().sorted().toList();
            buf.append("\n").append(joinNames(from, useCommaDelimiter));
            buf.append("\n").append(joinNames(to, useCommaDelimiter));
        }
    }

    private static void appendEdge(StringBuilder buf, String from, String to,
                                   boolean useCommaDelimiter) {
        if (useCommaDelimiter) {
            buf.append("\n").append(quoteIfNeeded(from)).append(", ").append(quoteIfNeeded(to));
        } else {
            buf.append("\n").append(from).append(" ").append(to);
        }
    }

    private static String joinNames(List<String> names, boolean useCommaDelimiter) {
        if (useCommaDelimiter) {
            StringBuilder b = new StringBuilder();

            for (int i = 0; i < names.size(); i++) {
                if (i > 0) b.append(", ");
                b.append(quoteIfNeeded(names.get(i)));
            }

            return b.toString();
        } else {
            return String.join(" ", names);
        }
    }

    /**
     * In the comma-delimited variant, a name containing a comma is wrapped in double quotes so the
     * tokenizer does not split it.
     */
    private static String quoteIfNeeded(String name) {
        if (name.indexOf(',') >= 0) {
            return "\"" + name + "\"";
        }

        return name;
    }
}






