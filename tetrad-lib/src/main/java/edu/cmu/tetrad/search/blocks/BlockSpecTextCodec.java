package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text codec for BlockSpec: - Supports LHS names with optional rank:  L1,  L1(2) - Validates duplicate block names
 * (ERROR) - Warns if #index refers to a named variable (prefer name) - Preserves user-supplied names and ranks; does
 * not reorder blocks - Sorts members within each block (stable) and removes within-line duplicates
 */
public final class BlockSpecTextCodec {

    // LHS: name with optional "(rank)"
    private static final Pattern LHS = Pattern.compile("^(?<name>[A-Za-z_][A-Za-z0-9_\\-.]*)(?:\\((?<rank>\\d+)\\))?$");
    // Whole line:  optional LHS ":" then RHS
    private static final Pattern LINE =
            Pattern.compile("^\\s*(?:(?<lhs>[A-Za-z_][A-Za-z0-9_\\-.]*(?:\\(\\d+\\))?)\\s*:)?" +
                            "\\s*(?<rhs>.*?)\\s*$");
    // RHS tokens: "quoted", #123, or bare
    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|#(\\d+)|([^,\\s]+)");

    private BlockSpecTextCodec() {
    }

    /**
     * Parses an input textual representation of block specifications into a structured format.
     * <p>
     * The input text is processed line-by-line to construct a block specification and validate the provided structure.
     * Lines in the text that are either malformed, contain duplicates, or reference unknown variables are recorded as
     * issues.
     *
     * @param text the input text defining the block specification
     * @param ds   the dataset providing variable names and their corresponding indices
     * @return a ParseResult record consisting of a constructed BlockSpec object and a list of encountered issues
     */
    public static ParseResult parse(String text, DataSet ds) {
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> blockVars = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();

        // name -> index
        Map<String, Integer> nameToIdx = new HashMap<>();
        for (int i = 0; i < ds.getNumColumns(); i++) {
            nameToIdx.put(ds.getVariable(i).getName(), i);
        }

        // Track duplicates across blocks, and block-name collisions
        Set<Integer> usedAcrossBlocks = new HashSet<>();
        Set<String> seenBlockNames = new HashSet<>();

        int latentAutoCount = 1;

        String[] lines = text.split("\\R", -1);
        for (int ln = 0; ln < lines.length; ln++) {
            String raw = lines[ln];
            String s = raw.strip();
            if (s.isEmpty() || s.startsWith("%")) continue; // comment or blank

            Matcher lm = LINE.matcher(s);
            if (!lm.matches()) {
                issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Malformed line", s));
                continue;
            }

            String lhsRaw = lm.group("lhs"); // may be null
            String rhs = lm.group("rhs");

            String blockName = null;
            int blockRank = 1;

            if (lhsRaw != null) {
                Matcher lhsMatch = LHS.matcher(lhsRaw);
                if (!lhsMatch.matches()) {
                    issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Bad block header", lhsRaw));
                    continue;
                }
                blockName = lhsMatch.group("name");
                String rankStr = lhsMatch.group("rank");
                if (rankStr != null) {
                    try {
                        blockRank = Integer.parseInt(rankStr);
                        if (blockRank < 1) {
                            issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Rank must be >= 1", rankStr));
                            blockRank = 1;
                        }
                    } catch (NumberFormatException nfe) {
                        issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Bad rank", rankStr));
                        blockRank = 1;
                    }
                }
                // Duplicate block name check
                if (!seenBlockNames.add(blockName)) {
                    issues.add(new Issue(ln + 1, 1, Severity.ERROR,
                            "Duplicate block variable (already used in another block)", blockName));
                }
                // Must have RHS for a named block
                if (rhs == null || rhs.isBlank()) {
                    issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Missing block members after ':'", s));
                    continue;
                }
            } else {
                // Singleton line: entire trimmed line is the token
                if (rhs == null || rhs.isBlank()) {
                    // s is the token itself
                    rhs = s;
                }
            }

            // Collect tokens from RHS (or singleton)
            List<String> tokens = new ArrayList<>();
            Matcher t = TOKEN.matcher(rhs);
            while (t.find()) {
                String q = t.group(1);
                String idx = t.group(2);
                String bare = t.group(3);
                if (q != null) tokens.add(q);
                else if (idx != null) tokens.add("#" + idx);
                else if (bare != null) tokens.add(bare);
            }
            if (lhsRaw == null && tokens.isEmpty() && !s.isEmpty()) {
                // singleton line with non-empty s but no parsed token → treat s as a bare token
                tokens.add(s);
            }

            // Build this block's member indices, skipping within-line duplicates
            List<Integer> block = new ArrayList<>();
            Set<Integer> seenInThisLine = new HashSet<>();
            for (String tok : tokens) {
                Integer idx = null;
                if (tok.startsWith("#")) {
                    try {
                        int k = Integer.parseInt(tok.substring(1));
                        if (k < 0 || k >= ds.getNumColumns()) {
                            issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Index out of range", tok));
                        } else {
                            // Warn if #k corresponds to a named variable
                            String varName = ds.getVariable(k).getName();
                            issues.add(new Issue(ln + 1, 1, Severity.WARNING,
                                    "Index " + tok + " refers to variable '" + varName + "'; prefer name.", tok));
                            idx = k;
                        }
                    } catch (NumberFormatException nfe) {
                        issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Bad index token", tok));
                    }
                } else {
                    Integer k = nameToIdx.get(tok);
                    if (k == null) {
                        issues.add(new Issue(ln + 1, 1, Severity.ERROR, "Unknown variable", tok));
                    } else {
                        idx = k;
                    }
                }
                if (idx != null) {
                    if (!seenInThisLine.add(idx)) {
                        // within-line duplicate → warn, but don't add twice
                        issues.add(new Issue(ln + 1, 1, Severity.WARNING,
                                "Duplicate variable within block", ds.getVariable(idx).getName()));
                        continue;
                    }
                    if (!usedAcrossBlocks.add(idx)) {
                        // duplicate across blocks → warn and skip
                        issues.add(new Issue(ln + 1, 1, Severity.WARNING,
                                "Duplicate variable (already in another block)", ds.getVariable(idx).getName()));
                        continue;
                    }
                    block.add(idx);
                }
            }

            if (block.isEmpty()) {
                issues.add(new Issue(ln + 1, 1, Severity.WARNING, "Empty block", s));
                continue;
            }

            // Sort members within block (stable) so downstream is deterministic
            Collections.sort(block);

            // Determine representative node name & rank
            if (block.size() == 1 && (blockName == null || blockRank == 1)) {
                // singleton → observed node, rank implicitly 1
                blockVars.add(ds.getVariable(block.get(0)));
                ranks.add(1);
            } else {
                String latentName = (blockName != null) ? blockName : ("L" + latentAutoCount++);
                ContinuousVariable L = new ContinuousVariable(latentName);
                L.setNodeType(NodeType.LATENT);
                blockVars.add(L);
                ranks.add(blockRank);
            }

            blocks.add(block);
        }

        // Build spec preserving names and ranks (no global canonicalization here)
        BlockSpec spec = new BlockSpec(ds, blocks, blockVars, ranks);
        return new ParseResult(spec, issues);
    }

