package edu.cmu.tetradapp.editor.ind_facts;

import edu.cmu.tetrad.util.TMath;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * IndFacts DSL v0.1 parser + expander.
 *
 * - One template per line
 * - Unordered pair semantics
 * - Enforce Z ∩ {x,y} = ∅
 *   * explicit violations => parse error
 *   * template expansions => skipped invalid
 */
public final class IndFactsDsl {

    // ---------- Public result types ----------

    public record ParseError(int lineIndex0, int startOffset, int endOffset, String message) {}

    public record PreviewLineStats(int lineIndex0, int expanded, int kept, int skippedInvalid, boolean warningZeroKept) {}

    public record PreviewResult(
            List<ParseError> errors,
            List<PreviewLineStats> lineStats,
            int totalExpanded,
            int totalKept,
            int totalSkippedInvalid,
            List<String> firstFacts // canonical strings
    ) {}

    // ---------- Template model ----------

    private sealed interface XYSel permits XYSel.Named, XYSel.Placeholders {
        record Named(String x, String y) implements XYSel {}
        record Placeholders() implements XYSel {}
    }

    private sealed interface PoolSpec permits PoolSpec.All, PoolSpec.Vars, PoolSpec.Not, PoolSpec.Adj {
        record All() implements PoolSpec {}
        record Vars(Set<String> vars) implements PoolSpec {}
        record Not(Set<String> vars) implements PoolSpec {}
        record Adj() implements PoolSpec {}
    }

    private record Template(
            int lineIndex0,
            String rawLine,
            XYSel xySel,
            Set<String> baseZ,
            GenSpec gen,
            PoolSpec pool
    ) {}

    private record GenSpec(int kMin, int kMax) {
        static GenSpec none() { return new GenSpec(0, -1); } // sentinel: no generator
        boolean isNone() { return kMax < kMin; }
    }

    // ---------- Parsing ----------

