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
package edu.pitt.dbmi.data.reader.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Dec 18, 2018 11:21:23 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ColumnMetadata {

    /**
     * The name of the column.
     */
    private String name;

    /**
     * Indicates whether the column is discrete.
     */
    private boolean discrete;

    /**
     * The column number.
     */
    @JsonIgnore
    private int columnNumber;

    /**
     * Default constructor.
     */
    public ColumnMetadata() {
        this.discrete = true;
    }

    /**
     * Constructor.
     *
     * @param name     the name of the column.
     * @param discrete indicates whether the column is discrete.
     */
    public ColumnMetadata(String name, boolean discrete) {
        this.name = name;
        this.discrete = discrete;
    }

    /**
     * Constructor.
     *
     * @param name         the name of the column.
     * @param columnNumber the column number.
     * @param discrete     indicates whether the column is discrete.
     */
    public ColumnMetadata(String name, int columnNumber, boolean discrete) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.discrete = discrete;
    }

    /**
     * Return a string representation of the column metadata.
     *
     * @return a string representation of the column metadata.
     */
    @Override
    public String toString() {
        return "ColumnMetadata{" + "name=" + this.name + ", discrete=" + this.discrete + ", columnNumber=" + this.columnNumber + '}';
    }

    /**
     * Return the name of the column.
     *
     * @return the name of the column.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Set the name of the column.
     *
     * @param name the name of the column.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Return whether the column is discrete.
     *
     * @return whether the column is discrete.
     */
    public boolean isDiscrete() {
        return this.discrete;
    }

    /**
     * Set whether the column is discrete.
     *
     * @param discrete whether the column is discrete.
     */
    public void setDiscrete(boolean discrete) {
        this.discrete = discrete;
    }

    /**
     * Return the column number.
     *
     * @return the column number.
     */
    public int getColumnNumber() {
        return this.columnNumber;
    }

    /**
     * Set the column number.
     *
     * @param columnNumber the column number.
     */
    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

}
