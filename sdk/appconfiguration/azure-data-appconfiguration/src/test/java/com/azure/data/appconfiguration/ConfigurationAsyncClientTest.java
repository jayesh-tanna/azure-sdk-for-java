// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.
package com.azure.data.appconfiguration;

import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.MatchConditions;
import com.azure.core.http.policy.AddHeadersFromContextPolicy;
import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.Response;
import com.azure.core.test.http.AssertingHttpClientBuilder;
import com.azure.core.test.models.CustomMatcher;
import com.azure.core.util.logging.ClientLogger;
import com.azure.core.util.polling.PollOperationDetails;
import com.azure.core.util.polling.SyncPoller;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.azure.data.appconfiguration.models.ConfigurationSnapshot;
import com.azure.data.appconfiguration.models.ConfigurationSnapshotStatus;
import com.azure.data.appconfiguration.models.FeatureFlagConfigurationSetting;
import com.azure.data.appconfiguration.models.SecretReferenceConfigurationSetting;
import com.azure.data.appconfiguration.models.SettingFields;
import com.azure.data.appconfiguration.models.SettingLabel;
import com.azure.data.appconfiguration.models.SettingLabelSelector;
import com.azure.data.appconfiguration.models.SettingSelector;
import com.azure.data.appconfiguration.models.SnapshotComposition;
import com.azure.data.appconfiguration.models.SnapshotFields;
import com.azure.data.appconfiguration.models.SnapshotSelector;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.azure.data.appconfiguration.implementation.Utility.getTagsFilterInString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ParameterizedClass
@MethodSource("com.azure.data.appconfiguration.TestHelper#getTestParameters")
public class ConfigurationAsyncClientTest extends ConfigurationClientTestBase {
    private static final ClientLogger LOGGER = new ClientLogger(ConfigurationAsyncClientTest.class);
    private static final String NO_LABEL = null;

    private final HttpClient httpClient;
    private final ConfigurationServiceVersion serviceVersion;

    private ConfigurationAsyncClient client;

    public ConfigurationAsyncClientTest(HttpClient httpClient, ConfigurationServiceVersion serviceVersion) {
        this.httpClient = httpClient;
        this.serviceVersion = serviceVersion;
    }

    @Override
    protected void beforeTest() {
        beforeTestSetup();
        client = getConfigurationAsyncClient(httpClient, serviceVersion);
    }

    @Override
    protected void afterTest() {
        LOGGER.info("Cleaning up created key values.");
        client.listConfigurationSettings(new SettingSelector().setKeyFilter(keyPrefix + "*"))
            .flatMap(configurationSetting -> {
                LOGGER.info("Deleting key:label [{}:{}]. isReadOnly? {}", configurationSetting.getKey(),
                    configurationSetting.getLabel(), configurationSetting.isReadOnly());
                Mono<Response<ConfigurationSetting>> unlock = configurationSetting.isReadOnly()
                    ? client.setReadOnlyWithResponse(configurationSetting, false)
                    : Mono.empty();
                return unlock.then(client.deleteConfigurationSettingWithResponse(configurationSetting, false));
            })
            .blockLast();

        LOGGER.info("Finished cleaning up values.");
    }

    private ConfigurationAsyncClient getConfigurationAsyncClient(HttpClient httpClient,
        ConfigurationServiceVersion serviceVersion) {
        return clientSetup((credentials, endpoint) -> {
            ConfigurationClientBuilder builder = new ConfigurationClientBuilder().credential(credentials)
                .endpoint(endpoint)
                .serviceVersion(serviceVersion);

            builder = setHttpClient(httpClient, builder);

            if (interceptorManager.isRecordMode()) {
                builder.addPolicy(interceptorManager.getRecordPolicy());
            } else if (interceptorManager.isPlaybackMode()) {
                interceptorManager.addMatchers(Collections.singletonList(
                    new CustomMatcher().setHeadersKeyOnlyMatch(Arrays.asList("Sync-Token", "If-Match"))));
            }

            // Disable `$.key` snanitizer
            if (!interceptorManager.isLiveMode()) {
                interceptorManager.removeSanitizers(REMOVE_SANITIZER_ID);
            }
            return builder.buildAsyncClient();
        });
    }

    private ConfigurationClientBuilder setHttpClient(HttpClient httpClient, ConfigurationClientBuilder builder) {
        if (interceptorManager.isPlaybackMode()) {
            return builder.httpClient(buildAsyncAssertingClient(interceptorManager.getPlaybackClient()));
        }
        return builder.httpClient(buildAsyncAssertingClient(httpClient));
    }

    private HttpClient buildAsyncAssertingClient(HttpClient httpClient) {
        return new AssertingHttpClientBuilder(httpClient).assertAsync().build();
    }

