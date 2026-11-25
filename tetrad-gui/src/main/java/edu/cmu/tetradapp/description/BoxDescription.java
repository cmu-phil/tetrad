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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The description of one session box: its name and an ordered list of items. An item is either a field (a key and a
 * one-line value) or a block (a name and several lines of text, used for knowledge, graphs and notes). The same key
 * may occur more than once; changed parameters, for instance, are one "Changed" field each.
 * <p>
 * This class holds text only. It knows nothing of session nodes or models, so that a description can be written,
 * parsed and compared without a session.
 *
 * @author josephramsey
 * @author Claude
 * @see SessionDescription
 */
public final class BoxDescription {

    /**
     * The key of the field giving the box type, for example "Search".
     */
    public static final String TYPE = "Type";

    /**
     * The key of the field giving the model chosen in the box, for example "Tiers and Edges".
     */
    public static final String MODEL = "Model";

    /**
     * The key of the field listing the names of the parent boxes, separated by commas.
     */
    public static final String PARENTS = "Parents";

    /**
     * The key of the field giving the center of the box in the session workspace, as "x, y".
     */
    public static final String LOCATION = "Location";

    /**
     * The key of a field giving one parameter whose value differs from its default.
     */
    public static final String CHANGED = "Changed";

    /**
     * The name of the box.
     */
    private final String name;

    /**
     * The items of the box, in order.
     */
    private final List<Item> items;

    /**
     * Constructs a box description.
     *
     * @param name  the name of the box.
     * @param items the items of the box, in order.
     */
    public BoxDescription(String name, List<Item> items) {
        if (name == null) {
            throw new NullPointerException("The box name must not be null.");
        }

        this.name = oneLine(name);
        this.items = List.copyOf(items);
    }

