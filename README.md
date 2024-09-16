# Securely Connecting Azure Container Apps to Azure OpenAI Using User Managed Identity

Today, I want to share two key points from the entry "Securely Connecting Azure Container Apps to Azure OpenAI using User Managed Identity":

1. Deploying Java Applications to Azure Container Apps Without Containerization
2. Secure Connection from Azure Container Apps to Azure OpenAI Using User Managed Identity

## 1. Deploying Java Applications to Azure Container Apps Without Containerization

On September 11, 2024, the announcement "[Announcing the General Availability of Java Experiences on Azure Container Apps](https://techcommunity.microsoft.com/t5/apps-on-azure-blog/announcing-the-general-availability-of-java-experiences-on-azure/ba-p/4238294)" was made.

As detailed in the [Java on Azure Container Apps overview](https://learn.microsoft.com/azure/container-apps/java-overview), support for Java on Azure Container Apps has been enhanced. For example, Azure Container Apps now supports the following Spring components as managed services:

* Eureka Server for Spring
* Config Server for Spring
* Admin for Spring

Additionally, as explained in the [Quickstart: Launch Your First Java Application in Azure Container Apps](https://learn.microsoft.com/azure/container-apps/java-get-started?pivots=war), Azure Container Apps now offers a new feature called `Cloud Build Service`. This allows you to deploy applications directly to Azure Container Apps from Java artifacts like JAR or WAR files. The service automatically creates and deploys container images from the specified Java artifacts, eliminating the need to manually write Dockerfile container definitions or handle container builds and pushes.

To achieve this with Azure Container Apps, you use the `az containerapp up` command and specify the Java artifact as an argument. Detailed steps are provided later in the guide (see: `2.8 Creating an Azure Container Apps Instance`).

This significantly simplifies the deploying of Java applications to Azure Container Apps. Azure Container Apps can also scale the number of instances from zero as needed, making it a highly convenient service. We encourage you to try it.

## 2. Securely Connecting Azure Container Apps to Azure OpenAI Using User Managed Identity

In recent times, security measures have become increasingly important, and it is essential for businesses to build more secure systems. Microsoft recommends using Managed Identity for connections instead of password-based access when creating secure environments, such as production environments. This approach utilizes the Microsoft Entra ID authentication.

This method allows you to grant specific permissions for resources within a defined scope, making security management more flexible. For more details on Managed Identity, refer to the article "[What are Managed Identities for Azure Resources?](https://learn.microsoft.com/entra/identity/managed-identities-azure-resources/overview)". In this entry, I will clearly explain how to set up a User Managed Identity, step by step.

Following these steps will help you understand how to configure it, and they can also serve as a reference for setting up other resources.

### Steps to Set Up User Managed Identity

To connect Azure Container Apps to Azure OpenAI using a User Managed Identity, follow these steps to set up the environment:

1. Set Environment Variables
2. Create a Resource Group
3. Create an Azure OpenAI Instance
4. Create a User Managed Identity
5. Assign Roles to the User Managed Identity for Azure OpenAI
6. Create an Azure Container Apps Environment
7. Develop a Spring Boot Web Application
8. Create an Azure Container Apps Instance
9. Assign the User Managed Identity to Azure Container Apps
10. Verify the Setup

### 2.1. Set Environment Variables

When setting up the environment, configure environment variables to avoid repetitive input.

```bash
export RESOURCE_GROUP=yoshio-OpenAI-rg
export LOCATION=eastus2
export AZURE_OPENAI_NAME=yt-secure-openai
export OPENAI_DEPLOY_MODEL_NAME=gpt-4o
export USER_MANAGED_IDENTITY_NAME=yoshio-user-managed-id
export SUBSCRIPTION=$(az account show --query id --output tsv)
```

Below are the environment variable names and their descriptions. If you need to change the names to suit your environment, please refer to the descriptions below to modify each resource name accordingly.

| Environment Variable Name | Description |
| ------------------------------- | ----------------------------------------- |
| RESOURCE_GROUP| Name of the resource group to create|
| LOCATION| Location where the environment will be set up |
| USER\_MANAGED\_IDENTITY\_NAME| Name of the User Managed Identity|
| AZURE\_OPENAI\_NAME| Name of the Azure OpenAI|
| OPENAI\_DEPLOY\_MODEL\_NAME| Name of the AI model to be deployed |
| SUBSCRIPTION| Subscription ID to be used |

### 2.2. Create a Resource Group

First, create a resource group in the Azure environment. Use the `--location` argument to specify the Azure region where it will be created.

```azurecli
az group create --name $RESOURCE_GROUP --location $LOCATION
```

### 2.3. Create an Azure OpenAI Instance

Next, create an Azure OpenAI instance by running the `az cognitiveservices account create` command. In this step, specify `--kind OpenAI` and `--custom-domain` for the instance.

```azurecli
az cognitiveservices account create \
  --name $AZURE_OPENAI_NAME \
  --resource-group $RESOURCE_GROUP \
  --kind OpenAI \
  --custom-domain $AZURE_OPENAI_NAME \
  --sku S0 \
  --location $LOCATION
```

> **Note:**  
> It is crucial to specify `--custom-domain` when creating an Azure OpenAI instance.
> If omitted, a `region endpoint` like `https://eastus2.api.cognitive.microsoft.com/` will be automatically generated. As outlined in "[Authenticate with Microsoft Entra ID](https://learn.microsoft.com/azure/ai-services/authentication#authenticate-with-microsoft-entra-id)", you cannot perform authentication using Microsoft Entra ID, in this case Managed Identity, without specifying this option.To enable Managed Identity authentication, need to specify `--custom-domain`.

Next, deploy the OpenAI model to your newly created Azure OpenAI instance using the `az cognitiveservices account deployment create` command. Here, specify the model name with `--model-name` and the model version with `--model-version`. You should also define the service capacity using `--sku-capacity` and select the service plan with `--sku-name`.

```azurecli
az cognitiveservices account deployment create \
  --name $AZURE_OPENAI_NAME \
  --resource-group  $RESOURCE_GROUP \
  --deployment-name $OPENAI_DEPLOY_MODEL_NAME \
  --model-name $OPENAI_DEPLOY_MODEL_NAME \
  --model-version "2024-08-06"  \
  --model-format OpenAI \
  --sku-capacity "20" \
  --sku-name "GlobalStandard"
```

With this, the creation of the Azure OpenAI instance is complete. After creating the instance, store the necessary information for your Java program implementation and other operations in environment variables.

```bash
export OPEN_AI_RESOURCE_ID=$(az cognitiveservices account list \
                                -g $RESOURCE_GROUP \
                                --query "[0].id" \
                                --output tsv)
export OPEN_AI_ENDPOINT=$(az cognitiveservices account show \
                                --resource-group $RESOURCE_GROUP \
                                --name $AZURE_OPENAI_NAME \
                                --query "properties.endpoint" \
                                --output tsv)
export OPEN_AI_ACCESS_KEY=$(az cognitiveservices account keys list \
                                --resource-group $RESOURCE_GROUP \
                                --name $AZURE_OPENAI_NAME \
                                --query key1 --output tsv)
```

Here, we are setting the following environment variables:

| Environment Variable Name | Description |
| ------------------------- | ----------------------------------------------------------- |
| OPEN\_AI\_RESOURCE\_ID | Resource ID of OpenAI<br> (Needed for role assignment scope) |
| OPEN\_AI\_ENDPOINT | Endpoint of OpenAI<br> (Required for Java app connection) |
| OPEN\_AI\_ACCESS\_KEY| Access key for OpenAI<br> (Needed for Java app development locally) |

> Note:  
> You can set up Azure OpenAI using the Azure CLI, as demonstrated below.
> However, as of September 2024, Java Applications utilizing Managed Identity did not work with Azure OpenAI environments created using the Azure CLI. Therefore, I recommend creating the Azure OpenAI instance using the Azure Portal instead.

```azurecli
az cognitiveservices account create \
  --name $AZURE_OPENAI_NAME \
  --resource-group $RESOURCE_GROUP \
  --kind OpenAI \
  --sku S0 \
  --location $LOCATION
az cognitiveservices account deployment create \
  --name $AZURE_OPENAI_NAME \
  --resource-group  $RESOURCE_GROUP \
  --deployment-name $OPENAI_DEPLOY_MODEL_NAME \
  --model-name $OPENAI_DEPLOY_MODEL_NAME \
  --model-version "2024-08-06"  \
  --model-format OpenAI \
  --sku-capacity "20" \
  --sku-name "GlobalStandard"
```

### 2.4. Create a User Managed Identity

Now that the Azure OpenAI instance is set up, the next step is to create a User Managed Identity. Use the `az identity create` command.

```azurecli
az identity create -g $RESOURCE_GROUP -n $USER_MANAGED_IDENTITY_NAME
```

Once the User Managed Identity is created, retrieve the necessary information for future command execution and Java program usage, and assign it to the environment variables.

```bash
export USER_MANAGED_ID_CLIENT_ID=$(az identity list \
                                        -g $RESOURCE_GROUP \
                                        --query "[0].clientId" \
                                        -o tsv)
export USER_MANAGED_ID_PRINCIPAL_ID=$(az identity list \
                                        -g $RESOURCE_GROUP \
                                        --query "[0].principalId" \
                                        -o tsv)
export USER_MANAGED_ID_RESOURCE_ID=$(az identity list \
                                        -g $RESOURCE_GROUP \
                                        --query "[0].id" \
                                        -o tsv)
```

Below is an explanation of each environment variable's value and its usage:

| Environment Variable Name| Description|
| ------------------------------ | ------------------------------------------------------------- |
| USER\_MANAGED\_ID\_CLIENT\_ID| Client ID of the User Managed Identity<br> (Needed for Java app implementation) |
| USER\_MANAGED\_ID\_PRINCIPAL\_ID| Principal ID of the User Managed Identity<br> (Required for role assignment) |
| USER\_MANAGED\_ID\_RESOURCE\_ID | Resource ID of the User Managed Identity<br> (Needed for assigning ID to Container Apps) |

### 2.5. Assign Roles to User Managed Identity for Azure OpenAI

Use the `az role assignment create` command to assign a role to the User Managed Identity, allowing it to interact with the OpenAI resource using the `Cognitive Services OpenAI User` permission.

The `$OPEN_AI_RESOURCE_ID` represents the OpenAI resource ID, and the role is assigned specifically to that resource. This method grants only the necessary permissions for the app to run, enhancing security by avoiding unnecessary permissions.

```azurecli
az role assignment create --assignee $USER_MANAGED_ID_PRINCIPAL_ID \
                          --scope $OPEN_AI_RESOURCE_ID \
                          --role "Cognitive Services OpenAI User" 
```

In addition to the roles mentioned, you can also assign the following roles.

* Cognitive Services OpenAI User
* Cognitive Services OpenAI Contributor
* Cognitive Services Contributor
* Cognitive Services Usages Reader

For detailed information about the capabilities of each role, please refer to the [Role-based Access Control for Azure OpenAI Service](https://learn.microsoft.com/azure/ai-services/openai/how-to/role-based-access-control).

### 2.6. Create an Azure Container Apps Environment

Next, create an Azure Container Apps Environment. To do this using the Azure CLI, you need to register additional extensions and providers. If you haven't executed the following commands before, please execute them now.

```azurecli
az upgrade
az extension add --name containerapp --upgrade -y
az provider register --namespace Microsoft.Web
az provider register --namespace Microsoft.App
az provider register --namespace Microsoft.OperationalInsights
```

Then, define the name of the Azure Container Apps Environment as an environment variable. Choose a name that suits your environment.

```bash
export CONTAINER_ENVIRONMENT=YTContainerEnv3
```

Finally, run the `az containerapp env create` command to set up the environment.

```azurecli
az containerapp env create --name $CONTAINER_ENVIRONMENT \
                           --enable-workload-profiles \
                           -g $RESOURCE_GROUP \
                           --location $LOCATION
```

### 2.7. Creating a Spring Boot Web Application

With the Azure OpenAI and Azure Container Apps Environment set up, we will now create a Java project to implement a simple app that invoke an OpenAI model from a Java Application. We will use the Spring Boot for this implementation.

#### 2.7.1 Creating a Spring Boot Project

Execute the following command to create a Spring Boot project. After creating and downloading the project, unzip the archive to extract its contents.

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.3.3 \
  -d baseDir=Yoshio-AI-App-Spring-Boot \
  -d groupId=com.yoshio3 \
  -d artifactId=Yoshio-AI-App \
  -d name=myproject \
  -d packageName=com.yoshio3 \
  -o YoshioAIProject.zip

unzip YoshioAIProject.zip
```

The above command will generate a project with the following directory structure.

```text
├── HELP.md
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src
    ├── main
    │   ├── java
    │   │   └── com
    │   │       └── yoshio3
    │   │           └── MyprojectApplication.java
    │   └── resources
    │       ├── application.properties
    │       ├── static
    │       └── templates
    └── test
        └── java
            └── com
                └── yoshio3
                    └── MyprojectApplicationTests.java
```

#### 2.7.2 Editing the pom.xml Project File

Add the following dependencies to the `pom.xml` file located in the root directory. This will include the necessary libraries for connecting to and authenticating with OpenAI.

```xml
	<dependencies>
       ......
		<dependency>
			<groupId>com.azure</groupId>
			<artifactId>azure-ai-openai</artifactId>
			<version>1.0.0-beta.11</version>
		</dependency>
       <dependency>
			<groupId>com.azure</groupId>
			<artifactId>azure-identity</artifactId>
			<version>1.13.2</version>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-api</artifactId>
			<version>2.0.16</version>
		</dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-core</artifactId>
            <version>1.5.8</version>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.5.8</version>
        </dependency>
	</dependencies>
```

#### 2.7.3 Implementing a RESTful Endpoint (Main Part)

Next, create an `AIChatController.java` file in the `src/main/java/com/yoshio3` directory. Implement the following code to define a RESTful endpoint that queries OpenAI upon receiving a request.

```java
package com.yoshio3;

import org.springframework.web.bind.annotation.RestController;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class AIChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AIChatController.class);

    @Value("${USER_MANAGED_ID_CLIENT_ID}")
    private String userManagedIDClientId;

    @Value("${OPENAI_ENDPOINT}")
    private String openAIEndpoint;

    @Value("${OPENAI_KEY}")
    private String openAIKey;

    private static final String OPEN_AI_CHAT_MODEL = "gpt-4o";

    /**
     * This API is used to chat with OpenAI's GPT-4 model. And if user ask somethings, it will
     * return the message with Pirate language.
     * 
     * Ex. You can invoke the API by using the following command: curl -X POST
     * http://localhost:8080/askAI -H "Content-Type: application/json" -d '{"message":"Please tell
     * me about the appeal of Spring Boot in Japanese."}'
     * 
     * @param message RequestMessage
     * @return String Response from OpenAI
     */

    @PostMapping("/askAI")
    public String chat(@RequestBody RequestMessage message) {
        return getResponseFromOpenAI(message.getMessage());
    }

    /**
     * This method is used to get the response from OpenAI.
     * 
     * For production environment, you can use Managed Identity to authenticate with OpenAI. If you
     * want to use Managed Identity, please use the ManagedIdentityCredentialBuilder.
     * 
     * For local development, you can use AzureKeyCredential to authenticate with OpenAI.
     * 
     * @param message RequestMessage
     * @return String Response from OpenAI
     */

    private String getResponseFromOpenAI(String message) {
        try {
            // Create OpenAI client with User Managed Identity
            ManagedIdentityCredential credential =
                    new ManagedIdentityCredentialBuilder().clientId(userManagedIDClientId).build();
            OpenAIClient openAIClient = new OpenAIClientBuilder().credential(credential)
                    .endpoint(openAIEndpoint).buildClient();

            // Create OpenAI client without Managed Identity (For local development)
            // OpenAIClient openAIClient = new OpenAIClientBuilder().endpoint(openAIEndpoint)
            // .credential(new AzureKeyCredential(openAIKey)).buildClient();

            // Create Chat Request Messages
            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage("You are a helpful assistant. You will talk like a pirate."));
            chatMessages.add(new ChatRequestUserMessage("Can you help me?"));
            chatMessages.add(new ChatRequestAssistantMessage("Of course, me hearty! What can I do for ye?"));
            chatMessages.add(new ChatRequestUserMessage(message));
            ChatCompletionsOptions chatCompletionsOptions = new ChatCompletionsOptions(chatMessages);

            // Invoke OpenAI Chat API
            ChatCompletions chatCompletions = openAIClient.getChatCompletions(OPEN_AI_CHAT_MODEL, chatCompletionsOptions);
            StringBuilder response = new StringBuilder();
            chatCompletions.getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            return response.toString();
        } catch (Exception e) {
            StackTraceElement[] stackTrace = e.getStackTrace();
            LOGGER.error(e.getMessage());
            LOGGER.error(e.getLocalizedMessage());
            Throwable cause = e.getCause();
            if (cause != null) {
                LOGGER.error(e.getCause().toString());
            }
            for (StackTraceElement element : stackTrace) {
                LOGGER.error(element.toString());
            }
            return e.getMessage();
        }
    }
}
```

Note that the `OpenAIClient` instance is described in two different ways. The current code uses `ManagedIdentityCredential` to connect to Azure OpenAI with a User Managed Identity. This code works only when running in an Azure environment and won't work in a local or non-Azure environment.

During development, you need to test and verify functionality locally. In such cases, a User Managed Identity cannot be used. Instead, connect using the OpenAI Access Key by uncommenting the line with `AzureKeyCredential(openAIKey)` to create the OpenAIClient instance.

Additionally, `SLF4J` and `Logback` are used for logging in the implementation. Configure them by creating a `logback-spring.xml` file in the `/src/main/resources` directory. While detailed logging configuration is not covered here, the original code is available on GitHub for reference if needed.

Finally, here's a brief overview of the code: When a question or message is received from a user, it responds in a pirate tone as defined by `SYSTEM`. Enjoy the pirate-style replies!

#### 2.7.4 Defining the JSON Format for Endpoint Reception

Next, define the JSON data format to be sent to this RESTful service. To process messages like `{"message":"What is the benefit of Spring Boot"}` in the HTTP request BODY, define the following class.

```java
package com.yoshio3;

