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
 *
 * Dec 18, 2018 11:21:23 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ColumnMetadata {

    private String name;

    private boolean discrete;

    @JsonIgnore
    private int columnNumber;

    public ColumnMetadata() {
        this.discrete = true;
    }

    public ColumnMetadata(String name, boolean discrete) {
        this.name = name;
        this.discrete = discrete;
    }

    public ColumnMetadata(String name, int columnNumber, boolean discrete) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.discrete = discrete;
    }

    @Override
    public String toString() {
        return "ColumnMetadata{" + "name=" + name + ", discrete=" + discrete + ", columnNumber=" + columnNumber + '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDiscrete() {
        return discrete;
    }

    public void setDiscrete(boolean discrete) {
        this.discrete = discrete;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

}
