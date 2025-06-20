// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

import com.azure.autorest.customization.Customization;
import com.azure.autorest.customization.Editor;
import com.azure.autorest.customization.LibraryCustomization;
import com.azure.autorest.customization.PackageCustomization;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.javadoc.Javadoc;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import static com.github.javaparser.javadoc.description.JavadocDescription.parseText;

/**
 * Contains customizations for Azure Key Vault Administration code generation.
 */
public class AdministrationCustomizations extends Customization {
    @Override
    public void customize(LibraryCustomization libraryCustomization, Logger logger) {
        removeFiles(libraryCustomization.getRawEditor());
        customizeKeyVaultRoleScope(libraryCustomization);
        customizeServiceVersion(libraryCustomization);
        customizeImplClients(libraryCustomization);
        customizeModuleInfo(libraryCustomization.getRawEditor());
        customizePackageInfos(libraryCustomization.getRawEditor());
    }

    private static void removeFiles(Editor editor) {
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/KeyVaultAdministrationAsyncClient.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/KeyVaultAdministrationClient.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/KeyVaultAdministrationClientBuilder.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/KeyVaultServiceVersion.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/RoleAssignmentsAsyncClient.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/RoleAssignmentsClient.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/RoleDefinitionsAsyncClient.java");
        editor.removeFile("src/main/java/com/azure/security/keyvault/administration/RoleDefinitionsClient.java");
    }

    private static void customizeImplClients(LibraryCustomization libraryCustomization) {
        PackageCustomization packageCustomization = libraryCustomization.getPackage(
            "com.azure.security.keyvault.administration.implementation");

        // Make some implementation methods public to facilitate calling LROs:
        // KeyVaultAdministrationClientImpl
        packageCustomization.getClass("KeyVaultAdministrationClientImpl").customizeAst(ast ->
            ast.getClassByName("KeyVaultAdministrationClientImpl").ifPresent(clazz ->
                makeAllMethodsPublic(clazz, "fullBackupWithResponseAsync", "fullBackupWithResponse",
                    "preFullBackupWithResponseAsync", "preFullBackupWithResponse",
                    "preFullRestoreOperationWithResponseAsync", "preFullRestoreOperationWithResponse",
                    "fullRestoreOperationWithResponseAsync", "fullRestoreOperationWithResponse",
                    "selectiveKeyRestoreOperationWithResponseAsync", "selectiveKeyRestoreOperationWithResponse")));

        // RoleAssignmentsImpl
        packageCustomization.getClass("RoleAssignmentsImpl").customizeAst(ast ->
            ast.getClassByName("RoleAssignmentsImpl").ifPresent(clazz ->
                makeAllMethodsPublic(clazz, "listForScopeSinglePageAsync", "listForScopeNextSinglePageAsync")));

        // RoleDefinitionsImpl
        packageCustomization.getClass("RoleDefinitionsImpl").customizeAst(ast ->
            ast.getClassByName("RoleDefinitionsImpl").ifPresent(clazz ->
                makeAllMethodsPublic(clazz, "listSinglePageAsync", "listNextSinglePageAsync")));
    }

    private static void makeAllMethodsPublic(ClassOrInterfaceDeclaration clazz, String... methodNames) {
        for (String methodName : methodNames) {
            clazz.getMethodsByName(methodName).forEach(method -> method.setModifiers(Modifier.Keyword.PUBLIC));
        }
    }