    private static final Pattern COMMENT = Pattern.compile("^\\s*#.*$");
    private static final Pattern SEP = Pattern.compile("\\s*(?:_\\|\\|_|⟂|indep)\\s*");
    private static final Pattern KGEN = Pattern.compile("\\+\\s*k\\s*\\(\\s*(\\d+)\\s*\\.\\.\\s*(\\d+)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBSETS = Pattern.compile("\\+\\s*subsets\\s*\\(\\s*max\\s*=\\s*(\\d+)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern POOL = Pattern.compile("pool\\s*\\(\\s*(.+?)\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE = Pattern.compile("\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\.\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}");

    /**
     * Parse and preview-expand given DSL text.
     *
     * @param text raw editor text
     * @param allVars list of variable names
     * @param adjMap adjacency for pool(adj); keys/values are variable names. May be null.
     * @param previewLimit max number of concrete facts to include in preview list
     */
    public static PreviewResult preview(String text, List<String> allVars, Map<String, Set<String>> adjMap, int previewLimit) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(allVars);

        List<String> lines = Arrays.asList(text.split("\\R", -1));
        List<ParseError> errors = new ArrayList<>();
        List<Template> templates = new ArrayList<>();

        // Build offset map for squiggles
        int[] lineStartOffsets = new int[lines.size()];
        {
            int off = 0;
            for (int i = 0; i < lines.size(); i++) {
                lineStartOffsets[i] = off;
                // +1 for '\n' except maybe last; OK for highlight bounds
                off += lines.get(i).length() + 1;
            }
        }

        // Parse each line into a Template or error.
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();
            if (line.isEmpty() || COMMENT.matcher(line).matches()) continue;

            int baseOffset = lineStartOffsets[i];

            // Strip trailing pool(...) if present
            PoolSpec poolSpec = new PoolSpec.All();
            Matcher pm = POOL.matcher(line);
            if (pm.find()) {
                String inside = pm.group(1).trim();
                poolSpec = parsePool(inside, allVars, adjMap, i, baseOffset, errors, raw);
                // remove pool(...) from line text for further parsing
                line = line.substring(0, pm.start()).trim();
            }

            // Split X/Y vs Z-part using independence separator
            Matcher sepM = SEP.matcher(line);
            if (!sepM.find()) {
                errors.add(spanError(i, baseOffset, raw, "Missing independence separator (_||_, ⟂, indep)."));
                continue;
            }

            String left = line.substring(0, sepM.start()).trim();
            String right = line.substring(sepM.end()).trim();

            String xTok = left;
            String yPartAndMore = right;

            // parse Y and optional conditioning " | ..."
            String yTok;
            String zAndMore = "";

            int bar = yPartAndMore.indexOf('|');
            if (bar >= 0) {
                yTok = yPartAndMore.substring(0, bar).trim();
                zAndMore = yPartAndMore.substring(bar + 1).trim();
            } else {
                yTok = yPartAndMore.trim();
            }

            if (xTok.isEmpty() || yTok.isEmpty()) {
                errors.add(spanError(i, baseOffset, raw, "Missing X or Y token."));
                continue;
            }

            XYSel xySel;
            boolean xPh = xTok.equals("?x") || xTok.equals("?") || xTok.equalsIgnoreCase("?X");
            boolean yPh = yTok.equals("?y") || yTok.equals("?") || yTok.equalsIgnoreCase("?Y");
            if (xPh || yPh) {
                // v0.1: require both placeholders if using placeholders
                if (!(xPh && yPh)) {
                    errors.add(spanError(i, baseOffset, raw, "If using placeholders, use both ?x and ?y (unordered pairs)."));
                    continue;
                }
                xySel = new XYSel.Placeholders();
            } else {
                // validate names now
                if (!allVars.contains(xTok)) {
                    errors.add(tokenError(i, baseOffset, raw, xTok, "Unknown variable: " + xTok));
                    continue;
                }
                if (!allVars.contains(yTok)) {
                    errors.add(tokenError(i, baseOffset, raw, yTok, "Unknown variable: " + yTok));
                    continue;
                }
                if (xTok.equals(yTok)) {
                    errors.add(tokenError(i, baseOffset, raw, yTok, "X and Y must be distinct."));
                    continue;
                }
                xySel = new XYSel.Named(xTok, yTok);
            }

            // Parse optional generator at end of zAndMore
            GenSpec gen = GenSpec.none();

            String zPart = zAndMore;
            if (!zPart.isEmpty()) {
                // Look for +k(...) or +subsets(max=...)
                Matcher km = KGEN.matcher(zPart);
                Matcher sm = SUBSETS.matcher(zPart);

                if (km.find()) {
                    int a = Integer.parseInt(km.group(1));
                    int b = Integer.parseInt(km.group(2));
                    if (a > b) {
                        errors.add(spanError(i, baseOffset, raw, "Invalid +k(a..b): a must be <= b."));
                        continue;
                    }
                    gen = new GenSpec(a, b);
                    zPart = zPart.substring(0, km.start()).trim();
                } else if (sm.find()) {
                    int m = Integer.parseInt(sm.group(1));
                    gen = new GenSpec(0, m);
                    zPart = zPart.substring(0, sm.start()).trim();
                }
            }

            // Parse BASE Z list (with {X3..X9} expansions)
            Set<String> baseZ = new LinkedHashSet<>();
            if (!zPart.isEmpty()) {
                List<String> toks = splitCommaList(zPart);
                for (String t : toks) {
                    if (t.isEmpty()) continue;
                    List<String> expanded = expandRangesToken(t, allVars, i, baseOffset, raw, errors);
                    if (expanded == null) continue; // error already recorded
                    for (String v : expanded) {
                        if (!allVars.contains(v)) {
                            errors.add(tokenError(i, baseOffset, raw, v, "Unknown variable: " + v));
                            // continue parsing line; treat as error but keep scanning
                        } else {
                            baseZ.add(v);
                        }
                    }
                }
            }

            // Explicit-rule enforcement for named x,y: Z ∩ {x,y} = ∅ is parse error
            if (xySel instanceof XYSel.Named n) {
                if (baseZ.contains(n.x()) || baseZ.contains(n.y())) {
                    errors.add(spanError(i, baseOffset, raw, "Invalid explicit fact: conditioning set contains X or Y."));
                    continue;
                }
            }

            templates.add(new Template(i, raw, xySel, baseZ, gen, poolSpec));
        }

        // If any parse errors, we still compute previews for parsable templates.
        // Expansion
        List<PreviewLineStats> stats = new ArrayList<>();
        List<String> firstFacts = new ArrayList<>(TMath.min(previewLimit, 1024));

        int totalExpanded = 0, totalKept = 0, totalSkippedInvalid = 0;

        // Dedup by canonical key
        Set<String> seen = new HashSet<>();

        List<String> vars = new ArrayList<>(allVars);
        Collections.sort(vars);

        for (Template t : templates) {
            int expanded = 0, kept = 0, skippedInvalid = 0;

            List<String[]> pairs = expandPairs(t.xySel, vars);

            for (String[] pair : pairs) {
                String x = pair[0], y = pair[1];

                // If placeholders, we must enforce BASE doesn't contain x or y: template invalid => skip this expansion
                if (t.baseZ.contains(x) || t.baseZ.contains(y)) {
                    skippedInvalid++;
                    expanded++;
                    continue;
                }

                // Determine pool (depends on x,y)
                Set<String> pool = computePool(t.pool, vars, t.baseZ, x, y, adjMap);

                if (t.gen.isNone()) {
                    expanded++;
                    Set<String> z = new LinkedHashSet<>(t.baseZ);
                    // Z∩{x,y} already ensured for this expansion (baseZ checked)
                    String key = canonicalKey(x, y, z);
                    if (seen.add(key)) {
                        kept++;
                        maybeAddPreview(firstFacts, key, previewLimit);
                    }
                } else {
                    for (int k = t.gen.kMin(); k <= t.gen.kMax(); k++) {
                        List<List<String>> subsets = subsetsOfSize(pool, k);
                        for (List<String> s : subsets) {
                            expanded++;
                            Set<String> z = new LinkedHashSet<>(t.baseZ);
                            z.addAll(s);

                            // Structural guarantee should make this unnecessary, but keep safety:
                            if (z.contains(x) || z.contains(y)) {
                                skippedInvalid++;
                                continue;
                            }

                            String key = canonicalKey(x, y, z);
                            if (seen.add(key)) {
                                kept++;
                                maybeAddPreview(firstFacts, key, previewLimit);
                            }
                        }
                    }
                }
            }

            boolean warnZero = kept == 0 && expanded > 0;
            stats.add(new PreviewLineStats(t.lineIndex0, expanded, kept, skippedInvalid, warnZero));

            totalExpanded += expanded;
            totalKept += kept;
            totalSkippedInvalid += skippedInvalid;
        }

        return new PreviewResult(errors, stats, totalExpanded, totalKept, totalSkippedInvalid, firstFacts);
    }

