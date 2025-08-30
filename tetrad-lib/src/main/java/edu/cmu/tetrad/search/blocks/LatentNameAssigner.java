package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

/**
 * Deterministic, collision-safe latent naming for block-based models.
 * <p>
 * Features: - Content-based names using overlaps with provided true clusters - Stable tie-breaking (count ↓, Jaccard ↓,
 * name ↑) - Optional collapsing of legacy single-capital suffixes (e.g., L12C → L12) - Global uniqueness across latents
 * and observed names - Reserved literals (default: {"Noise"}) are never altered or suffixed - Two modes: •
 * LEARNED_SINGLE: one latent per block (preserves ranks if present) • SIMULATION_EXPANDED: expands rank-r block into r
 * rank-1 latents with lettered suffixes
 */
public final class LatentNameAssigner {

    private LatentNameAssigner() {
    }

    // ----- Public API -----

    public static BlockSpec giveGoodLatentNames(
            BlockSpec spec,
            Map<String, List<String>> trueClusters,
            BlocksUtil.NamingMode mode
    ) {
        return giveGoodLatentNames(spec, trueClusters, mode, Config.defaults());
    }

    public static BlockSpec giveGoodLatentNames(
            BlockSpec spec,
            Map<String, List<String>> trueClusters,
            BlocksUtil.NamingMode mode,
            Config config
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(trueClusters, "trueClusters");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(config, "config");

        final List<List<Integer>> blocks = spec.blocks();
        final DataSet dataSet = spec.dataSet();
        final List<Node> dataVars = dataSet.getVariables();

        final List<Integer> ranksIn = spec.ranks() != null ? new ArrayList<>(spec.ranks()) : null;

        // Canon cache
        final Map<String, String> canonCache = new HashMap<>();
        final java.util.function.Function<String, String> CANON =
                s -> canonCache.computeIfAbsent(s, LatentNameAssigner::canon);

        // Observed names (avoid collisions)
        final Set<String> observedNamesUsed = new HashSet<>();
        for (Node v : dataVars) observedNamesUsed.add(sanitizeName(v.getName()));

        // Canonicalize true clusters deterministically
        final List<String> trueKeysSorted = new ArrayList<>(trueClusters.keySet());
        Collections.sort(trueKeysSorted);

        final Map<String, Set<String>> trueSetsCanon = new LinkedHashMap<>();
        final Map<String, String> sanitizedTrueName = new LinkedHashMap<>();
        for (String key : trueKeysSorted) {
            final Set<String> tset = new HashSet<>();
            for (String v : trueClusters.get(key)) tset.add(CANON.apply(v));
            trueSetsCanon.put(key, tset);
            sanitizedTrueName.put(key, sanitizeName(key));
        }

        // Original latent names aligned by index if present
        final List<Node> origLatents = spec.blockVariables() != null ? spec.blockVariables() : Collections.emptyList();
        final List<String> origNames = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            if (i < origLatents.size() && origLatents.get(i) != null) {
                origNames.add(origLatents.get(i).getName()); // keep raw; reservation logic handles literal "Noise"
            } else {
                origNames.add("L" + (i + 1));
            }
        }

        // Canonical block contents
        final List<Set<String>> blockCanonSets = new ArrayList<>(blocks.size());
        for (List<Integer> block : blocks) {
            final Set<String> bset = new HashSet<>(block.size());
            for (int idx : block) bset.add(CANON.apply(dataVars.get(idx).getName()));
            blockCanonSets.add(bset);
        }

        // Compute base names
        final List<String> baseNames = new ArrayList<>(blocks.size());
        final List<Boolean> hasMeaningfulMatch = new ArrayList<>(blocks.size());

