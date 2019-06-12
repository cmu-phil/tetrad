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
 *
 * Feb 16, 2017 11:33:31 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ValidationResult {

    private final ValidationCode code;

    private final MessageType messageType;

    private final Map<ValidationAttribute, Object> attributes;

    private String message;

    public ValidationResult(ValidationCode code, MessageType messageType) {
        this.code = code;
        this.messageType = messageType;
        this.attributes = new EnumMap<>(ValidationAttribute.class);
    }

    public ValidationResult(ValidationCode code, MessageType messageType, String message) {
        this(code, messageType);
        this.message = message;
    }

    @Override
    public String toString() {
        return "ValidationResult{" + "code=" + code + ", messageType=" + messageType + ", attributes=" + attributes + ", message=" + message + '}';
    }

    public ValidationCode getCode() {
        return code;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public Map<ValidationAttribute, Object> getAttributes() {
        return attributes;
    }

    public void setAttribute(ValidationAttribute attribute, Object value) {
        this.attributes.put(attribute, value);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