    // ---------- Helpers ----------

    private static PoolSpec parsePool(String inside, List<String> allVars, Map<String, Set<String>> adjMap,
                                      int lineIndex0, int baseOffset, List<ParseError> errors, String rawLine) {
        String s = inside.trim();

        if (s.equalsIgnoreCase("all")) return new PoolSpec.All();
        if (s.equalsIgnoreCase("adj")) {
            if (adjMap == null) {
                errors.add(spanError(lineIndex0, baseOffset, rawLine, "pool(adj) requires a graph/adjacency context."));
            }
            return new PoolSpec.Adj();
        }

        // vars(...)
        if (s.toLowerCase(Locale.ROOT).startsWith("vars")) {
            int l = s.indexOf('(');
            int r = s.lastIndexOf(')');
            if (l < 0 || r < 0 || r <= l) {
                errors.add(spanError(lineIndex0, baseOffset, rawLine, "Malformed pool(vars(...))."));
                return new PoolSpec.All();
            }
            String body = s.substring(l + 1, r).trim();
            Set<String> out = new LinkedHashSet<>();
            for (String tok : splitCommaList(body)) {
                if (tok.isEmpty()) continue;
                List<String> expanded = expandRangesToken(tok, allVars, lineIndex0, baseOffset, rawLine, errors);
                if (expanded != null) out.addAll(expanded);
            }
            return new PoolSpec.Vars(out);
        }

        // not(...)
        if (s.toLowerCase(Locale.ROOT).startsWith("not")) {
            int l = s.indexOf('(');
            int r = s.lastIndexOf(')');
            if (l < 0 || r < 0 || r <= l) {
                errors.add(spanError(lineIndex0, baseOffset, rawLine, "Malformed pool(not(...))."));
                return new PoolSpec.All();
            }
            String body = s.substring(l + 1, r).trim();
            Set<String> out = new LinkedHashSet<>();
            for (String tok : splitCommaList(body)) {
                if (tok.isEmpty()) continue;
                List<String> expanded = expandRangesToken(tok, allVars, lineIndex0, baseOffset, rawLine, errors);
                if (expanded != null) out.addAll(expanded);
            }
            return new PoolSpec.Not(out);
        }

        errors.add(spanError(lineIndex0, baseOffset, rawLine, "Unknown pool(...) spec: " + inside));
        return new PoolSpec.All();
    }