    /**
     * Flattens a string to one line with no leading or trailing space, so that it can be the value of a field.
     *
     * @param s the string.
     * @return the string on one line.
     */
    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s*\\R\\s*", " ").replace(' ', ' ').strip();
    }

    /**
     * Returns the name of the box.
     *
     * @return the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the items of the box, in order.
     *
     * @return the items; the list cannot be modified.
     */
    public List<Item> getItems() {
        return this.items;
    }

    /**
     * Returns the value of the first field with the given key.
     *
     * @param key the key.
     * @return the value, or null if there is no such field.
     */
    public String get(String key) {
        for (Item item : this.items) {
            if (item instanceof Field field && field.key().equals(key)) {
                return field.value();
            }
        }

        return null;
    }

    /**
     * Returns the values of all fields with the given key, in order.
     *
     * @param key the key.
     * @return the values; empty if there is no such field.
     */
    public List<String> getAll(String key) {
        List<String> values = new ArrayList<>();

        for (Item item : this.items) {
            if (item instanceof Field field && field.key().equals(key)) {
                values.add(field.value());
            }
        }

        return values;
    }

    /**
     * Returns the lines of the first block with the given name.
     *
     * @param blockName the name of the block.
     * @return the lines, or null if there is no such block.
     */
    public List<String> getBlock(String blockName) {
        for (Item item : this.items) {
            if (item instanceof Block block && block.name().equals(blockName)) {
                return block.lines();
            }
        }

        return null;
    }

    /**
     * Returns the location of the box, if it is given and well formed.
     *
     * @return an array holding x and y, or null.
     */
    public int[] getLocation() {
        String value = get(LOCATION);

        if (value == null) {
            return null;
        }

        String[] parts = value.split(",");

        if (parts.length != 2) {
            return null;
        }

        try {
            return new int[]{Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the changed parameters of the box, in order. Fields with the "Changed" key that cannot be read as a
     * parameter are left out.
     *
     * @return the changed parameters.
     */
    public List<ChangedParameter> getChangedParameters() {
        List<ChangedParameter> changed = new ArrayList<>();

        for (String value : getAll(CHANGED)) {
            ChangedParameter parameter = ChangedParameter.parse(value);

            if (parameter != null) {
                changed.add(parameter);
            }
        }

        return changed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof BoxDescription other && this.name.equals(other.name) && this.items.equals(other.items);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 31 * this.name.hashCode() + this.items.hashCode();
    }

    /**
     * An item of a box description: a field or a block.
     */
    public sealed interface Item permits Field, Block {
    }

    /**
     * A key with a one-line value.
     *
     * @param key   the key; it cannot contain a colon.
     * @param value the value, on one line.
     */
    public record Field(String key, String value) implements Item {

        /**
         * Constructs a field, flattening the value to one line.
         *
         * @param key   the key.
         * @param value the value.
         */
        public Field {
            if (key == null || key.isBlank() || key.contains(":") || key.contains("\n")) {
                throw new IllegalArgumentException("Not a usable key: " + key);
            }

            key = key.strip();
            value = oneLine(value);
        }
    }

    /**
     * A named run of lines, written between a "Begin" line and an "End" line.
     *
     * @param name  the name of the block, for example "knowledge".
     * @param lines the lines of the block.
     */
    public record Block(String name, List<String> lines) implements Item {

        /**
         * Constructs a block. Trailing white space is removed from each line, and blank lines are removed from the
         * start and the end of the block, so that writing and reading a block gives back the same block.
         *
         * @param name  the name of the block.
         * @param lines the lines of the block.
         */
        public Block {
            if (name == null || name.isBlank() || name.contains("\n")) {
                throw new IllegalArgumentException("Not a usable block name: " + name);
            }

            name = name.strip();
            List<String> cleaned = new ArrayList<>();

            for (String line : lines) {
                cleaned.add(line.replace(' ', ' ').stripTrailing());
            }

            while (!cleaned.isEmpty() && cleaned.get(0).isEmpty()) {
                cleaned.remove(0);
            }

            while (!cleaned.isEmpty() && cleaned.get(cleaned.size() - 1).isEmpty()) {
                cleaned.remove(cleaned.size() - 1);
            }

            lines = Collections.unmodifiableList(cleaned);
        }

        /**
         * Constructs a block from text holding line breaks.
         *
         * @param name the name of the block.
         * @param text the text.
         * @return the block.
         */
        public static Block ofText(String name, String text) {
            return new Block(name, List.of(text.split("\\R", -1)));
        }
    }

    /**
     * A parameter whose value differs from its default, as written in a "Changed" field: the parameter's name, an
     * equals sign, the value, and the default in parentheses, for example "penaltyDiscount = 2.0 (default 1.0)".
     *
     * @param name         the name of the parameter, as used in Tetrad's parameter lists.
     * @param value        the value, as text.
     * @param defaultValue the default value, as text.
     */
    public record ChangedParameter(String name, String value, String defaultValue) {

        private static final String DEFAULT_OPEN = " (default ";

        /**
         * Reads a changed parameter from the value of a "Changed" field.
         *
         * @param text the value of the field.
         * @return the changed parameter, or null if the text does not have the expected form.
         */
        public static ChangedParameter parse(String text) {
            int equals = text.indexOf(" = ");
            int defaultAt = text.lastIndexOf(DEFAULT_OPEN);

            if (equals <= 0 || defaultAt < equals + 3 || !text.endsWith(")")) {
                return null;
            }

            return new ChangedParameter(text.substring(0, equals).strip(),
                    text.substring(equals + 3, defaultAt),
                    text.substring(defaultAt + DEFAULT_OPEN.length(), text.length() - 1));
        }

        /**
         * Returns the text of this changed parameter as it is written in a "Changed" field.
         *
         * @return the text.
         */
        public String toText() {
            return this.name + " = " + this.value + DEFAULT_OPEN + this.defaultValue + ")";
        }
    }

    /**
     * Builds a box description item by item.
     */
    public static final class Builder {
        private final String name;
        private final List<Item> items = new ArrayList<>();

        /**
         * Constructs a builder for a box of the given name.
         *
         * @param name the name of the box.
         */
        public Builder(String name) {
            this.name = name;
        }

        /**
         * Adds a field.
         *
         * @param key   the key.
         * @param value the value.
         * @return this builder.
         */
        public Builder field(String key, String value) {
            this.items.add(new Field(key, value));
            return this;
        }

        /**
         * Adds a "Changed" field for a parameter whose value differs from its default.
         *
         * @param parameterName the name of the parameter.
         * @param value         the value, as text.
         * @param defaultValue  the default value, as text.
         * @return this builder.
         */
        public Builder changed(String parameterName, String value, String defaultValue) {
            return field(CHANGED, new ChangedParameter(parameterName, oneLine(value), oneLine(defaultValue)).toText());
        }

        /**
         * Adds a block.
         *
         * @param blockName the name of the block.
         * @param text      the text of the block, holding line breaks.
         * @return this builder.
         */
        public Builder block(String blockName, String text) {
            this.items.add(Block.ofText(blockName, text));
            return this;
        }

        /**
         * Returns the box description built so far.
         *
         * @return the box description.
         */
        public BoxDescription build() {
            return new BoxDescription(this.name, this.items);
        }
    }
}