public class RequestMessage {
    private String message;

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
```

With this, we have completed the minimal code necessary for operation verification.

#### 2.7.5 Application Configuration

Next, configure the settings to connect to Azure OpenAI. During the systems setup from sections 2.1 to 2.6, all required information was stored in environment variables. Execute the following command to retrieve the necessary information in the Java program.

```bash
echo "USER_MANAGED_ID_CLIENT_ID" : $USER_MANAGED_ID_CLIENT_ID
echo "OPEN_AI_ENDPOINT" : $OPEN_AI_ENDPOINT
echo "OPEN_AI_ACCESS_KEY" : $OPEN_AI_ACCESS_KEY
```

Write the execution results above into the `application.properties` file located in the `/src/main/resources/` directory.

```text
spring.application.name=AI-Chatbot
logging.level.root=INFO
logging.level.org.springframework.web=INFO

USER_MANAGED_ID_CLIENT_ID=********-****-****-****-************
OPENAI_ENDPOINT=https://********.openai.azure.com/
OPENAI_KEY=********************************
```

> Note:  
> `OPENAI_KEY` is the access key for OpenAI. Use it only during development in a development environment and avoid using it in a production environment.

#### 2.7.6 (Optional): Verifying Operation in a Local Environment

To verify if the Java program works locally, swap the comments in the `OpenAIClient` instance creation part of the `AIChatController` class code and run it.

```java
// ManagedIdentityCredential credential = new ManagedIdentityCredentialBuilder().clientId(userManagedIDClientId).build();
// OpenAIClient openAIClient = new OpenAIClientBuilder().credential(credential)
//                    .endpoint(openAIEndpoint).buildClient();