    private static List<String> splitCommaList(String s) {
        // simple split; v0.1 assumes no quoting
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toList());
    }

    private static List<String> expandRangesToken(String token, List<String> allVars,
                                                  int lineIndex0, int baseOffset, String rawLine, List<ParseError> errors) {
        String t = token.trim();

        // {X3..X9} style
        Matcher m = RANGE.matcher(t);
        if (m.matches()) {
            String a = m.group(1);
            String b = m.group(2);

            // parse prefix + int suffix
            String[] pa = splitPrefixInt(a);
            String[] pb = splitPrefixInt(b);
            if (pa == null || pb == null || !pa[0].equals(pb[0])) {
                errors.add(tokenError(lineIndex0, baseOffset, rawLine, t, "Bad range: " + t + " (prefix/int mismatch)."));
                return null;
            }
            int ia = Integer.parseInt(pa[1]);
            int ib = Integer.parseInt(pb[1]);
            if (ia > ib) {
                errors.add(tokenError(lineIndex0, baseOffset, rawLine, t, "Bad range: start > end."));
                return null;
            }
            List<String> out = new ArrayList<>();
            for (int k = ia; k <= ib; k++) out.add(pa[0] + k);
            return out;
        }

        return List.of(t);
    }

    private static String[] splitPrefixInt(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isDigit(s.charAt(i))) i--;
        if (i == s.length() - 1) return null;
        String prefix = s.substring(0, i + 1);
        String num = s.substring(i + 1);
        if (prefix.isEmpty()) return null;
        return new String[]{prefix, num};
    }

    private static List<String[]> expandPairs(XYSel xySel, List<String> sortedVars) {
        List<String[]> out = new ArrayList<>();
        if (xySel instanceof XYSel.Named n) {
            String a = n.x(), b = n.y();
            // enforce unordered canonical within the template too
            if (a.compareTo(b) <= 0) out.add(new String[]{a, b});
            else out.add(new String[]{b, a});
        } else {
            for (int i = 0; i < sortedVars.size(); i++) {
                for (int j = i + 1; j < sortedVars.size(); j++) {
                    out.add(new String[]{sortedVars.get(i), sortedVars.get(j)});
                }
            }
        }
        return out;
    }

    private static Set<String> computePool(PoolSpec poolSpec, List<String> allVarsSorted, Set<String> baseZ,
                                           String x, String y, Map<String, Set<String>> adjMap) {
        Set<String> pool;
        if (poolSpec instanceof PoolSpec.All) {
            pool = new LinkedHashSet<>(allVarsSorted);
        } else if (poolSpec instanceof PoolSpec.Vars v) {
            pool = new LinkedHashSet<>(v.vars());
        } else if (poolSpec instanceof PoolSpec.Adj) {
            pool = new LinkedHashSet<>();
            if (adjMap != null) {
                pool.addAll(adjMap.getOrDefault(x, Set.of()));
                pool.addAll(adjMap.getOrDefault(y, Set.of()));
            }
        } else if (poolSpec instanceof PoolSpec.Not n) {
            pool = new LinkedHashSet<>(allVarsSorted);
            pool.removeAll(n.vars());
        } else {
            pool = new LinkedHashSet<>(allVarsSorted);
        }

        // Always exclude x,y and baseZ
        pool.remove(x);
        pool.remove(y);
        pool.removeAll(baseZ);
        return pool;
    }

    private static List<List<String>> subsetsOfSize(Set<String> pool, int k) {
        if (k < 0) return List.of();
        List<String> items = new ArrayList<>(pool);
        // deterministic ordering
        Collections.sort(items);
        List<List<String>> out = new ArrayList<>();
        choose(items, 0, k, new ArrayList<>(k), out);
        return out;
    }

    private static void choose(List<String> items, int idx, int k, List<String> cur, List<List<String>> out) {
        if (cur.size() == k) {
            out.add(new ArrayList<>(cur));
            return;
        }
        if (idx >= items.size()) return;
        // pruning
        int remaining = items.size() - idx;
        int needed = k - cur.size();
        if (remaining < needed) return;

        // take
        cur.add(items.get(idx));
        choose(items, idx + 1, k, cur, out);
        cur.remove(cur.size() - 1);

        // skip
        choose(items, idx + 1, k, cur, out);
    }

    private static String canonicalKey(String x, String y, Set<String> z) {
        String a = x, b = y;
        if (a.compareTo(b) > 0) { String tmp = a; a = b; b = tmp; }
        List<String> zz = new ArrayList<>(z);
        Collections.sort(zz);
        if (zz.isEmpty()) return a + " _||_ " + b;
        return a + " _||_ " + b + " | " + String.join(", ", zz);
    }

    private static void maybeAddPreview(List<String> firstFacts, String fact, int limit) {
        if (firstFacts.size() < limit) firstFacts.add(fact);
    }

    private static ParseError spanError(int lineIndex0, int baseOffset, String rawLine, String msg) {
        // underline whole line
        int start = baseOffset;
        int end = baseOffset + rawLine.length();
        return new ParseError(lineIndex0, start, TMath.max(start, end), msg);
    }

    private static ParseError tokenError(int lineIndex0, int baseOffset, String rawLine, String token, String msg) {
        int idx = rawLine.indexOf(token);
        if (idx < 0) return spanError(lineIndex0, baseOffset, rawLine, msg);
        int start = baseOffset + idx;
        int end = start + token.length();
        return new ParseError(lineIndex0, start, end, msg);
    }
}