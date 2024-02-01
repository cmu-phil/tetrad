/*
 * Copyright (C) 2018 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.data.reader;

/**
 * Nov 5, 2018 2:27:47 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public enum Delimiter {

    TAB("tab", '\t'),
    SPACE("space", ' '),
    WHITESPACE("whitespace", ' '),
    COMMA("comma", ','),
    COLON("colon", ':'),
    SEMICOLON("semicolon", ';'),
    PIPE("pipe", '|');

    private final String name;
    private final char value;
    private final byte byteValue;

    /**
     * Constructor.
     *
     * @param name  the name of the delimiter.
     * @param value the value of the delimiter.
     */
    Delimiter(String name, char value) {
        this.name = name;
        this.value = value;
        this.byteValue = (byte) value;
    }

    /**
     * Get the name of the delimiter.
     *
     * @return the name of the delimiter.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the value of the delimiter.
     *
     * @return the value of the delimiter.
     */
    public char getValue() {
        return this.value;
    }

    /**
     * Get the byte value of the delimiter.
     *
     * @return the byte value of the delimiter.
     */
    public byte getByteValue() {
        return this.byteValue;
    }

}