        for (int bi = 0; bi < blocks.size(); bi++) {
            final Set<String> blockCanon = blockCanonSets.get(bi);
            final int blockSize = blockCanon.size();

            class Olap {
                final String key, sanitized;
                final int count;
                final double jaccard;

                Olap(String key, String sanitized, int count, double jaccard) {
                    this.key = key;
                    this.sanitized = sanitized;
                    this.count = count;
                    this.jaccard = jaccard;
                }
            }

            final List<Olap> overlaps = new ArrayList<>();
            String pureTrueKey = null;

            for (String key : trueKeysSorted) {
                final Set<String> tset = trueSetsCanon.get(key);
                int cnt = 0;
                for (String v : blockCanon) if (tset.contains(v)) cnt++;
                if (cnt > 0) {
                    final int union = blockSize + tset.size() - cnt;
                    final double jac = union > 0 ? (double) cnt / (double) union : 0.0;
                    overlaps.add(new Olap(key, sanitizedTrueName.get(key), cnt, jac));
                    if (cnt == blockSize) pureTrueKey = key;
                }
            }

            final String baseName;
            final boolean meaningful;

            if (pureTrueKey != null) {
                baseName = sanitizedTrueName.get(pureTrueKey);
                meaningful = true;
            } else if (!overlaps.isEmpty()) {
                // Previously: hyphen-joined overlaps like "A-B(+N.more)".
                // Now: just use the Mixed base; numbering handled by ensureUnique().
                baseName = config.defaultMixedName;  // e.g., "Mixed"
                meaningful = true;
            } else {
                baseName = config.defaultMixedName;
                meaningful = false;
            }

            baseNames.add(baseName);
            hasMeaningfulMatch.add(meaningful);
        }

        // Build output
        if (mode == BlocksUtil.NamingMode.LEARNED_SINGLE) {
            // We'll build nodes directly (no intermediate outNames), so we can drop-in observed nodes for singletons
            final Set<String> used = new HashSet<>(observedNamesUsed);
            final List<Node> newLatents = new ArrayList<>(blocks.size());

            for (int bi = 0; bi < blocks.size(); bi++) {
                final List<Integer> block = blocks.get(bi);

                // If block size == 1, use the original observed variable node as-is (do NOT set LATENT)
                if (block.size() == 1) {
                    Node obs = dataVars.get(block.get(0));
                    newLatents.add(obs);
                    used.add(sanitizeName(obs.getName())); // reserve its name to avoid later collisions
                    continue;
                }

                // Non-singleton: proceed with the naming logic
                final String preferredOrigRaw = origNames.get(bi);
                final boolean origReserved = isReserved(preferredOrigRaw, config);

                final String preferredOrig = origReserved
                        ? preferredOrigRaw
                        : maybeCollapse(cleanDecorations(preferredOrigRaw), config);

                final String baseDisplayRaw = baseNames.get(bi);
                final boolean baseReserved = isReserved(baseDisplayRaw, config);

                final String baseDisplay = baseReserved
                        ? baseDisplayRaw
                        : maybeCollapse(cleanDecorations(baseDisplayRaw), config);

                final boolean meaningful = hasMeaningfulMatch.get(bi);

                String chosen;
                if (origReserved) {
                    chosen = preferredOrigRaw; // keep literal "Noise"
                } else if (baseReserved) {
                    chosen = baseDisplayRaw;   // base is literal "Noise"
                } else if (meaningful) {
                    chosen = ensureUnique(baseDisplay, used, config);
                } else if (!used.contains(preferredOrig)) {
                    chosen = preferredOrig;
                } else {
                    chosen = ensureUnique(baseDisplay, used, config);
                }
                used.add(chosen);

                Node latent = new ContinuousVariable(chosen);
                latent.setNodeType(NodeType.LATENT);
                newLatents.add(latent);
            }

            final List<Integer> ranksOut = normalizeRanks(ranksIn, blocks.size());
            return ranksOut != null
                    ? new BlockSpec(dataSet, blocks, newLatents, ranksOut)
                    : new BlockSpec(dataSet, blocks, newLatents);
        } else { // SIMULATION_EXPANDED
            final List<List<Integer>> outBlocks = new ArrayList<>();
            final List<Node> outLatents = new ArrayList<>();
            final List<Integer> outRanks = new ArrayList<>();

            final Set<String> used = new HashSet<>(observedNamesUsed);
            final List<Integer> ranksOut = normalizeRanks(ranksIn, blocks.size());

            for (int bi = 0; bi < blocks.size(); bi++) {
                final List<Integer> block = blocks.get(bi);

                final String baseRaw = baseNames.get(bi);
                final boolean baseReserved = isReserved(baseRaw, config);

                final String baseCandidate = baseReserved
                        ? baseRaw
                        : maybeCollapse(cleanDecorations(baseRaw), config);

                final String origRaw = origNames.get(bi);
                final boolean origReserved = isReserved(origRaw, config);

                final String origCandidate = origReserved
                        ? origRaw
                        : maybeCollapse(cleanDecorations(origRaw), config);

                final String base = uniqueOrFallback(baseCandidate, origCandidate, used, config);

                final int r = (ranksOut != null && ranksOut.get(bi) != null) ? Math.max(1, ranksOut.get(bi)) : 1;

                for (int j = 0; j < r; j++) {
                    final String candidate;
                    final Node node;

                    if (j == 0 && block.size() == 1) {
                        // First component of a singleton block: use the observed node directly
                        node = dataVars.get(block.get(0));
                        candidate = sanitizeName(node.getName());
                        used.add(candidate); // mark its name as taken so later latents don’t collide
                    } else {
                        // Latent components (including for singleton blocks when j > 0)
                        candidate = (j == 0) ? base : nextLetteredBase(base, j, config);

                        final String name = isReserved(candidate, config)
                                ? candidate
                                : ensureUnique(candidate, used, config);

                        used.add(name);
                        Node latent = new ContinuousVariable(name);
                        latent.setNodeType(NodeType.LATENT);
                        node = latent;
                    }

                    outBlocks.add(new ArrayList<>(block));
                    outLatents.add(node);
                    outRanks.add(1); // expanded representation uses rank-1 per component by design
                }
            }

            return new BlockSpec(dataSet, outBlocks, outLatents, outRanks);
        }
    }

    // ----- Config -----

    private static List<Integer> normalizeRanks(List<Integer> ranksIn, int needed) {
        if (ranksIn == null) return null;
        final List<Integer> out = new ArrayList<>(needed);
        for (int i = 0; i < needed; i++) {
            Integer r = (i < ranksIn.size()) ? ranksIn.get(i) : 1;
            if (r == null || r < 0) r = 0;
            out.add(r);
        }
        return out;
    }

    // ----- Helpers -----

    private static boolean isReserved(String name, Config cfg) {
        return cfg.keepReservedLiteral && name != null && cfg.reservedLiteralNames.contains(name);
    }

    private static String maybeCollapse(String name, Config cfg) {
        if (isReserved(name, cfg)) return name; // keep literal
        if (!cfg.collapseTrailingCap) return name;
        if (name == null) return "L";
        var m = java.util.regex.Pattern.compile("^(.*\\d)[A-Z]$").matcher(name);
        return m.matches() ? m.group(1) : name;
    }

    private static String cleanDecorations(String s) {
        if (s == null) return "L";
        s = s.replaceAll("\\(\\d+\\)$", "");   // drop trailing "(2)" etc.
        return s.replaceAll("_+$", "");        // drop trailing underscores
    }

    //    private static String ensureUnique(String base, Set<String> used, Config cfg) {