    /**
     * Formats the given BlockSpec object into a textual representation.
     * <p>
     * Generates a string description of the block specification, including the names of variables and latent nodes, and
     * their ranks. The format includes line-by-line grouping of variables associated with blocks, using the structure
     * of the given BlockSpec object.
     *
     * @param spec the BlockSpec object to be formatted
     * @return a string representing the formatted textual description of the block specification
     */
    public static String format(BlockSpec spec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spec.blocks().size(); i++) {
            List<Integer> b = spec.blocks().get(i);
            Node rep = spec.blockVariables().get(i);
            int r = spec.ranks().get(i);

            if (b.size() == 1 && r == 1) {
                sb.append(spec.dataSet().getVariable(b.get(0)).getName());
            } else {
                // Rep name (latent) with optional "(rank)"
                sb.append(rep.getName());
                if (r > 1) sb.append('(').append(r).append(')');
                sb.append(": ");
                for (int j = 0; j < b.size(); j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(spec.dataSet().getVariable(b.get(j)).getName());
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * Represents the severity level of a condition or situation.
     * <p>
     * The severity levels can either be ERROR, indicating a critical issue, or WARNING, indicating a less critical
     * issue that might still require attention.
     */
    public enum Severity {

        /**
         * Represents a critical issue that requires immediate attention.
         * <p>
         * ERROR is used to indicate situations where a significant problem has occurred that needs to be addressed to
         * ensure proper functionality or operation of the system.
         */
        ERROR,

        /**
         * Represents a less critical issue that might still require attention.
         * <p>
         * WARNING indicates a condition or situation that does not prevent the system from functioning but could
         * potentially lead to issues or requires awareness to maintain optimal operation.
         */
        WARNING
    }

    /**
     * Represents an issue encountered during the parsing or processing of block specifications.
     * <p>
     * An issue captures specific details related to a problem found in the input textual representation of block
     * specifications. This includes the line and column where the issue occurred, its severity, a descriptive message,
     * and the token associated with the issue.
     * <p>
     * The purpose of this class is to provide a structured way to convey problems that arise during parsing or
     * validation, allowing for clear identification and resolution of such issues.
     *
     * @param line     The line number where the issue occurred. Lines are 1-based indexed.
     * @param col      The column number where the issue occurred. Columns are 1-based indexed.
     * @param severity The severity level of the issue, indicating whether it is an error or a warning.
     * @param message  A descriptive message detailing the nature of the issue.
     * @param token    The specific token or element in the input that caused the issue.
     */
    public record Issue(int line, int col, Severity severity, String message, String token) {
    }

    /**
     * Represents the result of parsing a textual representation of a block specification.
     * <p>
     * The ParseResult record encapsulates two pieces of information: - A BlockSpec object, which represents the
     * constructed block specification after parsing. - A list of issues encountered during the parsing process, which
     * may include errors such as malformed lines, duplicate entries, or references to unknown variables.
     * <p>
     * Instances of this record are typically produced by the `parse` method and contain the structured output along
     * with any issues identified during input processing.
     *
     * @param spec   the parsed block specification represented as a BlockSpec object
     * @param issues a list of issues encountered during parsing
     */
    public record ParseResult(BlockSpec spec, List<Issue> issues) {
    }
}