    private static void customizeKeyVaultRoleScope(LibraryCustomization customization) {
        customization.getClass("com.azure.security.keyvault.administration.models", "KeyVaultRoleScope")
            .customizeAst(ast ->
                ast.addImport(IllegalArgumentException.class)
                    .addImport(URL.class)
                    .addImport(MalformedURLException.class)
                    .getClassByName("KeyVaultRoleScope")
                    .ifPresent(clazz -> {
                        clazz.addMethod("fromUrl", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                            .setType("KeyVaultRoleScope")
                            .addParameter("String", "url")
                            .setJavadocComment(new Javadoc(
                                parseText("Creates of finds a {@link KeyVaultRoleScope} from its string representation."))
                                .addBlockTag("param", "url", "A string representing a URL containing the name of the scope to look for.")
                                .addBlockTag("return", "The corresponding {@link KeyVaultRoleScope}.")
                                .addBlockTag("throws", "IllegalArgumentException", "If the given {@code url} is malformed."))
                            .setBody(StaticJavaParser.parseBlock("{ try { return fromString(new URL(url).getPath()); }"
                                + "catch (MalformedURLException e) { throw new IllegalArgumentException(e); } }"));

                        clazz.addMethod("fromUrl", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
                            .setType("KeyVaultRoleScope")
                            .addParameter("URL", "url")
                            .setJavadocComment(new Javadoc(
                                parseText("Creates of finds a {@link KeyVaultRoleScope} from its string representation."))
                                .addBlockTag("param", "url", "A URL containing the name of the scope to look for.")
                                .addBlockTag("return", "The corresponding {@link KeyVaultRoleScope}."))
                            .setBody(StaticJavaParser.parseBlock("{ return fromString(url.getPath()); }"));
                    }));
    }

    private static void customizeServiceVersion(LibraryCustomization customization) {
        CompilationUnit compilationUnit = new CompilationUnit();

        compilationUnit.addOrphanComment(new LineComment(" Copyright (c) Microsoft Corporation. All rights reserved."));
        compilationUnit.addOrphanComment(new LineComment(" Licensed under the MIT License."));
        compilationUnit.addOrphanComment(new LineComment(" Code generated by Microsoft (R) TypeSpec Code Generator."));

        compilationUnit.setPackageDeclaration("com.azure.security.keyvault.administration")
            .addImport("com.azure.core.util.ServiceVersion");

        EnumDeclaration enumDeclaration = compilationUnit.addEnum("KeyVaultAdministrationServiceVersion", Modifier.Keyword.PUBLIC)
            .addImplementedType("ServiceVersion")
            .setJavadocComment("The versions of Azure Key Vault supported by this client library.");

        for (String version : Arrays.asList("7.2", "7.3", "7.4", "7.5", "7.6")) {
            enumDeclaration.addEnumConstant("V" + version.replace('.', '_').replace('-', '_').toUpperCase())
                .setJavadocComment("Service version {@code " + version + "}.")
                .addArgument(new StringLiteralExpr(version));
        }

        enumDeclaration.addField("String", "version", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);

        enumDeclaration.addConstructor()
            .addParameter("String", "version")
            .setBody(StaticJavaParser.parseBlock("{ this.version = version; }"));

        enumDeclaration.addMethod("getVersion", Modifier.Keyword.PUBLIC)
            .setType("String")
            .setJavadocComment("{@inheritDoc}")
            .addMarkerAnnotation("Override")
            .setBody(StaticJavaParser.parseBlock("{ return this.version; }"));

        enumDeclaration.addMethod("getLatest", Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC)
            .setType("KeyVaultAdministrationServiceVersion")
            .setJavadocComment(new Javadoc(
                parseText("Gets the latest service version supported by this client library."))
                .addBlockTag("return", "The latest {@link KeyVaultAdministrationServiceVersion}."))
            .setBody(StaticJavaParser.parseBlock("{ return V7_6; }"));

        customization.getRawEditor()
            .addFile("src/main/java/com/azure/security/keyvault/administration/KeyVaultAdministrationServiceVersion.java",
                compilationUnit.toString());

        for (String impl : Arrays.asList("KeyVaultAdministrationClientImpl", "RoleAssignmentsImpl", "RoleDefinitionsImpl")) {
            String fileName = "src/main/java/com/azure/security/keyvault/administration/implementation/" + impl + ".java";
            String fileContent = customization.getRawEditor().getFileContent(fileName);
            fileContent = fileContent.replace("KeyVaultServiceVersion", "KeyVaultAdministrationServiceVersion");
            customization.getRawEditor().replaceFile(fileName, fileContent);
        }
    }

    private static void customizeModuleInfo(Editor editor) {
        editor.replaceFile("src/main/java/module-info.java", joinWithNewline(
            "// Copyright (c) Microsoft Corporation. All rights reserved.",
            "// Licensed under the MIT License.",
            "",
            "module com.azure.security.keyvault.administration {",
            "    requires transitive com.azure.core;",
            "",
            "    exports com.azure.security.keyvault.administration;",
            "    exports com.azure.security.keyvault.administration.models;",
            "",
            "    opens com.azure.security.keyvault.administration to com.azure.core;",
            "    opens com.azure.security.keyvault.administration.models to com.azure.core;",
            "    opens com.azure.security.keyvault.administration.implementation.models to com.azure.core;",
            "}"));
    }

    private static void customizePackageInfos(Editor editor) {
        editor.replaceFile("src/main/java/com/azure/security/keyvault/administration/package-info.java",
            joinWithNewline(
                "// Copyright (c) Microsoft Corporation. All rights reserved.",
                "// Licensed under the MIT License.",
                "",
                "/**",
                " * <!-- @formatter:off -->",
                " * <a href=\"https://learn.microsoft.com/azure/key-vault/managed-hsm/\">Azure Key Vault Managed HSM</a> is a",
                " * fully-managed, highly-available, single-tenant, standards-compliant cloud service that enables you to safeguard",
                " * cryptographic keys for your cloud applications using FIPS 140-2 Level 3 validated HSMs.",
                " *",
                " * <p>",
                " * The Azure Key Vault Administration client library allows developers to interact with the Azure Key Vault Managed",
                " * HSM service from their applications. The library provides a set of APIs that enable developers to perform",
                " * administrative tasks such as full backup/restore, key-level role-based access control (RBAC), and account settings",
                " * management.",
                " *",
                " * <p>",
                " * <strong>Key Concepts:</strong>",
                " *",
                " * <p>",
                " * <strong>What is a Key Vault Access Control Client?</strong>",
                " * <p>",
                " * The Key Vault Access Control client performs the interactions with the Azure Key Vault service for getting,",
                " * setting, deleting, and listing role assignments, as well as listing role definitions. Asynchronous",
                " * (KeyVaultAccessControlAsyncClient) and synchronous (KeyVaultAccessControlClient) clients exist in the SDK allowing",
                " * for the selection of a client based on an application's use case. Once you've initialized a role assignment, you can",
                " * interact with the primary resource types in Key Vault.",
                " *",
                " * <p>",
                " * <strong>What is a Role Definition?</strong>",
                " * <p>",
                " * A role definition is a collection of permissions. It defines the operations that can be performed, such as read,",
                " * write, and delete. It can also define the operations that are excluded from allowed operations.",
                " *",
                " * <p>",
                " * Role definitions can be listed and specified as part of a role assignment.",
                " *",
                " * <p>",
                " * <strong>What is a Role Assignment?</strong>",
                " * <p>",
                " * A role assignment is the association of a role definition to a service principal. They can be created, listed,",
                " * fetched individually, and deleted.",
                " *",
                " * <p>",
                " * <strong>What is a Key Vault Backup Client</strong>",
                " * <p>",
                " * The Key Vault Backup Client provides both synchronous and asynchronous operations for performing full key backups,",
                " * full key restores, and selective key restores. Asynchronous (KeyVaultBackupAsyncClient) and synchronous",
                " * (KeyVaultBackupClient) clients exist in the SDK allowing for the selection of a client based on an application's use",
                " * case.",
                " *",
                " * <p>",
                " * <strong>NOTE:</strong> The backing store for key backups is a blob storage container using Shared Access Signature",
                " * authentication. For more details on creating a SAS token using the BlobServiceClient, see the <a",
                " * href=\"https://github.com/Azure/azure-sdk-for-java/tree/main/sdk/storage/azure-storage-blob#get-credentials\">Azure",
                " * Storage Blobs client README</a>. Alternatively, it is possible to <a",
                " * href=",
                " * \"https://docs.microsoft.com/azure/vs-azure-tools-storage-manage-with-storage-explorer?tabs=windows#generate-a-shared-access-signature-in-storage-explorer\">",
                " * generate a SAS token in Storage Explorer</a>.",
                " *",
                " * <p>",
                " * <strong>What is a Backup Operation?</strong>",
                " * <p>",
                " * A backup operation represents a long-running operation for a full key backup.",
                " *",
                " * <p>",
                " * <strong>What is a Restore Operation</strong>",
                " * <p>",
                " * A restore operation represents a long-running operation for both a full key and selective key restore.",
                " *",
                " * <p>",
                " * <strong>What is a Key Vault Settings Client?</strong>",
                " * <p>",
                " * The Key Vault Settings client allows manipulation of an Azure Key Vault account's settings, with operations",
                " * such as: getting, updating, and listing. Asynchronous (KeyVaultSettingsAsyncClient) and synchronous",
                " * (KeyVaultSettingsClient) clients exist in the SDK allowing for the selection of a client based on an application's",
                " * use case.",
                " *",
                " * <h2>Getting Started</h2>",
                " *",
                " * In order to interact with the Azure Key Vault service, you will need to create an instance of the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient} class, a vault url and a credential",
                " * object.",
                " *",
                " * <p>",
                " * The examples shown in this document use a credential object named DefaultAzureCredential for authentication, which",
                " * is appropriate for most scenarios, including local development and production environments. Additionally, we",
                " * recommend using a <a href=\"https://learn.microsoft.com/azure/active-directory/managed-identities-azure-resources/\">",
                " * managed identity</a> for authentication in production environments. You can find more information on different ways",
                " * of authenticating and their corresponding credential types in the <a",
                " * href=\"https://learn.microsoft.com/java/api/overview/azure/identity-readme?view=azure-java-stable\">Azure Identity",
                " * documentation\"</a>.",
                " *",
                " * <p>",
                " * <strong>Sample: Construct Synchronous Access Control Client</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates the creation of a {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient}, using the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClientBuilder} to configure it.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultAccessControlClient.instantiation -->",
                " * <pre>",
                " * KeyVaultAccessControlClient keyVaultAccessControlClient = new KeyVaultAccessControlClientBuilder&#40;&#41;",
                " *     .vaultUrl&#40;&quot;&lt;your-managed-hsm-url&gt;&quot;&#41;",
                " *     .credential&#40;new DefaultAzureCredentialBuilder&#40;&#41;.build&#40;&#41;&#41;",
                " *     .buildClient&#40;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultAccessControlClient.instantiation -->",
                " *",
                " * <p>",
                " * <strong>Sample: Construct Asynchronous Access Control Client</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates the creation of a {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}, using the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClientBuilder} to configure it.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient.instantiation -->",
                " * <pre>",
                " * KeyVaultAccessControlAsyncClient keyVaultAccessControlAsyncClient = new KeyVaultAccessControlClientBuilder&#40;&#41;",
                " *     .vaultUrl&#40;&quot;&lt;your-managed-hsm-url&gt;&quot;&#41;",
                " *     .credential&#40;new DefaultAzureCredentialBuilder&#40;&#41;.build&#40;&#41;&#41;",
                " *     .buildAsyncClient&#40;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient.instantiation -->",
                " * <br>",
                " * <hr/>",
                " *",
                " * <h2>Set a Role Definition</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to set a role",
                " * definition in the key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously create a role definition in the key vault, using the",
                " * {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#setRoleDefinition(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String) KeyVaultAccessControlClient.setRoleDefinition(KeyVaultRoleScope, String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.setRoleDefinition#KeyVaultRoleScope -->",
                " *",
                " * <pre>",
                " * KeyVaultRoleDefinition roleDefinition = keyVaultAccessControlClient.setRoleDefinition&#40;KeyVaultRoleScope.GLOBAL&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Created role definition with randomly generated name '%s' and role name '%s'.%n&quot;,",
                " *     roleDefinition.getName&#40;&#41;, roleDefinition.getRoleName&#40;&#41;&#41;;",
                " * </pre>",
                " *",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultAccessControlClient.setRoleDefinition#KeyVaultRoleScope",
                " * -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Get a Role Definition</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to retrieve a role",
                " * definition from the key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously retrieve a role definition from the key vault, using",
                " * the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#getRoleDefinition(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String) KeyVaultAccessControlClient.getRoleDefinition(KeyVaultRoleScope, String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.getRoleDefinition#KeyVaultRoleScope-String -->",
                " *",
                " * <pre>",
                " * String roleDefinitionName = &quot;de8df120-987e-4477-b9cc-570fd219a62c&quot;;",
                " * KeyVaultRoleDefinition roleDefinition",
                " *     = keyVaultAccessControlClient.getRoleDefinition&#40;KeyVaultRoleScope.GLOBAL, roleDefinitionName&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Retrieved role definition with name '%s' and role name '%s'.%n&quot;, roleDefinition.getName&#40;&#41;,",
                " *     roleDefinition.getRoleName&#40;&#41;&#41;;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.getRoleDefinition#KeyVaultRoleScope-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Delete a Role Definition</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to delete a role",
                " * definition from the key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously delete a role definition from the key vault, using",
                " * the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#deleteRoleDefinition(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String) KeyVaultAccessControlClient.deleteRoleDefinition(KeyVaultRoleScope, String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.deleteRoleDefinition#KeyVaultRoleScope-String",
                " * -->",
                " *",
                " * <pre>",
                " * String roleDefinitionName = &quot;6a709e6e-8964-4012-a99b-6b0131e8ce40&quot;;",
                " *",
                " * keyVaultAccessControlClient.deleteRoleDefinition&#40;KeyVaultRoleScope.GLOBAL, roleDefinitionName&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Deleted role definition with name '%s'.%n&quot;, roleDefinitionName&#41;;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.deleteRoleDefinition#KeyVaultRoleScope-String",
                " * -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Create a Role Assignment</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to set a role",
                " * assignment in the key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously create a role assignment in the key vault, using the",
                " * {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#createRoleAssignment(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String, java.lang.String) KeyVaultAccessControlClient.createRoleAssignment(KeyVaultRoleScope, String,",
                " * String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.createRoleAssignment#KeyVaultRoleScope-String-String",
                " * -->",
                " *",
                " * <pre>",
                " * String roleDefinitionId = &quot;b0b43a39-920c-475b-b34c-32ecc2bbb0ea&quot;;",
                " * String servicePrincipalId = &quot;169d6a86-61b3-4615-ac7e-2da09edfeed4&quot;;",
                " * KeyVaultRoleAssignment roleAssignment = keyVaultAccessControlClient.createRoleAssignment&#40;KeyVaultRoleScope.GLOBAL,",
                " *     roleDefinitionId, servicePrincipalId&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Created role assignment with randomly generated name '%s' for principal with id '%s'.%n&quot;,",
                " *     roleAssignment.getName&#40;&#41;, roleAssignment.getProperties&#40;&#41;.getPrincipalId&#40;&#41;&#41;;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.createRoleAssignment#KeyVaultRoleScope-String-String",
                " * -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Get a Role Definition</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to retrieve a role",
                " * definition from the key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously retrieve a role definition from the key vault, using",
                " * the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#getRoleDefinition(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String) KeyVaultAccessControlClient.getRoleDefinition(KeyVaultRoleScope, String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.getRoleAssignment#KeyVaultRoleScope-String -->",
                " *",
                " * <pre>",
                " * String roleAssignmentName = &quot;06d1ae8b-0791-4f02-b976-f631251f5a95&quot;;",
                " * KeyVaultRoleAssignment roleAssignment",
                " *     = keyVaultAccessControlClient.getRoleAssignment&#40;KeyVaultRoleScope.GLOBAL, roleAssignmentName&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Retrieved role assignment with name '%s'.%n&quot;, roleAssignment.getName&#40;&#41;&#41;;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.getRoleAssignment#KeyVaultRoleScope-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Delete a Role Definition</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultAccessControlClient} can be used to delete a role",
                " * definition from an Azure Key Vault account.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to asynchronously delete a role definition from the key vault, using",
                " * the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient#deleteRoleDefinition(com.azure.security.keyvault.administration.models.KeyVaultRoleScope,",
                " * java.lang.String) KeyVaultAccessControlClient.deleteRoleDefinition(KeyVaultRoleScope, String)} API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.deleteRoleAssignment#KeyVaultRoleScope-String",
                " * -->",
                " *",
                " * <pre>",
                " * String roleAssignmentName = &quot;c3ed874a-64a9-4a87-8581-2a1ad84b9ddb&quot;;",
                " *",
                " * keyVaultAccessControlClient.deleteRoleAssignment&#40;KeyVaultRoleScope.GLOBAL, roleAssignmentName&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Deleted role assignment with name '%s'.%n&quot;, roleAssignmentName&#41;;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlClient.deleteRoleAssignment#KeyVaultRoleScope-String",
                " * -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Run Pre-Backup Check for a Collection of Keys</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultBackupClient} can be used to check if it is possible to",
                " * back up the entire collection of keys from a key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously check if it is possible to back up an entire collection",
                " * of keys, using the",
                " * {@link com.azure.security.keyvault.administration.KeyVaultBackupClient#beginPreBackup(String, String)} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultBackupClient.beginPreBackup#String-String -->",
                " * <pre>",
                " * String blobStorageUrl = &quot;https:&#47;&#47;myaccount.blob.core.windows.net&#47;myContainer&quot;;",
                " * String sasToken = &quot;&lt;sas-token&gt;&quot;;",
                " *",
                " * SyncPoller&lt;KeyVaultBackupOperation, Void&gt; preBackupPoller = client.beginPreBackup&#40;blobStorageUrl, sasToken&#41;;",
                " * PollResponse&lt;KeyVaultBackupOperation&gt; pollResponse = preBackupPoller.poll&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;The current status of the operation is: %s.%n&quot;, pollResponse.getStatus&#40;&#41;&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultBackupOperation&gt; finalPollResponse = preBackupPoller.waitForCompletion&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Pre-backup check completed with status: %s.%n&quot;, finalPollResponse.getStatus&#40;&#41;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultBackupClient.beginPreBackup#String-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Back Up a Collection of Keys</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultBackupClient} can be used to back up the entire",
                " * collection of keys from a key vault.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously back up an entire collection of keys using, using the",
                " * {@link com.azure.security.keyvault.administration.KeyVaultBackupClient#beginBackup(String, String)} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultBackupClient.beginBackup#String-String -->",
                " * <pre>",
                " * String blobStorageUrl = &quot;https:&#47;&#47;myaccount.blob.core.windows.net&#47;myContainer&quot;;",
                " * String sasToken = &quot;&lt;sas-token&gt;&quot;;",
                " *",
                " * SyncPoller&lt;KeyVaultBackupOperation, String&gt; backupPoller = client.beginBackup&#40;blobStorageUrl, sasToken&#41;;",
                " * PollResponse&lt;KeyVaultBackupOperation&gt; pollResponse = backupPoller.poll&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;The current status of the operation is: %s.%n&quot;, pollResponse.getStatus&#40;&#41;&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultBackupOperation&gt; finalPollResponse = backupPoller.waitForCompletion&#40;&#41;;",
                " *",
                " * if &#40;finalPollResponse.getStatus&#40;&#41; == LongRunningOperationStatus.SUCCESSFULLY_COMPLETED&#41; &#123;",
                " *     String folderUrl = backupPoller.getFinalResult&#40;&#41;;",
                " *",
                " *     System.out.printf&#40;&quot;Backup completed. The storage location of this backup is: %s.%n&quot;, folderUrl&#41;;",
                " * &#125; else &#123;",
                " *     KeyVaultBackupOperation operation = backupPoller.poll&#40;&#41;.getValue&#40;&#41;;",
                " *",
                " *     System.out.printf&#40;&quot;Backup failed with error: %s.%n&quot;, operation.getError&#40;&#41;.getMessage&#40;&#41;&#41;;",
                " * &#125;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultBackupClient.beginBackup#String-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Run Pre-Restore Check for a Collection of Keys</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultBackupClient} can be used to check if it is possible to",
                " * restore an entire collection of keys from a backup.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously check if it is possible to restore an entire collection",
                " * of keys from a backup, using the",
                " * {@link com.azure.security.keyvault.administration.KeyVaultBackupClient#beginPreRestore(String, String)} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultBackupClient.beginPreRestore#String-String -->",
                " * <pre>",
                " * String folderUrl = &quot;https:&#47;&#47;myaccount.blob.core.windows.net&#47;myContainer&#47;mhsm-myaccount-2020090117323313&quot;;",
                " * String sasToken = &quot;&lt;sas-token&gt;&quot;;",
                " *",
                " * SyncPoller&lt;KeyVaultRestoreOperation, Void&gt; preRestorePoller = client.beginPreRestore&#40;folderUrl, sasToken&#41;;",
                " * PollResponse&lt;KeyVaultRestoreOperation&gt; pollResponse = preRestorePoller.poll&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;The current status of the operation is: %s.%n&quot;, pollResponse.getStatus&#40;&#41;&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultRestoreOperation&gt; finalPollResponse = preRestorePoller.waitForCompletion&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Pre-restore check completed with status: %s.%n&quot;, finalPollResponse.getStatus&#40;&#41;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultBackupClient.beginPreRestore#String-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Restore a Collection of Keys</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultBackupClient} can be used to restore an entire",
                " * collection of keys from a backup.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously restore an entire collection of keys from a backup,",
                " * using the {@link com.azure.security.keyvault.administration.KeyVaultBackupClient#beginRestore(String, String)} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultBackupClient.beginRestore#String-String -->",
                " * <pre>",
                " * String folderUrl = &quot;https:&#47;&#47;myaccount.blob.core.windows.net&#47;myContainer&#47;mhsm-myaccount-2020090117323313&quot;;",
                " * String sasToken = &quot;&lt;sas-token&gt;&quot;;",
                " *",
                " * SyncPoller&lt;KeyVaultRestoreOperation, KeyVaultRestoreResult&gt; restorePoller =",
                " *     client.beginRestore&#40;folderUrl, sasToken&#41;;",
                " * PollResponse&lt;KeyVaultRestoreOperation&gt; pollResponse = restorePoller.poll&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;The current status of the operation is: %s.%n&quot;, pollResponse.getStatus&#40;&#41;&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultRestoreOperation&gt; finalPollResponse = restorePoller.waitForCompletion&#40;&#41;;",
                " *",
                " * if &#40;finalPollResponse.getStatus&#40;&#41; == LongRunningOperationStatus.SUCCESSFULLY_COMPLETED&#41; &#123;",
                " *     System.out.printf&#40;&quot;Backup restored successfully.%n&quot;&#41;;",
                " * &#125; else &#123;",
                " *     KeyVaultRestoreOperation operation = restorePoller.poll&#40;&#41;.getValue&#40;&#41;;",
                " *",
                " *     System.out.printf&#40;&quot;Restore failed with error: %s.%n&quot;, operation.getError&#40;&#41;.getMessage&#40;&#41;&#41;;",
                " * &#125;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultBackupClient.beginRestore#String-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Selectively Restore a Key</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultBackupClient} can be used to restore a specific key",
                " * from a backup.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously restore a specific key from a backup, using the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupClient#beginSelectiveKeyRestore(String, String, String)}",
                " * API.",
                " * <!-- src_embed",
                " * com.azure.security.keyvault.administration.KeyVaultBackupClient.beginSelectiveKeyRestore#String-String-String -->",
                " *",
                " * <pre>",
                " * String folderUrl = &quot;https:&#47;&#47;myaccount.blob.core.windows.net&#47;myContainer&#47;mhsm-myaccount-2020090117323313&quot;;",
                " * String sasToken = &quot;sv=2020-02-10&amp;ss=b&amp;srt=o&amp;sp=rwdlactfx&amp;se=2021-06-17T07:13:07Z&amp;st=2021-06-16T23:13:07Z&quot;",
                " *     &quot;&amp;spr=https&amp;sig=n5V6fnlkViEF9b7ij%2FttTHNwO2BdFIHKHppRxGAyJdc%3D&quot;;",
                " * String keyName = &quot;myKey&quot;;",
                " *",
                " * SyncPoller&lt;KeyVaultSelectiveKeyRestoreOperation, KeyVaultSelectiveKeyRestoreResult&gt; backupPoller =",
                " *     client.beginSelectiveKeyRestore&#40;folderUrl, sasToken, keyName&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultSelectiveKeyRestoreOperation&gt; pollResponse = backupPoller.poll&#40;&#41;;",
                " *",
                " * System.out.printf&#40;&quot;The current status of the operation is: %s.%n&quot;, pollResponse.getStatus&#40;&#41;&#41;;",
                " *",
                " * PollResponse&lt;KeyVaultSelectiveKeyRestoreOperation&gt; finalPollResponse = backupPoller.waitForCompletion&#40;&#41;;",
                " *",
                " * if &#40;finalPollResponse.getStatus&#40;&#41; == LongRunningOperationStatus.SUCCESSFULLY_COMPLETED&#41; &#123;",
                " *     System.out.printf&#40;&quot;Key restored successfully.%n&quot;&#41;;",
                " * &#125; else &#123;",
                " *     KeyVaultSelectiveKeyRestoreOperation operation = backupPoller.poll&#40;&#41;.getValue&#40;&#41;;",
                " *",
                " *     System.out.printf&#40;&quot;Key restore failed with error: %s.%n&quot;, operation.getError&#40;&#41;.getMessage&#40;&#41;&#41;;",
                " * &#125;",
                " * </pre>",
                " *",
                " * <!-- end",
                " * com.azure.security.keyvault.administration.KeyVaultBackupClient.beginSelectiveKeyRestore#String-String-String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Get All Settings</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultSettingsClient} can be used to list all the settings",
                " * for an Azure Key Vault account.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously back up an entire collection of keys using, using the",
                " * {@link com.azure.security.keyvault.administration.KeyVaultSettingsClient#getSettings()} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultSettingsClient.getSettings -->",
                " * <pre>",
                " * KeyVaultGetSettingsResult getSettingsResult = keyVaultSettingsClient.getSettings&#40;&#41;;",
                " * List&lt;KeyVaultSetting&gt; settings = getSettingsResult.getSettings&#40;&#41;;",
                " *",
                " * settings.forEach&#40;setting -&gt;",
                " *     System.out.printf&#40;&quot;Retrieved setting with name '%s' and value %s'.%n&quot;, setting.getName&#40;&#41;,",
                " *         setting.asBoolean&#40;&#41;&#41;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultSettingsClient.getSettings -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultSettingsAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Retrieve a Specific Setting</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultSettingsClient} can be used to retrieve a specific",
                " * setting.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously restore an entire collection of keys from a backup,",
                " * using the {@link com.azure.security.keyvault.administration.KeyVaultSettingsClient#getSetting(String)} API.",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultSettingsClient.getSetting#String -->",
                " * <pre>",
                " * KeyVaultSetting setting = keyVaultSettingsClient.getSetting&#40;settingName&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Retrieved setting '%s' with value '%s'.%n&quot;, setting.getName&#40;&#41;, setting.asBoolean&#40;&#41;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultSettingsClient.getSetting#String -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultSettingsAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * <h2>Update a Specific Setting</h2>",
                " *",
                " * The {@link com.azure.security.keyvault.administration.KeyVaultSettingsClient} can be used to restore a specific key",
                " * from a backup.",
                " *",
                " * <p>",
                " * <strong>Code Sample:</strong>",
                " *",
                " * <p>",
                " * The following code sample demonstrates how to synchronously restore a specific key from a backup, using the {@link",
                " * com.azure.security.keyvault.administration.KeyVaultSettingsClient#updateSetting(com.azure.security.keyvault.administration.models.KeyVaultSetting)",
                " * KeyVaultSettingsClient.updateSetting(KeyVaultSetting)}",
                " * <!-- src_embed com.azure.security.keyvault.administration.KeyVaultSettingsClient.updateSetting#KeyVaultSetting -->",
                " * <pre>",
                " * KeyVaultSetting settingToUpdate = new KeyVaultSetting&#40;settingName, true&#41;;",
                " * KeyVaultSetting updatedSetting = keyVaultSettingsClient.updateSetting&#40;settingToUpdate&#41;;",
                " *",
                " * System.out.printf&#40;&quot;Updated setting '%s' to '%s'.%n&quot;, updatedSetting.getName&#40;&#41;, updatedSetting.asBoolean&#40;&#41;&#41;;",
                " * </pre>",
                " * <!-- end com.azure.security.keyvault.administration.KeyVaultSettingsClient.updateSetting#KeyVaultSetting -->",
                " *",
                " * <p>",
                " * <strong>Note:</strong> For the asynchronous sample, refer to {@link",
                " * com.azure.security.keyvault.administration.KeyVaultSettingsAsyncClient}. <br>",
                " * <hr/>",
                " *",
                " * @see com.azure.security.keyvault.administration.KeyVaultAccessControlClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultAccessControlAsyncClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultAccessControlClientBuilder",
                " * @see com.azure.security.keyvault.administration.KeyVaultBackupClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultBackupAsyncClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultBackupClientBuilder",
                " * @see com.azure.security.keyvault.administration.KeyVaultSettingsClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultSettingsAsyncClient",
                " * @see com.azure.security.keyvault.administration.KeyVaultSettingsClientBuilder",
                " */",
                "package com.azure.security.keyvault.administration;",
                ""));

        editor.replaceFile("src/main/java/com/azure/security/keyvault/administration/models/package-info.java",
            joinWithNewline(
                "// Copyright (c) Microsoft Corporation. All rights reserved.",
                "// Licensed under the MIT License.",
                "",
                "/**",
                " * <!-- @formatter:off -->",
                " * Package containing the data models for Administration clients. The Key Vault clients perform cryptographic key and",
                " * vault operations against the Key Vault service.",
                " */",
                "package com.azure.security.keyvault.administration.models;",
                ""));

        editor.replaceFile("src/main/java/com/azure/security/keyvault/administration/implementation/package-info.java",
            joinWithNewline(
                "// Copyright (c) Microsoft Corporation. All rights reserved.",
                "// Licensed under the MIT License.",
                "",
                "/**",
                " * <!-- @formatter:off -->",
                " * Package containing the implementations for Administration clients. The Key Vault clients perform cryptographic key",
                " * operations and vault operations against the Key Vault service.",
                " */",
                "package com.azure.security.keyvault.administration.implementation;",
                ""));

        editor.replaceFile("src/main/java/com/azure/security/keyvault/administration/implementation/models/package-info.java",
            joinWithNewline(
                "// Copyright (c) Microsoft Corporation. All rights reserved.",
                "// Licensed under the MIT License.",
                "",
                "/**",
                " * <!-- @formatter:off -->",
                " * Package containing the implementation data models for Administration clients. The Key Vault clients perform",
                " * cryptographic key operations and vault operations against the Key Vault service.",
                " */",
                "package com.azure.security.keyvault.administration.implementation.models;",
                ""));
    }

    private static String joinWithNewline(String... lines) {
        return String.join("\n", lines);
    }
}