//        if (isReserved(base, cfg)) return base; // literal stays as-is, even if "used"
//        String b = (base == null || base.isEmpty()) ? "L" : base;
//        if (!used.contains(b)) return b;
//        int k = 2;
//        while (used.contains(b + cfg.numericSep + k)) k++;
//        return b + cfg.numericSep + k;
//    }
    private static String ensureUnique(String base, Set<String> used, Config cfg) {
        if (isReserved(base, cfg)) return base; // keep literal, even if "used"
        String b = (base == null || base.isEmpty()) ? "L" : base;

        // Special-case: for mixed clusters, force numbering as Mixed1, Mixed2, ...
        if (b.equals(cfg.defaultMixedName)) {
            int k = 1;
            while (used.contains(b + k)) k++;
            return b + k;
        }

        // Default behavior for everything else
        if (!used.contains(b)) return b;
        int k = 2;
        while (used.contains(b + cfg.numericSep + k)) k++;
        return b + cfg.numericSep + k;
    }

    private static String uniqueOrFallback(String preferred, String fallback, Set<String> used, Config cfg) {
        if (isReserved(preferred, cfg)) return preferred;
        if (preferred != null && !preferred.isEmpty() && !used.contains(preferred)) return preferred;
        if (isReserved(fallback, cfg)) return fallback;
        if (fallback != null && !fallback.isEmpty() && !used.contains(fallback)) return fallback;
        return ensureUnique((preferred == null || preferred.isEmpty()) ? "L" : preferred, used, cfg);
    }

    private static String nextLetteredBase(String base, int idx, Config cfg) {
        int letterIndex = idx; // j=1->B, j=2->C, ...
        if (letterIndex >= 1 && letterIndex <= 25) {
            char suffix = (char) ('A' + letterIndex);
            return base + suffix;
        }
        return base + cfg.numericSep + (idx + 1);
    }

    private static String canon(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String sanitizeName(String s) {
        return s == null ? "L" : s.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    public static final class Config {
        /**
         * Specifies the maximum number of overlapping true clusters to display.
         * <p>
         * This value determines how many concurrent overlaps in data clusters will be visually represented or processed
         * in certain contexts.
         */
        public final int maxOverlapParts;         // how many overlapping true clusters to show
        /**
         * Indicates whether to display the count of overflow items in a concatenated string. If set to {@code true}, an
         * additional count (e.g., "+N more") is appended to denote the number of overflow elements that cannot be
         * displayed within the specified limit.
         */
        public final boolean showOverflowCount;   // add "+Nmore"
        /**
         * Specifies the string used to join parts in cases of overlap within a configuration. It defines a delimiter
         * that is applied when multiple components need to be combined and overlap resolution is required.
         */
        public final String overlapJoiner;        // e.g., "-"
        /**
         * Represents the prefix string to be applied when the configured overlap or part limit is exceeded. Typically
         * used to indicate an overflow condition in generated or processed output.
         * <p>
         * This value allows customization of how overflows are marked, providing flexibility in format representation.
         */
        public final String overflowPrefix;       // e.g., "+"
        /**
         * Represents the default name used for blocks when no match is identified. This variable is part of the
         * configuration settings for managing naming behavior in scenarios where specific identifiers are unavailable
         * or not applicable.
         */
        public final String defaultMixedName;     // name for blocks with no match
        /**
         * A configuration flag that determines whether trailing capital letters following a digit in mixed alphanumeric
         * strings should be collapsed during processing.
         */
        public final boolean collapseTrailingCap; // collapse ...<digit><Capital> → ...<digit>
        /**
         * Represents the separator used for numeric disambiguation in configurations. It is commonly utilized to denote
         * a boundary or separation for numeric values, such as the inclusion of a dash ("-") in certain contexts.
         */
        public final String numericSep;           // separator for numeric disambiguation, e.g. "-"
        /**
         * A set of reserved literal names that must be preserved as-is during processing. This collection typically
         * includes predefined strings or values that should not be altered or replaced. It can be useful in scenarios
         * where specific keywords or identifiers need to remain intact, ensuring they retain their intended meaning or
         * function.
         */
        public final Set<String> reservedLiteralNames; // e.g., {"Noise"}
        /**
         * Indicates whether reserved literals should be retained in their original form when processing or transforming
         * text or structures. If set to {@code true}, the reserved literals will not be altered or replaced during
         * processing. If set to {@code false}, reserved literals may be modified based on the processing logic.
         */
        public final boolean keepReservedLiteral;      // never modify these

        private Config(int maxOverlapParts, boolean showOverflowCount, String overlapJoiner,
                       String overflowPrefix, String defaultMixedName, boolean collapseTrailingCap,
                       String numericSep, Set<String> reservedLiteralNames, boolean keepReservedLiteral) {
            this.maxOverlapParts = maxOverlapParts;
            this.showOverflowCount = showOverflowCount;
            this.overlapJoiner = overlapJoiner;
            this.overflowPrefix = overflowPrefix;
            this.defaultMixedName = defaultMixedName;
            this.collapseTrailingCap = collapseTrailingCap;
            this.numericSep = numericSep;
            this.reservedLiteralNames = reservedLiteralNames == null ? Set.of() : Set.copyOf(reservedLiteralNames);
            this.keepReservedLiteral = keepReservedLiteral;
        }

        /**
         * Creates and returns a new instance of the {@code Config} class initialized with default configuration
         * values.
         *
         * @return a {@code Config} instance preconfigured with default settings, including default values for
         * maxOverlapParts, showOverflowCount, overlapJoiner, overflowPrefix, defaultMixedName, collapseTrailingCap,
         * numericSep, reservedLiteralNames, and keepReservedLiteral properties.
         */
        public static Config defaults() {
            return new Config(
                    8, true, "-", "+", "Mixed", true, "-",
                    Set.of("Noise"),  // reserve “Noise” by default
                    true              // keep reserved names literal
            );
        }

        /**
         * Creates and returns a new instance of the Builder class initialized with default configuration values.
         *
         * @return a new Builder instance preconfigured with the default configuration properties of the Config class.
         */
        public static Builder builder() {
            return new Builder(defaults());
        }

        /**
         * Builder class for constructing instances of the Config class with customizable properties. Provides methods
         * to set specific properties and a build method to create a Config instance.
         */
        public static final class Builder {
            private int maxOverlapParts;
            private boolean showOverflowCount;
            private String overlapJoiner;
            private String overflowPrefix;
            private String defaultMixedName;
            private boolean collapseTrailingCap;
            private String numericSep;
            private Set<String> reservedLiteralNames;
            private boolean keepReservedLiteral;

            private Builder(Config base) {
                this.maxOverlapParts = base.maxOverlapParts;
                this.showOverflowCount = base.showOverflowCount;
                this.overlapJoiner = base.overlapJoiner;
                this.overflowPrefix = base.overflowPrefix;
                this.defaultMixedName = base.defaultMixedName;
                this.collapseTrailingCap = base.collapseTrailingCap;
                this.numericSep = base.numericSep;
                this.reservedLiteralNames = new HashSet<>(base.reservedLiteralNames);
                this.keepReservedLiteral = base.keepReservedLiteral;
            }

            /**
             * Sets the maximum number of overlapping parts allowed.
             *
             * @param v the maximum number of parts that can overlap
             * @return the Builder instance with the updated configuration
             */
            public Builder maxOverlapParts(int v) {
                this.maxOverlapParts = v;
                return this;
            }

            /**
             * Sets whether the overflow count should be displayed in the resulting configuration.
             *
             * @param v a boolean value indicating whether to show the overflow count (true to enable, false to
             *          disable)
             * @return the Builder instance with the updated configuration
             */
            public Builder showOverflowCount(boolean v) {
                this.showOverflowCount = v;
                return this;
            }

            /**
             * Sets the string that will be used as the joiner for overlapping parts.
             *
             * @param v the string value to be used for joining overlapping parts
             * @return the Builder instance with the updated configuration
             */
            public Builder overlapJoiner(String v) {
                this.overlapJoiner = v;
                return this;
            }

            /**
             * Sets the string to be used as the prefix for overflow information in the resulting configuration.
             *
             * @param v the string value to be used as the overflow prefix
             * @return the Builder instance with the updated configuration
             */
            public Builder overflowPrefix(String v) {
                this.overflowPrefix = v;
                return this;
            }

            /**
             * Sets the default mixed name for this configuration.
             *
             * @param v the default mixed name to be set
             * @return the Builder instance with the updated configuration
             */
            public Builder defaultMixedName(String v) {
                this.defaultMixedName = v;
                return this;
            }

            /**
             * Sets whether trailing capital letters should be collapsed in the resulting configuration.
             *
             * @param v a boolean value indicating whether to collapse trailing capital letters (true to enable, false
             *          to disable)
             * @return the Builder instance with the updated configuration
             */
            public Builder collapseTrailingCap(boolean v) {
                this.collapseTrailingCap = v;
                return this;
            }

            /**
             * Sets the string to be used as the separator for numeric parts in the resulting configuration.
             *
             * @param v the string value to be used as the numeric separator
             * @return the Builder instance with the updated configuration
             */
            public Builder numericSep(String v) {
                this.numericSep = v;
                return this;
            }

            /**
             * Sets the reserved literal names for the configuration.
             *
             * @param v a set of literal names to be considered reserved
             * @return the Builder instance with the updated configuration
             */
            public Builder reservedLiteralNames(Set<String> v) {
                this.reservedLiteralNames = new HashSet<>(v);
                return this;
            }

            /**
             * Sets whether reserved literal names should be kept in the resulting configuration.
             *
             * @param v a boolean value indicating whether to keep reserved literal names (true to enable, false to
             *          disable)
             * @return the Builder instance with the updated configuration
             */
            public Builder keepReservedLiteral(boolean v) {
                this.keepReservedLiteral = v;
                return this;
            }

            /**
             * Builds the configuration with the specified settings.
             *
             * @return the Config instance with the updated configuration
             */
            public Config build() {
                return new Config(maxOverlapParts, showOverflowCount, overlapJoiner,
                        overflowPrefix, defaultMixedName, collapseTrailingCap, numericSep,
                        reservedLiteralNames, keepReservedLiteral);
            }
        }
    }

}