    /**
     * Tests that a configuration is able to be added, these are differentiate from each other using a key or key-label
     * identifier.
     */
    @Test
    public void addConfigurationSetting() {
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());
    }

    @Test
    public void addConfigurationSettingConvenience() {
        addConfigurationSettingRunner((expected) -> StepVerifier.create(client.addConfigurationSetting(expected))
            .assertNext(response -> assertConfigurationEquals(expected, response))
            .verifyComplete());
    }

    @Test
    public void addFeatureFlagConfigurationSettingConvenience() {
        addFeatureFlagConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete());
    }

    @Test
    public void addSecretReferenceConfigurationSettingConvenience() {
        addSecretReferenceConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete());
    }

    /**
     * Tests that we cannot add a configuration setting when the key is an empty string.
     */
    @Test
    public void addConfigurationSettingEmptyKey() {
        StepVerifier.create(client.addConfigurationSetting("", null, "A value"))
            .verifyErrorSatisfies(ex -> assertRestException(ex, HttpURLConnection.HTTP_BAD_METHOD));
    }

    /**
     * Tests that we can add configuration settings when value is not null or an empty string.
     */
    @Test
    public void addConfigurationSettingEmptyValue() {
        addConfigurationSettingEmptyValueRunner((setting) -> {
            StepVerifier
                .create(client.addConfigurationSetting(setting.getKey(), setting.getLabel(), setting.getValue()))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSetting(setting.getKey(), setting.getLabel(), null))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();
        });
    }

    /**
     * Verifies that an exception is thrown when null key is passed.
     */
    @Test
    public void addConfigurationSettingNullKey() {
        StepVerifier.create(client.addConfigurationSetting(null, null, "A Value"))
            .expectError(IllegalArgumentException.class)
            .verify();

        StepVerifier.create(client.addConfigurationSettingWithResponse(null))
            .expectError(NullPointerException.class)
            .verify();
    }

    /**
     * Tests that a configuration cannot be added twice with the same key. This should return a 412 error.
     */
    @Test
    public void addExistingSetting() {
        addExistingSettingRunner((expected) -> StepVerifier
            .create(client.addConfigurationSettingWithResponse(expected)
                .then(client.addConfigurationSettingWithResponse(expected)))
            .verifyErrorSatisfies(
                ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_PRECON_FAILED)));
    }

    /**
     * Tests that a configuration is able to be added or updated with set.
     * When the configuration is read-only updates cannot happen, this will result in a 409.
     */
    @Test
    public void setConfigurationSetting() {
        setConfigurationSettingRunner(
            (expected, update) -> StepVerifier.create(client.setConfigurationSettingWithResponse(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());
    }

    @Test
    public void setConfigurationSettingConvenience() {
        setConfigurationSettingRunner(
            (expected, update) -> StepVerifier.create(client.setConfigurationSetting(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());
    }

    @Test
    public void setFeatureFlagConfigurationSettingConvenience() {
        setFeatureFlagConfigurationSettingRunner(
            (expected, update) -> StepVerifier.create(client.setConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete());
    }

    @Test
    public void featureFlagConfigurationSettingUnknownAttributesArePreserved() {
        featureFlagConfigurationSettingUnknownAttributesArePreservedRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.setConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.getConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.getConfigurationSetting(expected))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    @Test
    public void setSecretReferenceConfigurationSettingConvenience() {
        setSecretReferenceConfigurationSettingRunner(
            (expected, update) -> StepVerifier.create(client.setConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete());
    }

    @Test
    public void secretReferenceConfigurationSettingUnknownAttributesArePreserved() {
        secretReferenceConfigurationSettingUnknownAttributesArePreservedRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.setConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.getConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();
            StepVerifier.create(client.getConfigurationSetting(expected))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    /**
     * Tests that when an ETag is passed to set it will only set if the current representation of the setting has the
     * ETag. If the set ETag doesn't match anything the update won't happen, this will result in a 412. This will
     * prevent set from doing an add as well.
     */
    @Test
    public void setConfigurationSettingIfETag() {
        setConfigurationSettingIfETagRunner((initial, update) -> {
            // This ETag is not the correct format. It is not the correct hash that the service is expecting.
            StepVerifier.create(client.setConfigurationSettingWithResponse(initial.setETag("badEtag"), true))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_PRECON_FAILED));

            StepVerifier
                .create(client.addConfigurationSettingWithResponse(initial)
                    .map(Response::getValue)
                    .flatMap(val -> client.setConfigurationSettingWithResponse(update.setETag(val.getETag()), true)))
                .assertNext(response -> assertConfigurationEquals(update, response))
                .verifyComplete();

            StepVerifier.create(client.setConfigurationSettingWithResponse(initial, true))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_PRECON_FAILED));

            StepVerifier.create(client.getConfigurationSettingWithResponse(update, null, false))
                .assertNext(response -> assertConfigurationEquals(update, response))
                .verifyComplete();
        });
    }

    /**
     * Tests that we cannot set a configuration setting when the key is an empty string.
     */
    @Test
    public void setConfigurationSettingEmptyKey() {
        StepVerifier.create(client.setConfigurationSetting("", NO_LABEL, "A value"))
            .verifyErrorSatisfies(ex -> assertRestException(ex, HttpURLConnection.HTTP_BAD_METHOD));
    }

    /**
     * Tests that we can set configuration settings when value is not null or an empty string.
     * Value is not a required property.
     */
    @Test
    public void setConfigurationSettingEmptyValue() {
        setConfigurationSettingEmptyValueRunner((setting) -> {
            StepVerifier.create(client.setConfigurationSetting(setting.getKey(), NO_LABEL, setting.getValue()))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSetting(setting.getKey(), setting.getLabel(), null))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();
        });
    }

    /**
     * Verifies that an exception is thrown when null key is passed.
     */
    @Test
    public void setConfigurationSettingNullKey() {

        StepVerifier.create(client.setConfigurationSetting(null, NO_LABEL, "A Value"))
            .verifyError(IllegalArgumentException.class);
        StepVerifier.create(client.setConfigurationSettingWithResponse(null, false))
            .verifyError(NullPointerException.class);
    }

    /**
     * Tests that a configuration is able to be retrieved when it exists, whether or not it is read-only.
     */
    @Test
    public void getConfigurationSetting() {
        getConfigurationSettingRunner((expected) -> StepVerifier
            .create(client.addConfigurationSettingWithResponse(expected)
                .then(client.getConfigurationSettingWithResponse(expected, null, false)))
            .assertNext(response -> assertConfigurationEquals(expected, response))
            .verifyComplete());
    }

    @Test
    public void getConfigurationSettingConvenience() {
        getConfigurationSettingRunner((expected) -> StepVerifier
            .create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
            .assertNext(response -> assertConfigurationEquals(expected, response))
            .verifyComplete());
    }

    @Test
    public void getFeatureFlagConfigurationSettingConvenience() {
        getFeatureFlagConfigurationSettingRunner((expected) -> StepVerifier
            .create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
            .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                (FeatureFlagConfigurationSetting) response))
            .verifyComplete());
    }

    @Test
    public void getSecretReferenceConfigurationSettingConvenience() {
        getSecretReferenceConfigurationSettingRunner((expected) -> StepVerifier
            .create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
            .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                (SecretReferenceConfigurationSetting) response))
            .verifyComplete());
    }

    /**
     * Tests that attempting to retrieve a non-existent configuration doesn't work, this will result in a 404.
     */
    @Test
    public void getConfigurationSettingNotFound() {
        final String key = getKey();
        final ConfigurationSetting neverRetrievedConfiguration
            = new ConfigurationSetting().setKey(key).setValue("myNeverRetreivedValue");
        final ConfigurationSetting nonExistentLabel
            = new ConfigurationSetting().setKey(key).setLabel("myNonExistentLabel");

        StepVerifier.create(client.addConfigurationSettingWithResponse(neverRetrievedConfiguration))
            .assertNext(response -> assertConfigurationEquals(neverRetrievedConfiguration, response))
            .verifyComplete();

        StepVerifier.create(client.getConfigurationSetting("myNonExistentKey", null, null))
            .verifyErrorSatisfies(
                ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));

        StepVerifier.create(client.getConfigurationSettingWithResponse(nonExistentLabel, null, false))
            .verifyErrorSatisfies(
                ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
    }

    /**
     * Tests that configurations are able to be deleted when they exist.
     * After the configuration has been deleted attempting to get it will result in a 404, the same as if the
     * configuration never existed.
     */
    @Test
    public void deleteConfigurationSetting() {
        deleteConfigurationSettingRunner((expected) -> {
            StepVerifier
                .create(client.addConfigurationSettingWithResponse(expected)
                    .then(client.getConfigurationSettingWithResponse(expected, null, false)))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            StepVerifier.create(client.deleteConfigurationSettingWithResponse(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSettingWithResponse(expected, null, false))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    @Test
    public void deleteConfigurationSettingConvenience() {
        deleteConfigurationSettingRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSetting(expected))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    @Test
    public void deleteFeatureFlagConfigurationSettingConvenience() {
        deleteFeatureFlagConfigurationSettingRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();

            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSetting(expected))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    @Test
    public void deleteSecretReferenceConfigurationSettingConvenience() {
        deleteSecretReferenceConfigurationSettingRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected).then(client.getConfigurationSetting(expected)))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();

            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSetting(expected))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    /**
     * Tests that attempting to delete a non-existent configuration will return a 204.
     */
    @Test
    public void deleteConfigurationSettingNotFound() {
        final String key = getKey();
        final ConfigurationSetting neverDeletedConfiguration
            = new ConfigurationSetting().setKey(key).setValue("myNeverDeletedValue");

        StepVerifier.create(client.addConfigurationSettingWithResponse(neverDeletedConfiguration))
            .assertNext(response -> assertConfigurationEquals(neverDeletedConfiguration, response))
            .verifyComplete();

        StepVerifier
            .create(client.deleteConfigurationSettingWithResponse(new ConfigurationSetting().setKey("myNonExistentKey"),
                false))
            .assertNext(response -> assertConfigurationEquals(null, response, HttpURLConnection.HTTP_NO_CONTENT))
            .verifyComplete();

        StepVerifier
            .create(client.deleteConfigurationSettingWithResponse(
                new ConfigurationSetting().setKey(neverDeletedConfiguration.getKey()).setLabel("myNonExistentLabel"),
                false))
            .assertNext(response -> assertConfigurationEquals(null, response, HttpURLConnection.HTTP_NO_CONTENT))
            .verifyComplete();

        StepVerifier
            .create(client.getConfigurationSetting(neverDeletedConfiguration.getKey(),
                neverDeletedConfiguration.getLabel(), null))
            .assertNext(response -> assertConfigurationEquals(neverDeletedConfiguration, response))
            .verifyComplete();
    }

    /**
     * Tests that when an ETag is passed to delete it will only delete if the current representation of the setting has the ETag.
     * If the delete ETag doesn't match anything the delete won't happen, this will result in a 412.
     */
    @Test
    public void deleteConfigurationSettingWithETag() {
        deleteConfigurationSettingWithETagRunner((initial, update) -> {
            final ConfigurationSetting initiallyAddedConfig
                = client.addConfigurationSettingWithResponse(initial).block().getValue();
            final ConfigurationSetting updatedConfig
                = client.setConfigurationSettingWithResponse(update, true).block().getValue();

            StepVerifier.create(client.getConfigurationSettingWithResponse(initial, null, false))
                .assertNext(response -> assertConfigurationEquals(update, response))
                .verifyComplete();

            StepVerifier.create(client.deleteConfigurationSettingWithResponse(initiallyAddedConfig, true))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_PRECON_FAILED));

            StepVerifier.create(client.deleteConfigurationSettingWithResponse(updatedConfig, true))
                .assertNext(response -> assertConfigurationEquals(update, response))
                .verifyComplete();

            StepVerifier.create(client.getConfigurationSettingWithResponse(initial, null, false))
                .verifyErrorSatisfies(
                    ex -> assertRestException(ex, HttpResponseException.class, HttpURLConnection.HTTP_NOT_FOUND));
        });
    }

    /**
     * Test the API will not make a delete call without having a key passed, an IllegalArgumentException should be thrown.
     */
    @Test
    public void deleteConfigurationSettingNullKey() {
        StepVerifier.create(client.deleteConfigurationSetting(null, null)).verifyError(IllegalArgumentException.class);
        StepVerifier.create(client.deleteConfigurationSettingWithResponse(null, false))
            .verifyError(NullPointerException.class);
    }

    /**
     * Tests assert that the setting can be deleted after clear read-only of the setting.
     */
    @Test
    public void clearReadOnly() {

        lockUnlockRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // read-only setting
            StepVerifier.create(client.setReadOnly(expected.getKey(), expected.getLabel(), true))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // unsuccessfully delete
            StepVerifier.create(client.deleteConfigurationSettingWithResponse(expected, false))
                .verifyErrorSatisfies(ex -> assertRestException(ex, HttpResponseException.class, 409));

            // clear read-only of setting and delete
            StepVerifier.create(client.setReadOnly(expected.getKey(), expected.getLabel(), false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // successfully deleted
            StepVerifier.create(client.deleteConfigurationSettingWithResponse(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();
        });
    }

    /**
     * Tests assert that the setting can be deleted after clear read-only of the setting.
     */
    @Test
    public void clearReadOnlyWithConfigurationSetting() {
        lockUnlockRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // read-only setting
            StepVerifier.create(client.setReadOnly(expected.getKey(), expected.getLabel(), true))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // unsuccessfully delete
            StepVerifier.create(client.deleteConfigurationSettingWithResponse(expected, false))
                .verifyErrorSatisfies(ex -> assertRestException(ex, HttpResponseException.class, 409));

            // clear read-only setting and delete
            StepVerifier.create(client.setReadOnlyWithResponse(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // successfully deleted
            StepVerifier.create(client.deleteConfigurationSettingWithResponse(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();
        });
    }

    @Test
    public void clearReadOnlyWithConfigurationSettingConvenience() {
        lockUnlockRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // read-only setting
            StepVerifier.create(client.setReadOnly(expected, true))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // unsuccessfully delete
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .verifyErrorSatisfies(ex -> assertRestException(ex, HttpResponseException.class, 409));

            // clear read-only setting and delete
            StepVerifier.create(client.setReadOnly(expected, false))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();

            // successfully deleted
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete();
        });
    }

    @Test
    public void clearReadOnlyWithFeatureFlagConfigurationSettingConvenience() {
        lockUnlockFeatureFlagRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();

            // read-only setting
            StepVerifier.create(client.setReadOnly(expected, true))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();

            // unsuccessfully delete
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .verifyErrorSatisfies(ex -> assertRestException(ex, HttpResponseException.class, 409));

            // clear read-only setting and delete
            StepVerifier.create(client.setReadOnly(expected, false))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();

            // successfully deleted
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertFeatureFlagConfigurationSettingEquals(expected,
                    (FeatureFlagConfigurationSetting) response))
                .verifyComplete();
        });
    }

    @Test
    public void clearReadOnlyWithSecretReferenceConfigurationSettingConvenience() {
        lockUnlockSecretReferenceRunner((expected) -> {
            StepVerifier.create(client.addConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();

            // read-only setting
            StepVerifier.create(client.setReadOnly(expected, true))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();

            // unsuccessfully delete
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .verifyErrorSatisfies(ex -> assertRestException(ex, HttpResponseException.class, 409));

            // clear read-only setting and delete
            StepVerifier.create(client.setReadOnly(expected, false))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();

            // successfully deleted
            StepVerifier.create(client.deleteConfigurationSetting(expected))
                .assertNext(response -> assertSecretReferenceConfigurationSettingEquals(expected,
                    (SecretReferenceConfigurationSetting) response))
                .verifyComplete();
        });
    }

    /**
     * Verifies that a ConfigurationSetting can be added with a label, and that we can fetch that ConfigurationSetting
     * from the service when filtering by either its label or just its key.
     */
    @Test
    public void listWithKeyAndLabel() {
        final String value = "myValue";
        final String key = testResourceNamer.randomName(keyPrefix, 16);
        final String label = testResourceNamer.randomName("lbl", 8);
        final ConfigurationSetting expected = new ConfigurationSetting().setKey(key).setValue(value).setLabel(label);

        StepVerifier.create(client.setConfigurationSettingWithResponse(expected, false))
            .assertNext(response -> assertConfigurationEquals(expected, response))
            .verifyComplete();

        StepVerifier
            .create(client.listConfigurationSettings(new SettingSelector().setKeyFilter(key).setLabelFilter(label)))
            .assertNext(configurationSetting -> assertConfigurationEquals(expected, configurationSetting))
            .verifyComplete();

        StepVerifier.create(client.listConfigurationSettings(new SettingSelector().setKeyFilter(key)))
            .assertNext(configurationSetting -> assertConfigurationEquals(expected, configurationSetting))
            .verifyComplete();
    }

    /**
     * Verifies that ConfigurationSettings can be added and that we can fetch those ConfigurationSettings from the
     * service when filtering by their keys.
     */
    @Test
    public void listWithMultipleKeys() {
        String key = getKey();
        String key2 = getKey();

        listWithMultipleKeysRunner(key, key2, (setting, setting2) -> {
            List<ConfigurationSetting> selected = new ArrayList<>();

            StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();

            StepVerifier.create(client.addConfigurationSettingWithResponse(setting2))
                .assertNext(response -> assertConfigurationEquals(setting2, response))
                .verifyComplete();

            StepVerifier.create(client.listConfigurationSettings(new SettingSelector().setKeyFilter(key + "," + key2)))
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .verifyComplete();

            return selected;
        });
    }

    @Test
    public void listConfigurationSettingsWithNullSelector() {
        final String key = getKey();
        final String key2 = getKey();

        // Delete all existing settings in the resource
        StepVerifier.create(client.listConfigurationSettings(null)
            .flatMap(setting -> client.deleteConfigurationSettingWithResponse(setting, false))
            .then()).verifyComplete();

        listWithMultipleKeysRunner(key, key2, (setting, setting2) -> {
            List<ConfigurationSetting> selected = new ArrayList<>();
            StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();

            StepVerifier.create(client.addConfigurationSettingWithResponse(setting2))
                .assertNext(response -> assertConfigurationEquals(setting2, response))
                .verifyComplete();

            StepVerifier.create(client.listConfigurationSettings(null))
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .verifyComplete();
            assertEquals(2, selected.size());
            return selected;
        });
    }

    /**
     * Verifies that ConfigurationSettings can be added with different labels and that we can fetch those ConfigurationSettings
     * from the service when filtering by their labels.
     */
    @Test
    public void listWithMultipleLabels() {
        String key = getKey();
        String label = getLabel();
        String label2 = getLabel();

        listWithMultipleLabelsRunner(key, label, label2, (setting, setting2) -> {
            List<ConfigurationSetting> selected = new ArrayList<>();

            StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();

            StepVerifier.create(client.addConfigurationSettingWithResponse(setting2))
                .assertNext(response -> assertConfigurationEquals(setting2, response))
                .verifyComplete();

            StepVerifier
                .create(client.listConfigurationSettings(
                    new SettingSelector().setKeyFilter(key).setLabelFilter(label + "," + label2)))
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .verifyComplete();

            return selected;
        });
    }

    /**
     * Verifies that we can select filter results by key, label, and select fields using SettingSelector.
     */
    @Test
    public void listConfigurationSettingsSelectFields() {
        listConfigurationSettingsSelectFieldsRunner((settings, selector) -> {
            final List<Mono<Response<ConfigurationSetting>>> settingsBeingAdded = new ArrayList<>();
            for (ConfigurationSetting setting : settings) {
                settingsBeingAdded.add(client.setConfigurationSettingWithResponse(setting, false));
            }

            // Waiting for all the settings to be added.
            Flux.merge(settingsBeingAdded).blockLast();

            List<ConfigurationSetting> settingsReturned = new ArrayList<>();
            StepVerifier.create(client.listConfigurationSettings(selector))
                .assertNext(settingsReturned::add)
                .assertNext(settingsReturned::add)
                .verifyComplete();

            return settingsReturned;
        });
    }

    /**
     * Verifies that throws exception when using SettingSelector with not supported *a key filter.
     */
    @Test
    public void listConfigurationSettingsSelectFieldsWithPrefixStarKeyFilter() {
        filterValueTest("*" + getKey(), getLabel());
    }

    /**
     * Verifies that throws exception when using SettingSelector with not supported *a* key filter.
     */
    @Test
    public void listConfigurationSettingsSelectFieldsWithSubstringKeyFilter() {
        filterValueTest("*" + getKey() + "*", getLabel());
    }

    /**
     * Verifies that throws exception when using SettingSelector with not supported *a label filter.
     */
    @Test
    public void listConfigurationSettingsSelectFieldsWithPrefixStarLabelFilter() {
        filterValueTest(getKey(), "*" + getLabel());
    }

    /**
     * Verifies that throws exception when using SettingSelector with not supported *a* label filter.
     */
    @Test
    public void listConfigurationSettingsSelectFieldsWithSubstringLabelFilter() {
        filterValueTest(getKey(), "*" + getLabel() + "*");
    }

    /**
     * Verifies that we can get a ConfigurationSetting at the provided accept datetime
     */
    @Test
    public void listConfigurationSettingsAcceptDateTime() {
        final String keyName = testResourceNamer.randomName(keyPrefix, 16);
        final ConfigurationSetting original = new ConfigurationSetting().setKey(keyName).setValue("myValue");
        final ConfigurationSetting updated
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue");
        final ConfigurationSetting updated2
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue2");

        // Create 3 revisions of the same key.
        StepVerifier.create(client.setConfigurationSettingWithResponse(original, false))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();
        StepVerifier
            .create(client.setConfigurationSettingWithResponse(updated, false).delayElement(Duration.ofSeconds(2)))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .verifyComplete();
        StepVerifier.create(client.setConfigurationSettingWithResponse(updated2, false))
            .assertNext(response -> assertConfigurationEquals(updated2, response))
            .verifyComplete();

        // Gets all versions of this value so we can get the one we want at that particular date.
        List<ConfigurationSetting> revisions
            = client.listRevisions(new SettingSelector().setKeyFilter(keyName)).collectList().block();

        assertNotNull(revisions);
        assertEquals(3, revisions.size());

        // We want to fetch the configuration setting when we first updated its value.
        SettingSelector options
            = new SettingSelector().setKeyFilter(keyName).setAcceptDatetime(revisions.get(1).getLastModified());
        StepVerifier.create(client.listConfigurationSettings(options))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .verifyComplete();
    }

    /**
     * Verifies that we can get all of the revisions for this ConfigurationSetting. Then verifies that we can select
     * specific fields.
     */
    @Test
    public void listRevisions() {
        final String keyName = testResourceNamer.randomName(keyPrefix, 16);
        final ConfigurationSetting original = new ConfigurationSetting().setKey(keyName).setValue("myValue");
        final ConfigurationSetting updated
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue");
        final ConfigurationSetting updated2
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue2");

        // Create 3 revisions of the same key.
        StepVerifier.create(client.setConfigurationSettingWithResponse(original, false))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();
        StepVerifier.create(client.setConfigurationSettingWithResponse(updated, false))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .verifyComplete();
        StepVerifier.create(client.setConfigurationSettingWithResponse(updated2, false))
            .assertNext(response -> assertConfigurationEquals(updated2, response))
            .verifyComplete();

        // Get all revisions for a key, they are listed in descending order.
        StepVerifier.create(client.listRevisions(new SettingSelector().setKeyFilter(keyName)))
            .assertNext(response -> assertConfigurationEquals(updated2, response))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();

        // Verifies that we can select specific fields.
        StepVerifier
            .create(client.listRevisions(
                new SettingSelector().setKeyFilter(keyName).setFields(SettingFields.KEY, SettingFields.ETAG)))
            .assertNext(response -> validateListRevisions(updated2, response))
            .assertNext(response -> validateListRevisions(updated, response))
            .assertNext(response -> validateListRevisions(original, response))
            .verifyComplete();

        // Verifies that we have revision list size greater than 0. The count number of revision changes.
        StepVerifier.create(client.listRevisions(null).count())
            .assertNext(count -> assertTrue(count > 0))
            .verifyComplete();
    }

    /**
     * Verifies that we can get all the revisions for all settings with the specified keys.
     */
    @Test
    public void listRevisionsWithMultipleKeys() {
        String key = getKey();
        String key2 = getKey();

        listRevisionsWithMultipleKeysRunner(key, key2, (testInput) -> {
            List<ConfigurationSetting> selected = new ArrayList<>();

            StepVerifier.create(client.addConfigurationSettingWithResponse(testInput.get(0)))
                .assertNext(response -> assertConfigurationEquals(testInput.get(0), response))
                .verifyComplete();

            StepVerifier.create(client.setConfigurationSettingWithResponse(testInput.get(1), false))
                .assertNext(response -> assertConfigurationEquals(testInput.get(1), response))
                .verifyComplete();

            StepVerifier.create(client.addConfigurationSettingWithResponse(testInput.get(2)))
                .assertNext(response -> assertConfigurationEquals(testInput.get(2), response))
                .verifyComplete();

            StepVerifier.create(client.setConfigurationSettingWithResponse(testInput.get(3), false))
                .assertNext(response -> assertConfigurationEquals(testInput.get(3), response))
                .verifyComplete();

            StepVerifier.create(client.listRevisions(new SettingSelector().setKeyFilter(key + "," + key2)))
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .verifyComplete();

            return selected;
        });
    }

    /**
     * Verifies that we can get all revisions for all settings with the specified labels.
     */
    @Test
    public void listRevisionsWithMultipleLabels() {
        String key = getKey();
        String label = getLabel();
        String label2 = getLabel();

        listRevisionsWithMultipleLabelsRunner(key, label, label2, (testInput) -> {
            List<ConfigurationSetting> selected = new ArrayList<>();

            StepVerifier.create(client.addConfigurationSettingWithResponse(testInput.get(0)))
                .assertNext(response -> assertConfigurationEquals(testInput.get(0), response))
                .verifyComplete();

            StepVerifier.create(client.setConfigurationSettingWithResponse(testInput.get(1), false))
                .assertNext(response -> assertConfigurationEquals(testInput.get(1), response))
                .verifyComplete();

            StepVerifier.create(client.addConfigurationSettingWithResponse(testInput.get(2)))
                .assertNext(response -> assertConfigurationEquals(testInput.get(2), response))
                .verifyComplete();

            StepVerifier.create(client.setConfigurationSettingWithResponse(testInput.get(3), false))
                .assertNext(response -> assertConfigurationEquals(testInput.get(3), response))
                .verifyComplete();

            StepVerifier
                .create(
                    client.listRevisions(new SettingSelector().setKeyFilter(key).setLabelFilter(label + "," + label2)))
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .consumeNextWith(selected::add)
                .verifyComplete();

            return selected;
        });
    }

    /**
     * Verifies that we can get a subset of revisions based on the "acceptDateTime"
     */
    @Test
    public void listRevisionsAcceptDateTime() {
        final String keyName = testResourceNamer.randomName(keyPrefix, 16);
        final ConfigurationSetting original = new ConfigurationSetting().setKey(keyName).setValue("myValue");
        final ConfigurationSetting updated
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue");
        final ConfigurationSetting updated2
            = new ConfigurationSetting().setKey(original.getKey()).setValue("anotherValue2");

        // Create 3 revisions of the same key.
        StepVerifier.create(client.setConfigurationSettingWithResponse(original, false))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();
        StepVerifier
            .create(client.setConfigurationSettingWithResponse(updated, false).delayElement(Duration.ofSeconds(2)))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .verifyComplete();
        StepVerifier.create(client.setConfigurationSettingWithResponse(updated2, false))
            .assertNext(response -> assertConfigurationEquals(updated2, response))
            .verifyComplete();

        // Gets all versions of this value.
        List<ConfigurationSetting> revisions
            = client.listRevisions(new SettingSelector().setKeyFilter(keyName)).collectList().block();

        assertNotNull(revisions);
        assertEquals(3, revisions.size());

        // We want to fetch all the revisions that existed up and including when the first revision was created.
        // Revisions are returned in descending order from creation date.
        SettingSelector options
            = new SettingSelector().setKeyFilter(keyName).setAcceptDatetime(revisions.get(1).getLastModified());
        StepVerifier.create(client.listRevisions(options))
            .assertNext(response -> assertConfigurationEquals(updated, response))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();
    }

    /**
     * Verifies that, given a ton of revisions, we can list the revisions ConfigurationSettings using pagination
     * (ie. where 'nextLink' has a URL pointing to the next page of results.)
     */
    @Test
    public void listRevisionsWithPagination() {
        final int numberExpected = 50;
        List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
        for (int value = 0; value < numberExpected; value++) {
            settings
                .add(new ConfigurationSetting().setKey(keyPrefix).setValue("myValue" + value).setLabel(labelPrefix));
        }

        for (ConfigurationSetting setting : settings) {
            StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
        }

        SettingSelector filter = new SettingSelector().setKeyFilter(keyPrefix).setLabelFilter(labelPrefix);
        StepVerifier.create(client.listRevisions(filter)).expectNextCount(numberExpected).verifyComplete();
    }

    /**
     * Verifies that, given a ton of revisions, we can list the revisions ConfigurationSettings using pagination and stream is invoked multiple times.
     * (ie. where 'nextLink' has a URL pointing to the next page of results.)
     */
    @Test
    public void listRevisionsWithPaginationAndRepeatStream() {
        final int numberExpected = 50;
        List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
        for (int value = 0; value < numberExpected; value++) {
            ConfigurationSetting setting
                = new ConfigurationSetting().setKey(keyPrefix).setValue("myValue" + value).setLabel(labelPrefix);
            settings.add(setting);
            StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
        }

        SettingSelector filter = new SettingSelector().setKeyFilter(keyPrefix).setLabelFilter(labelPrefix);

        PagedFlux<ConfigurationSetting> configurationSettingPagedFlux = client.listRevisions(filter);
        StepVerifier.create(configurationSettingPagedFlux.count())
            .assertNext(count -> assertEquals(numberExpected, count))
            .verifyComplete();

        StepVerifier.create(configurationSettingPagedFlux.count())
            .assertNext(count -> assertEquals(numberExpected, count))
            .verifyComplete();
    }

    /**
     * Verifies that, given a ton of revisions, we can list the revisions ConfigurationSettings using pagination and stream is invoked multiple times.
     * (ie. where 'nextLink' has a URL pointing to the next page of results.)
     */
    @Test
    public void listRevisionsWithPaginationAndRepeatIterator() {
        final int numberExpected = 50;
        List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
        for (int value = 0; value < numberExpected; value++) {
            ConfigurationSetting setting
                = new ConfigurationSetting().setKey(keyPrefix).setValue("myValue" + value).setLabel(labelPrefix);
            settings.add(setting);
            StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
        }

        SettingSelector filter = new SettingSelector().setKeyFilter(keyPrefix).setLabelFilter(labelPrefix);

        PagedFlux<ConfigurationSetting> configurationSettingPagedFlux = client.listRevisions(filter);
        StepVerifier.create(configurationSettingPagedFlux.count())
            .assertNext(count -> assertEquals(numberExpected, count))
            .verifyComplete();

        StepVerifier.create(configurationSettingPagedFlux.count())
            .assertNext(count -> assertEquals(numberExpected, count))
            .verifyComplete();
    }

    /**
     * Verifies that, given a ton of existing settings, we can list the ConfigurationSettings using pagination
     * (ie. where 'nextLink' has a URL pointing to the next page of results.
     */
    @Disabled("Error code 403 TOO_MANY_REQUESTS https://github.com/Azure/azure-sdk-for-java/issues/36602")
    @Test
    public void listConfigurationSettingsWithPagination() {
        final int numberExpected = 50;
        List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
        for (int value = 0; value < numberExpected; value++) {
            settings.add(
                new ConfigurationSetting().setKey(keyPrefix + "-" + value).setValue("myValue").setLabel(labelPrefix));
        }

        for (ConfigurationSetting setting : settings) {
            StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
        }

        SettingSelector filter = new SettingSelector().setKeyFilter(keyPrefix + "-*").setLabelFilter(labelPrefix);

        StepVerifier.create(client.listConfigurationSettings(filter)).expectNextCount(numberExpected).verifyComplete();
    }

    /**
     * Verifies the conditional "GET" scenario where the setting has yet to be updated, resulting in a 304. This GET
     * scenario will return a setting when the ETag provided does not match the one of the current setting.
     */
    @Test
    public void getConfigurationSettingWhenValueNotUpdated() {
        final String key = getKey();
        final ConfigurationSetting expected = new ConfigurationSetting().setKey(key).setValue("myValue");
        final ConfigurationSetting newExpected = new ConfigurationSetting().setKey(key).setValue("myNewValue");
        final ConfigurationSetting block = client.addConfigurationSettingWithResponse(expected).block().getValue();

        assertNotNull(block);
        assertConfigurationEquals(expected, block);

        // conditional get, now the setting has not be updated yet, resulting 304 and null value
        StepVerifier.create(client.getConfigurationSettingWithResponse(block, null, true))
            .assertNext(response -> assertConfigurationEquals(null, response, 304))
            .verifyComplete();

        StepVerifier.create(client.setConfigurationSettingWithResponse(newExpected, false))
            .assertNext(response -> assertConfigurationEquals(newExpected, response))
            .verifyComplete();

        // conditional get, now the setting is updated and we are able to get a new setting with 200 code
        StepVerifier.create(client.getConfigurationSettingWithResponse(block, null, true))
            .assertNext(response -> assertConfigurationEquals(newExpected, response))
            .verifyComplete();
    }

    @Test
    public void deleteAllSettings() {
        client.listConfigurationSettings(new SettingSelector().setKeyFilter("*")).flatMap(configurationSetting -> {
            LOGGER.info("Deleting key:label [{}:{}]. isReadOnly? {}", configurationSetting.getKey(),
                configurationSetting.getLabel(), configurationSetting.isReadOnly());
            return client.deleteConfigurationSettingWithResponse(configurationSetting, false);
        }).blockLast();
    }

    @Test
    public void addHeadersFromContextPolicyTest() {
        final HttpHeaders headers = getCustomizedHeaders();
        addHeadersFromContextPolicyRunner(expected -> StepVerifier
            .create(client.addConfigurationSettingWithResponse(expected)
                .contextWrite(Context.of(AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY, headers)))
            .assertNext(response -> {
                final HttpHeaders requestHeaders = response.getRequest().getHeaders();
                assertContainsHeaders(headers, requestHeaders);
            })
            .verifyComplete());
    }

    @Test
    public void createSnapshot() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void getSnapshot() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Retrieve a snapshot after creation
            StepVerifier
                .create(client.getSnapshotWithResponse(name,
                    Arrays.asList(SnapshotFields.NAME, SnapshotFields.STATUS, SnapshotFields.FILTERS)))
                .assertNext(getSnapshot -> {
                    assertEquals(200, getSnapshot.getStatusCode());

                    ConfigurationSnapshot actualSnapshot = getSnapshot.getValue();
                    assertEquals(name, actualSnapshot.getName());
                    assertEquals(ConfigurationSnapshotStatus.READY, actualSnapshot.getStatus());
                    assertEqualsSnapshotFilters(filters, actualSnapshot.getFilters());
                    assertNull(actualSnapshot.getSnapshotComposition());
                    assertNull(actualSnapshot.getRetentionPeriod());
                    assertNull(actualSnapshot.getCreatedAt());
                    assertNull(actualSnapshot.getItemCount());
                    assertNull(actualSnapshot.getSizeInBytes());
                    assertNull(actualSnapshot.getETag());
                })
                .verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void getSnapshotConvenience() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Retrieve a snapshot after creation
            StepVerifier.create(client.getSnapshot(name))
                .assertNext(getSnapshot -> assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY,
                    filters, SnapshotComposition.KEY, MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, getSnapshot))
                .verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void archiveSnapshot() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier
                .create(client.archiveSnapshotWithResponse(snapshotResult.getName(),
                    new MatchConditions().setIfMatch(snapshotResult.getETag())))
                .assertNext(
                    response -> assertConfigurationSnapshotWithResponse(200, name, ConfigurationSnapshotStatus.ARCHIVED,
                        filters, SnapshotComposition.KEY, MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, response))
                .verifyComplete();
        });
    }

    @Test
    public void archiveSnapshotConvenience() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.ARCHIVED,
                    filters, SnapshotComposition.KEY, MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, response))
                .verifyComplete();
        });
    }

    @Test
    public void recoverSnapshot() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();

            // Recover the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier
                .create(client.recoverSnapshotWithResponse(snapshotResult.getName(),
                    new MatchConditions().setIfMatch(snapshotResult.getETag())))
                .assertNext(
                    response -> assertConfigurationSnapshotWithResponse(200, name, ConfigurationSnapshotStatus.READY,
                        filters, SnapshotComposition.KEY, MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, response))
                .verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void recoverSnapshotConvenience() {
        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();

            // Recover the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.recoverSnapshot(name))
                .assertNext(response -> assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY,
                    filters, SnapshotComposition.KEY, MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, response))
                .verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void listSnapshots() {

        client.listSnapshots(new SnapshotSelector().setStatus(ConfigurationSnapshotStatus.READY))
            .flatMap(existSnapshot -> client.archiveSnapshot(existSnapshot.getName()))
            .blockLast();

        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        List<ConfigurationSnapshot> readySnapshots = new ArrayList<>();
        // Create first snapshot
        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();
            readySnapshots.add(snapshotResult);

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);
        });
        // Create second snapshot
        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });

        // readySnapshots contains only 1 snapshot
        ConfigurationSnapshot readySnapshot = readySnapshots.get(0);
        // List only the snapshot with a specific name
        StepVerifier.create(client.listSnapshots(new SnapshotSelector().setNameFilter(readySnapshot.getName())))
            .assertNext(snapshotWithName -> {
                assertEquals(readySnapshot.getName(), snapshotWithName.getName());
                assertEquals(readySnapshot.getStatus(), snapshotWithName.getStatus());
            })
            .verifyComplete();

        // Archived the snapshot, it will be deleted automatically when retention period expires.
        StepVerifier.create(client.archiveSnapshot(readySnapshot.getName()))
            .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
            .verifyComplete();

    }

    @Test
    public void listSnapshotsWithFields() {

        client.listSnapshots(new SnapshotSelector().setStatus(ConfigurationSnapshotStatus.READY))
            .flatMap(existSnapshot -> client.archiveSnapshot(existSnapshot.getName()))
            .blockLast();

        // Prepare a setting before creating a snapshot
        addConfigurationSettingRunner(
            (expected) -> StepVerifier.create(client.addConfigurationSettingWithResponse(expected))
                .assertNext(response -> assertConfigurationEquals(expected, response))
                .verifyComplete());

        List<ConfigurationSnapshot> readySnapshots = new ArrayList<>();
        // Create first snapshot
        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();
            readySnapshots.add(snapshotResult);

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);
        });
        // Create second snapshot
        createSnapshotRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 1000L, 0L, null, snapshotResult);

            // Archived the snapshot
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });

        // readySnapshots contains only 1 snapshot
        ConfigurationSnapshot readySnapshot = readySnapshots.get(0);
        // List only the snapshot with a specific name
        StepVerifier
            .create(client.listSnapshots(new SnapshotSelector().setNameFilter(readySnapshot.getName())
                .setFields(SnapshotFields.NAME, SnapshotFields.FILTERS, SnapshotFields.STATUS)))
            .assertNext(snapshotFieldFiltered -> {
                assertEquals(readySnapshot.getName(), snapshotFieldFiltered.getName());
                assertNotNull(snapshotFieldFiltered.getFilters());
                assertEquals(readySnapshot.getStatus(), snapshotFieldFiltered.getStatus());
                assertNull(snapshotFieldFiltered.getETag());
                assertNull(snapshotFieldFiltered.getSnapshotComposition());
                assertNull(snapshotFieldFiltered.getItemCount());
                assertNull(snapshotFieldFiltered.getRetentionPeriod());
                assertNull(snapshotFieldFiltered.getSizeInBytes());
                assertNull(snapshotFieldFiltered.getCreatedAt());
                assertNull(snapshotFieldFiltered.getExpiresAt());
                assertNull(snapshotFieldFiltered.getTags());
            })
            .verifyComplete();

        // Archived the snapshot, it will be deleted automatically when retention period expires.
        StepVerifier.create(client.archiveSnapshot(readySnapshot.getName()))
            .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
            .verifyComplete();
    }

    @Test
    public void listSettingFromSnapshot() {

        // Create a snapshot
        createSnapshotRunner((name, filters) -> {
            // Prepare 5 settings before creating a snapshot
            final int numberExpected = 5;
            List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
            for (int value = 0; value < numberExpected; value++) {
                settings.add(new ConfigurationSetting().setKey(name + "-" + value));
            }
            for (ConfigurationSetting setting : settings) {
                StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
            }
            SettingSelector filter = new SettingSelector().setKeyFilter(name + "-*");
            StepVerifier.create(client.listConfigurationSettings(filter))
                .expectNextCount(numberExpected)
                .verifyComplete();

            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 15000L, (long) numberExpected, null, snapshotResult);

            StepVerifier.create(client.listConfigurationSettingsForSnapshot(name))
                .expectNextCount(numberExpected)
                .verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void listSettingFromSnapshotWithFields() {
        // Create a snapshot
        createSnapshotRunner((name, filters) -> {
            // Prepare 5 settings before creating a snapshot
            final int numberExpected = 5;
            List<ConfigurationSetting> settings = new ArrayList<>(numberExpected);
            for (int value = 0; value < numberExpected; value++) {
                settings.add(new ConfigurationSetting().setKey(name + "-" + value).setValue(value + "-" + name));
            }
            for (ConfigurationSetting setting : settings) {
                StepVerifier.create(client.setConfigurationSetting(setting)).expectNextCount(1).verifyComplete();
            }
            SettingSelector filter = new SettingSelector().setKeyFilter(name + "-*");
            StepVerifier.create(client.listConfigurationSettings(filter))
                .expectNextCount(numberExpected)
                .verifyComplete();

            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();

            assertEqualsConfigurationSnapshot(name, ConfigurationSnapshotStatus.READY, filters, SnapshotComposition.KEY,
                MINIMUM_RETENTION_PERIOD, 15000L, (long) numberExpected, null, snapshotResult);

            StepVerifier.create(client.listConfigurationSettingsForSnapshot(name,
                Arrays.asList(SettingFields.KEY, SettingFields.VALUE))).assertNext(setting -> {
                    assertNotNull(setting.getKey());
                    assertNotNull(setting.getValue());
                    assertNull(setting.getLabel());
                    assertNull(setting.getContentType());
                    assertNull(setting.getLastModified());
                    assertNull(setting.getETag());
                    assertFalse(setting.isReadOnly());
                    assertTrue(setting.getTags().isEmpty());
                }).expectNextCount(numberExpected - 1).verifyComplete();

            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    @Test
    public void listSettingsWithPageETag() {
        // Step 1: Prepare testing data.
        // Clean all existing settings before this test purpose
        client.listConfigurationSettings(null)
            .flatMap(configurationSetting -> client.deleteConfigurationSetting(configurationSetting))
            .blockLast();

        // Add a few setting to form a page of settings
        final ConfigurationSetting setting = new ConfigurationSetting().setKey(getKey()).setValue("value");
        final ConfigurationSetting setting2 = new ConfigurationSetting().setKey(getKey()).setValue("value");
        client.setConfigurationSetting(setting).block();
        client.setConfigurationSetting(setting2).block();
        // Get all page ETags
        List<MatchConditions> matchConditionsList = new ArrayList<>();
        PagedResponse<ConfigurationSetting> pagedResponse = client.listConfigurationSettings(null).byPage().blockLast();
        matchConditionsList
            .add(new MatchConditions().setIfNoneMatch(pagedResponse.getHeaders().getValue(HttpHeaderName.ETAG)));

        // Step 2: Test list settings with page ETag
        // Validation 1: Validate all pages are not modified and return empty list of settings in each page response.
        // List settings with page ETag
        StepVerifier
            .create(client.listConfigurationSettings(new SettingSelector().setMatchConditions(matchConditionsList))
                .byPage())
            .assertNext(response -> {
                // No changes on the server side, so the response should be empty list
                assertEquals(0, response.getValue().size());
            })
            .verifyComplete();
        // Validation 2: validate the page has the updated setting should be returned
        // Update a setting
        final ConfigurationSetting updatedSetting
            = new ConfigurationSetting().setKey(setting.getKey()).setValue("new value");
        client.setConfigurationSetting(updatedSetting).block();
        // List settings with expired page ETag
        StepVerifier
            .create(client.listConfigurationSettings(new SettingSelector().setMatchConditions(matchConditionsList))
                .byPage())
            .assertNext(response -> {
                // The page has the updated setting should be returned, so the response should not be empty list
                assertFalse(response.getValue().isEmpty());
                // find the updated setting in the list
                ConfigurationSetting updatedSettingFromResponse = response.getValue()
                    .stream()
                    .filter(s -> s.getKey().equals(updatedSetting.getKey()))
                    .findAny()
                    .get();
                assertConfigurationEquals(updatedSetting, updatedSettingFromResponse);
            })
            .verifyComplete();
    }

    @Test
    public void listLabels() {
        // Clean all existing settings before this test purpose
        StepVerifier.create(client.listConfigurationSettings(null)
            .flatMap(setting -> client.deleteConfigurationSettingWithResponse(setting, false))
            .then()).verifyComplete();
        // Prepare two settings with different labels
        List<ConfigurationSetting> preparedSettings
            = listLabelsRunner(setting -> StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete());
        ConfigurationSetting setting = preparedSettings.get(0);
        ConfigurationSetting setting2 = preparedSettings.get(1);
        // List only the first label var, 'label'
        String label = setting.getLabel();
        StepVerifier.create(client.listLabels(new SettingLabelSelector().setNameFilter(label)))
            .assertNext(actual -> assertEquals(label, actual.getName()))
            .verifyComplete();
        // List labels with wildcard label filter
        String label2 = setting2.getLabel();
        StepVerifier.create(client.listLabels(new SettingLabelSelector().setNameFilter("label*"))
            .map(SettingLabel::getName)
            .collectList()).assertNext(actualLabels -> {
                assertTrue(actualLabels.contains(label));
                assertTrue(actualLabels.contains(label2));
            }).verifyComplete();
        // List all labels
        List<SettingLabel> selected = new ArrayList<>();
        StepVerifier.create(client.listLabels())
            .consumeNextWith(selected::add)
            .consumeNextWith(selected::add)
            .verifyComplete();
        assertTrue(selected.size() >= 2);
    }

    @Test
    public void listSettingByTagsFilter() {
        // Clean all existing settings before this test purpose
        StepVerifier.create(client.listConfigurationSettings(null)
            .flatMap(setting -> client.deleteConfigurationSettingWithResponse(setting, false))
            .then()).verifyComplete();

        // Prepare two settings with different tags
        List<ConfigurationSetting> preparedSettings = listSettingByTagsFilterRunner(setting -> {
            StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();
        });
        ConfigurationSetting setting = preparedSettings.get(0);
        ConfigurationSetting setting2 = preparedSettings.get(1);

        // List setting by first tags filter, it should return all settings
        StepVerifier
            .create(client.listConfigurationSettings(
                new SettingSelector().setTagsFilter(getTagsFilterInString(setting.getTags()))))
            .assertNext(response -> assertConfigurationEquals(setting, response))
            .assertNext(response -> assertConfigurationEquals(setting2, response))
            .verifyComplete();
        // List setting by second tags filter, it should return only one setting
        StepVerifier
            .create(client.listConfigurationSettings(
                new SettingSelector().setTagsFilter(getTagsFilterInString(setting2.getTags()))))
            .assertNext(response -> assertConfigurationEquals(setting2, response))
            .verifyComplete();
    }

    @Test
    public void listRevisionsWithTagsFilter() {
        // Create 3 revisions of the same key.
        List<ConfigurationSetting> configurationSettings = listRevisionsWithTagsFilterRunner(setting -> {
            StepVerifier.create(client.setConfigurationSettingWithResponse(setting, false))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();
        });

        ConfigurationSetting original = configurationSettings.get(0);

        // Get all revisions for a key with tags filter, they are listed in descending order.
        StepVerifier
            .create(client.listRevisions(new SettingSelector().setKeyFilter(original.getKey())
                .setTagsFilter(getTagsFilterInString(original.getTags()))))
            .assertNext(response -> assertConfigurationEquals(original, response))
            .verifyComplete();
    }

    @Test
    public void createSnapshotWithTagsFilter() {
        // Clean all existing settings before this test purpose
        StepVerifier.create(client.listConfigurationSettings(null)
            .flatMap(setting -> client.deleteConfigurationSettingWithResponse(setting, false))
            .then()).verifyComplete();

        // Prepare settings before creating a snapshot
        List<ConfigurationSetting> settings = createSnapshotWithTagsFilterPrepareRunner(setting -> {
            StepVerifier.create(client.addConfigurationSettingWithResponse(setting))
                .assertNext(response -> assertConfigurationEquals(setting, response))
                .verifyComplete();
        });
        ConfigurationSetting setting = settings.get(0);
        ConfigurationSetting settingWithTag = settings.get(1);
        assertTrue(setting.getTags().isEmpty());
        assertFalse(settingWithTag.getTags().isEmpty());

        createSnapshotWithTagsFilterRunner((name, filters) -> {
            // Retention period can be setup when creating a snapshot and cannot edit.
            ConfigurationSnapshot snapshot
                = new ConfigurationSnapshot(filters).setRetentionPeriod(MINIMUM_RETENTION_PERIOD);
            SyncPoller<PollOperationDetails, ConfigurationSnapshot> poller
                = client.beginCreateSnapshot(name, snapshot).getSyncPoller();
            poller.setPollInterval(interceptorManager.isPlaybackMode() ? Duration.ofMillis(1) : Duration.ofSeconds(10));
            poller.waitForCompletion();
            ConfigurationSnapshot snapshotResult = poller.getFinalResult();
            assertEquals(name, snapshotResult.getName());

            // The snapshot should only contain the setting with tags
            StepVerifier.create(client.listConfigurationSettingsForSnapshot(name))
                .assertNext(actual -> assertEquals(settingWithTag.getTags(), actual.getTags()))
                .verifyComplete();
            // Archived the snapshot, it will be deleted automatically when retention period expires.
            StepVerifier.create(client.archiveSnapshot(name))
                .assertNext(response -> assertEquals(ConfigurationSnapshotStatus.ARCHIVED, response.getStatus()))
                .verifyComplete();
        });
    }

    /**
     * Test helper that calling list configuration setting with given key and label input
     *
     * @param keyFilter key filter expression
     * @param labelFilter label filter expression
     */
    private void filterValueTest(String keyFilter, String labelFilter) {
        listConfigurationSettingsSelectFieldsWithNotSupportedFilterRunner(keyFilter, labelFilter,
            selector -> StepVerifier.create(client.listConfigurationSettings(selector))
                .verifyError(HttpResponseException.class));
    }
}
