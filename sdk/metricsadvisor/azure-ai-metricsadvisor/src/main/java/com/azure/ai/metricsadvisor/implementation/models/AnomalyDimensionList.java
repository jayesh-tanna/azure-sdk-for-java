// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.ai.metricsadvisor.implementation.models;

import com.azure.core.annotation.Fluent;
import com.azure.core.annotation.Generated;
import com.azure.json.JsonReader;
import com.azure.json.JsonSerializable;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import java.io.IOException;
import java.util.List;

/**
 * The AnomalyDimensionList model.
 */
@Fluent
public final class AnomalyDimensionList implements JsonSerializable<AnomalyDimensionList> {
    /*
     * The @nextLink property.
     */
    @Generated
    private String nextLink;

    /*
     * The value property.
     */
    @Generated
    private List<String> value;

    /**
     * Creates an instance of AnomalyDimensionList class.
     */
    @Generated
    public AnomalyDimensionList() {
    }

    /**
     * Get the nextLink property: The &#064;nextLink property.
     * 
     * @return the nextLink value.
     */
    @Generated
    public String getNextLink() {
        return this.nextLink;
    }

    /**
     * Get the value property: The value property.
     * 
     * @return the value value.
     */
    @Generated
    public List<String> getValue() {
        return this.value;
    }

    /**
     * Set the value property: The value property.
     * 
     * @param value the value value to set.
     * @return the AnomalyDimensionList object itself.
     */
    @Generated
    public AnomalyDimensionList setValue(List<String> value) {
        this.value = value;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Generated
    @Override
    public JsonWriter toJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.writeStartObject();
        jsonWriter.writeArrayField("value", this.value, (writer, element) -> writer.writeString(element));
        return jsonWriter.writeEndObject();
    }

    /**
     * Reads an instance of AnomalyDimensionList from the JsonReader.
     * 
     * @param jsonReader The JsonReader being read.
     * @return An instance of AnomalyDimensionList if the JsonReader was pointing to an instance of it, or null if it
     * was pointing to JSON null.
     * @throws IllegalStateException If the deserialized JSON object was missing any required properties.
     * @throws IOException If an error occurs while reading the AnomalyDimensionList.
     */
    @Generated
    public static AnomalyDimensionList fromJson(JsonReader jsonReader) throws IOException {
        return jsonReader.readObject(reader -> {
            AnomalyDimensionList deserializedAnomalyDimensionList = new AnomalyDimensionList();
            while (reader.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = reader.getFieldName();
                reader.nextToken();

                if ("value".equals(fieldName)) {
                    List<String> value = reader.readArray(reader1 -> reader1.getString());
                    deserializedAnomalyDimensionList.value = value;
                } else if ("@nextLink".equals(fieldName)) {
                    deserializedAnomalyDimensionList.nextLink = reader.getString();
                } else {
                    reader.skipChildren();
                }
            }

            return deserializedAnomalyDimensionList;
        });
    }
}
