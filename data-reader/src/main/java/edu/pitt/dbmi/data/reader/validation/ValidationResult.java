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
package edu.pitt.dbmi.data.reader.validation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Feb 16, 2017 11:33:31 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ValidationResult {

    private final ValidationCode code;

    private final MessageType messageType;

    private final Map<ValidationAttribute, Object> attributes;

    private String message;

    /**
     * Default constructor.
     *
     * @param code        the validation code.
     * @param messageType the message type.
     */
    public ValidationResult(ValidationCode code, MessageType messageType) {
        this.code = code;
        this.messageType = messageType;
        this.attributes = new EnumMap<>(ValidationAttribute.class);
    }

    /**
     * Constructor with message.
     *
     * @param code        the validation code.
     * @param messageType the message type.
     * @param message     the message.
     */
    public ValidationResult(ValidationCode code, MessageType messageType, String message) {
        this(code, messageType);
        this.message = message;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Return the string representation of this object.
     */
    @Override
    public String toString() {
        return "ValidationResult{" + "code=" + this.code + ", messageType=" + this.messageType + ", attributes=" + this.attributes + ", message=" + this.message + '}';
    }

    /**
     * Return the hash code of this object.
     *
     * @return the hash code of this object.
     */
    public ValidationCode getCode() {
        return this.code;
    }

    /**
     * Return the message type.
     *
     * @return the message type.
     */
    public MessageType getMessageType() {
        return this.messageType;
    }

    /**
     * Return the attributes.
     *
     * @return the attributes.
     */
    public Map<ValidationAttribute, Object> getAttributes() {
        return this.attributes;
    }

    /**
     * Sets the attribute value.
     *
     * @param attribute the attribute.
     * @param value     the value.
     */
    public void setAttribute(ValidationAttribute attribute, Object value) {
        this.attributes.put(attribute, value);
    }

    /**
     * Return the message.
     *
     * @return the message.
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Sets the message.
     *
     * @param message the message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

}
