// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
// Code generated by Microsoft (R) AutoRest Code Generator.

package com.azure.analytics.synapse.artifacts.models;

import com.azure.core.annotation.Fluent;
import com.azure.core.annotation.Generated;
import com.azure.core.util.CoreUtils;
import com.azure.json.JsonReader;
import com.azure.json.JsonSerializable;
import com.azure.json.JsonToken;
import com.azure.json.JsonWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Query parameters for listing runs.
 */
@Fluent
public final class RunFilterParameters implements JsonSerializable<RunFilterParameters> {
    /*
     * The continuation token for getting the next page of results. Null for first page.
     */
    @Generated
    private String continuationToken;

    /*
     * The time at or after which the run event was updated in 'ISO 8601' format.
     */
    @Generated
    private OffsetDateTime lastUpdatedAfter;

    /*
     * The time at or before which the run event was updated in 'ISO 8601' format.
     */
    @Generated
    private OffsetDateTime lastUpdatedBefore;

    /*
     * List of filters.
     */
    @Generated
    private List<RunQueryFilter> filters;

    /*
     * List of OrderBy option.
     */
    @Generated
    private List<RunQueryOrderBy> orderBy;

    /**
     * Creates an instance of RunFilterParameters class.
     */
    @Generated
    public RunFilterParameters() {
    }

    /**
     * Get the continuationToken property: The continuation token for getting the next page of results. Null for first
     * page.
     * 
     * @return the continuationToken value.
     */
    @Generated
    public String getContinuationToken() {
        return this.continuationToken;
    }

    /**
     * Set the continuationToken property: The continuation token for getting the next page of results. Null for first
     * page.
     * 
     * @param continuationToken the continuationToken value to set.
     * @return the RunFilterParameters object itself.
     */
    @Generated
    public RunFilterParameters setContinuationToken(String continuationToken) {
        this.continuationToken = continuationToken;
        return this;
    }

    /**
     * Get the lastUpdatedAfter property: The time at or after which the run event was updated in 'ISO 8601' format.
     * 
     * @return the lastUpdatedAfter value.
     */
    @Generated
    public OffsetDateTime getLastUpdatedAfter() {
        return this.lastUpdatedAfter;
    }

    /**
     * Set the lastUpdatedAfter property: The time at or after which the run event was updated in 'ISO 8601' format.
     * 
     * @param lastUpdatedAfter the lastUpdatedAfter value to set.
     * @return the RunFilterParameters object itself.
     */
    @Generated
    public RunFilterParameters setLastUpdatedAfter(OffsetDateTime lastUpdatedAfter) {
        this.lastUpdatedAfter = lastUpdatedAfter;
        return this;
    }

    /**
     * Get the lastUpdatedBefore property: The time at or before which the run event was updated in 'ISO 8601' format.
     * 
     * @return the lastUpdatedBefore value.
     */
    @Generated
    public OffsetDateTime getLastUpdatedBefore() {
        return this.lastUpdatedBefore;
    }

    /**
     * Set the lastUpdatedBefore property: The time at or before which the run event was updated in 'ISO 8601' format.
     * 
     * @param lastUpdatedBefore the lastUpdatedBefore value to set.
     * @return the RunFilterParameters object itself.
     */
    @Generated
    public RunFilterParameters setLastUpdatedBefore(OffsetDateTime lastUpdatedBefore) {
        this.lastUpdatedBefore = lastUpdatedBefore;
        return this;
    }

    /**
     * Get the filters property: List of filters.
     * 
     * @return the filters value.
     */
    @Generated
    public List<RunQueryFilter> getFilters() {
        return this.filters;
    }

    /**
     * Set the filters property: List of filters.
     * 
     * @param filters the filters value to set.
     * @return the RunFilterParameters object itself.
     */
    @Generated
    public RunFilterParameters setFilters(List<RunQueryFilter> filters) {
        this.filters = filters;
        return this;
    }

    /**
     * Get the orderBy property: List of OrderBy option.
     * 
     * @return the orderBy value.
     */
    @Generated
    public List<RunQueryOrderBy> getOrderBy() {
        return this.orderBy;
    }

    /**
     * Set the orderBy property: List of OrderBy option.
     * 
     * @param orderBy the orderBy value to set.
     * @return the RunFilterParameters object itself.
     */
    @Generated
    public RunFilterParameters setOrderBy(List<RunQueryOrderBy> orderBy) {
        this.orderBy = orderBy;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Generated
    @Override
    public JsonWriter toJson(JsonWriter jsonWriter) throws IOException {
        jsonWriter.writeStartObject();
        jsonWriter.writeStringField("lastUpdatedAfter",
            this.lastUpdatedAfter == null
                ? null
                : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(this.lastUpdatedAfter));
        jsonWriter.writeStringField("lastUpdatedBefore",
            this.lastUpdatedBefore == null
                ? null
                : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(this.lastUpdatedBefore));
        jsonWriter.writeStringField("continuationToken", this.continuationToken);
        jsonWriter.writeArrayField("filters", this.filters, (writer, element) -> writer.writeJson(element));
        jsonWriter.writeArrayField("orderBy", this.orderBy, (writer, element) -> writer.writeJson(element));
        return jsonWriter.writeEndObject();
    }

    /**
     * Reads an instance of RunFilterParameters from the JsonReader.
     * 
     * @param jsonReader The JsonReader being read.
     * @return An instance of RunFilterParameters if the JsonReader was pointing to an instance of it, or null if it was
     * pointing to JSON null.
     * @throws IllegalStateException If the deserialized JSON object was missing any required properties.
     * @throws IOException If an error occurs while reading the RunFilterParameters.
     */
    @Generated
    public static RunFilterParameters fromJson(JsonReader jsonReader) throws IOException {
        return jsonReader.readObject(reader -> {
            RunFilterParameters deserializedRunFilterParameters = new RunFilterParameters();
            while (reader.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = reader.getFieldName();
                reader.nextToken();

                if ("lastUpdatedAfter".equals(fieldName)) {
                    deserializedRunFilterParameters.lastUpdatedAfter = reader
                        .getNullable(nonNullReader -> CoreUtils.parseBestOffsetDateTime(nonNullReader.getString()));
                } else if ("lastUpdatedBefore".equals(fieldName)) {
                    deserializedRunFilterParameters.lastUpdatedBefore = reader
                        .getNullable(nonNullReader -> CoreUtils.parseBestOffsetDateTime(nonNullReader.getString()));
                } else if ("continuationToken".equals(fieldName)) {
                    deserializedRunFilterParameters.continuationToken = reader.getString();
                } else if ("filters".equals(fieldName)) {
                    List<RunQueryFilter> filters = reader.readArray(reader1 -> RunQueryFilter.fromJson(reader1));
                    deserializedRunFilterParameters.filters = filters;
                } else if ("orderBy".equals(fieldName)) {
                    List<RunQueryOrderBy> orderBy = reader.readArray(reader1 -> RunQueryOrderBy.fromJson(reader1));
                    deserializedRunFilterParameters.orderBy = orderBy;
                } else {
                    reader.skipChildren();
                }
            }

            return deserializedRunFilterParameters;
        });
    }
}