 OpenAIClient openAIClient = new OpenAIClientBuilder().endpoint(openAIEndpoint)
                   .credential(new AzureKeyCredential(openAIKey)).buildClient();
```

After making the changes, execute the following command.

```bash
mvn spring-boot:run
```

If the Spring Boot application starts successfully, it will be listening on port 8080. Use the following `curl` command to verify if you can query Azure OpenAI.

```bash
curl -X POST http://localhost:8080/askAI \
     -H "Content-Type: application/json" \
     -d '{"message":"What is the benefit of the Spring Boot?"}'
```

The response will vary each time you run the command, but it will reply in a pirate tone, for example.

```text
Aye, Captain! Let me explain the benefits of Spring Boot.
Spring Boot is a framework that aids rapid application development. 
It allows you to create standalone applications with minimal configuration.

It also comes with auto-configuration, reducing manual setup. Managing dependencies is easy, and it includes commonly used libraries.
It's fast to start up, easy to deploy, and suitable for cloud operations. 

It's a powerful tool for boosting developer productivity.
Anything else I can assist with?
```

After completing local verification, revert the changes to generate the `OpenAIClient` instance using `ManagedIdentityCredential`.

#### 2.7.7 Building the Application and Creating Artifacts

Once these steps are complete, build the application and create the artifacts.

```bash
mvn clean package
```

Upon building the project, the artifacts will be created in the `target` directory. Here, the Spring Boot application is created as `Yoshio-AI-App-0.0.1-SNAPSHOT.jar`.

```bash
> ls -l target 
total 40876
-rw-r--r-- 1 teradayoshio wheel 41847613  9 14 14:52 Yoshio-AI-App-0.0.1-SNAPSHOT.jar
-rw-r--r-- 1 teradayoshio wheel     7003  9 14 14:52 Yoshio-AI-App-0.0.1-SNAPSHOT.jar.original
drwxr-xr-x 5 teradayoshio wheel      160  9 14 20:34 classes
drwxr-xr-x 3 teradayoshio wheel       96  9 14 14:52 generated-sources
drwxr-xr-x 3 teradayoshio wheel       96  9 14 14:52 generated-test-sources
drwxr-xr-x 3 teradayoshio wheel       96  9 14 14:52 maven-archiver
drwxr-xr-x 3 teradayoshio wheel       96  9 14 14:52 maven-status
drwxr-xr-x 4 teradayoshio wheel      128  9 14 14:52 surefire-reports
drwxr-xr-x 3 teradayoshio wheel       96  9 14 20:34 test-classes
```

### 2.8. Creating an Azure Container Apps Instance

Now that the implementation of the Java program is complete, let's deploy this Java Application to Azure Container Apps.

Specify the name for the container application using the environment variable `CONTAINER_APP_NAME`. Define the path and filename of the Spring Boot artifact with `JAR_FILE_PATH_AND_NAME`.

```bash
export CONTAINER_APP_NAME=yoshio-ai-app
export JAR_FILE_PATH_AND_NAME=./target/Yoshio-AI-App-0.0.1-SNAPSHOT.jar
```

As explained in "[1. Deploying Java Applications to Azure Container Apps Without Containerization]()", you no longer need to create and deploy your own container image when deploying to Azure Container Apps. We only have the Java artifact `Yoshio-AI-App-0.0.1-SNAPSHOT.jar`, and we will deploy based on this.

To do so, use the `az containerapp up` command. As you can see from the arguments, you only need to specify `--artifact $JAR_FILE_PATH_AND_NAME`, without any container image arguments. This command will automatically set up the build environment, build the container, and deploy it.

Please execute the following command.

```azurecli
az containerapp up \
  --name $CONTAINER_APP_NAME \
  --resource-group $RESOURCE_GROUP \
  --subscription $SUBSCRIPTION \
  --location $LOCATION \
  --environment $CONTAINER_ENVIRONMENT \
  --artifact $JAR_FILE_PATH_AND_NAME \
  --ingress external \
  --target-port 8080 \
  --env-vars AZURE_LOG_LEVEL=2 \
  --query properties.configuration.ingress.fqdn
```

> Note:  
> You can slightly customize the container creation. Use the environment variables provided below as needed:
> [Build Environment Variables for Java in Azure Container Apps](https://learn.microsoft.com/en-us/azure/container-apps/java-build-environment-variables).

### 2.9. Assigning a User Managed Identity to Azure Container Apps

Having deployed the app to Azure Container Apps, the final step is to apply a User Managed Identity to the container we created. Execute the `az containerapp identity assign` command.

```azurecli
az containerapp identity assign --name $CONTAINER_APP_NAME \
                                --resource-group $RESOURCE_GROUP \
                                --user-assigned $USER_MANAGED_ID_RESOURCE_ID
```

With that, the User Managed Identity configuration is complete.

### 2.10. Verifying Operation

Now that all the settings are complete, let's verify the operation. First, obtain the FQDN host name assigned to the container's Ingress.

```bash
export REST_ENDPOINT=$(az containerapp show -n $CONTAINER_APP_NAME \
          -g $RESOURCE_GROUP \
          --query properties.configuration.ingress.fqdn --output tsv)
```

Next, append the RESTful endpoint URL to the obtained host name and connect to it.

```bash
curl -X POST https://$REST_ENDPOINT/askAI \
     -H "Content-Type: application/json" \
     -d '{"message":"What is Spring Boot? please explain around 200 words?"}'
```

Each time it is executed, the response may vary, but you can expect results like those below.

```text
Arrr, Spring Boot be a powerful framework fer buildin' Java applications with minimal fuss, savvy? 
It sails under the banner of the larger Spring ecosystem, providin' a swift and easy approach fer creatin' scalable and stand-alone web apps.

Ye see, it comes with embedded servers like Tomcat or Jetty, meanin' ye don't have the hassle of deployin' to external servers. 
Spring Boot simplifies the setup by makin' configurations automatic, allowin' developers to focus on writin' their code without worryin' about the boilerplate.

It also hoists the flag of convention over configuration, offerin' defaults that make gettin’ started as smooth as a voyage with favorable winds. 
Should ye need to adjust yer sails, customization be available through its easy-to-use properties.

And if ye treasure quick development, Spring Boot supports ye well with its rich assortment of pre-configured starters, integratin' smoothly with other technologies like databases and messaging systems.

In essence, Spring Boot be a trusty vessel fer any sea-farin' coder seekin' speed, efficiency, and a treasure chest of features in their Java web applications. 
Aye, it be a boon to all developers, whether seasoned or green as a fresh landlubber!
```

### 2.11. Reusing the Same User Managed Identity to Access Azure OpenAI from Other Azure Resources

Previously, we discussed how to apply a `User Managed Identity` to `Azure Container Apps` to connect to `Azure OpenAI`. However, this User Managed Identity can also be reused by other services.

In other words, applications deployed on other Azure resources can use the same User Managed Identity to connect to Azure OpenAI. 

Some resources allow you to specify this identity during creation, while for others, you can assign it later.

Below, we demonstrate how to assign a User Managed Identity to existing Azure resources, except for Azure Container Instances.

#### For Azure VM

To assign a User Managed Identity to an Azure VM, execute the following command:

```azurecli
az vm identity assign -g $RESOURCE_GROUP \
                      -n $VM_NAME \
                      --identities $USER_MANAGED_ID_RESOURCE_ID
```

#### For Azure App Service

To assign a User Managed Identity to an Azure App Service, execute the following command:

```azurecli
az webapp identity assign -g $RESOURCE_GROUP \
                          --name $APP_SERVICE_NAME \
                          --identities $USER_MANAGED_ID_RESOURCE_ID
```

#### For Azure Functions

To assign a User Managed Identity to Azure Functions, execute the following command:

```azurecli
az functionapp identity assign -g $RESOURCE_GROUP \
                               -n $AZURE_FUNCTION_NAME \
                               --identities $USER_MANAGED_ID_RESOURCE_ID
```

#### For Azure Container Instances

For Azure Container Instances, you can assign a User Managed Identity during the instance creation:

```azurecli
az container create \
                    --resource-group $RESOURCE_GROUP  \
                    --name $CONTAINER_INSTANCE_NAME \
                    --image $CONTAINER_IMAGE \
                    --assign-identity $USER_MANAGED_ID_RESOURCE_ID 
```

#### For Azure Kubernetes Service

To assign a User Managed Identity to Azure Kubernetes Service, execute the following command:

```azurecli
az aks update \
                -g $RESOURCE_GROUP \
                --name $AKS_CLUSTER_NAME \
                --enable-managed-identity \
                --assign-identity $USER_MANAGED_ID_RESOURCE_ID
```

Like this, by using a User Managed Identity, you can securely connect to target resources across different Azure services by reusing correctly configured access roles.

### 2.12. Comparing Access Keys and User Managed Identities from a Security Perspective

This section explains the security differences between using Access Keys and User Managed Identities for Azure OpenAI.

When using a User Managed Identity, you specify the `client ID` of the User Managed Identity when creating an instance of the `ManagedIdentityCredential` class in your Java Application. This `client ID` value is set as an environment variable `$USER_MANAGED_ID_CLIENT_ID` when the User Managed Identity is created.

At first glance, it might seem that this is the only critical piece of information needed for the connection, aside from the endpoint URL.

```java
ManagedIdentityCredential credential =
        new ManagedIdentityCredentialBuilder()
                       .clientId(userManagedIDClientId)
                        .build();
OpenAIClient openAIClient = new OpenAIClientBuilder()
                                        .credential(credential)
                                        .endpoint(openAIEndpoint)
                                        .buildClient();
```

Let's consider the impact of a leaked `client ID` versus a leaked `Access Key`.

An `Access Key` is look like a regular password. If it is leaked, anyone who knows the password can access the resource. In the case of Azure OpenAI, this means anyone could use AI models like GPT-4. If the network is publicly accessible, the impact could be even greater.

On the other hand, if the `client ID` is leaked, the impact is limited. This is because the `client ID` alone cannot connect to Azure OpenAI. To use a User Managed Identity, the service must be running on Azure. Even if Azure OpenAI is publicly accessible, you cannot connect from a local environment or over a network using a Java application.

Additionally, the following role assignment is configured for the User Managed Identity:

```azurecli
az role assignment create --assignee $USER_MANAGED_ID_PRINCIPAL_ID \
    --scope $OPEN_AI_RESOURCE_ID \
    --role "Cognitive Services OpenAI User" 
```

This sets what actions can be performed using this user ID. Here, the `Cognitive Services OpenAI User` role is granted for Azure OpenAI services, limiting permissions to operations within Azure OpenAI.

This means that even with this ID, you cannot perform administrative actions like adding models, not just across Azure but even within Azure OpenAI.

Furthermore, to use this user ID, you must log in to Azure as an administrator, and only the resources specified by the administrator can be accessed within the scope of this ID's permissions.

In summary, compared to the impact of a leaked access key, a leaked client ID requires multiple steps to exploit, making it more secure.

For these reasons, User Managed Identities offer a more secure way to manage operations than access keys. We encourage you to use Managed Identities to build a secure environment.

## 3 Summary

In this guide, we walked through a detailed, step-by-step process for securely connecting Azure Container Apps to Azure OpenAI using a User Managed Identity. While setting up a User Managed Identity might seem cumbersome at first, it offers the significant advantage of reusability. If your systems are few or flexibility isn't a major concern, a System Managed Identity might be preferable.

However, as the number of systems increases, User Managed Identities become very handy. After creating a User Managed Identity and assigning roles, you can reuse the same ID across various services like Azure App Service, Azure Functions, and Azure Kubernetes Services. The more systems or services you manage, the more beneficial this becomes.

Please try using Managed Identities to effectively build secure environments.
