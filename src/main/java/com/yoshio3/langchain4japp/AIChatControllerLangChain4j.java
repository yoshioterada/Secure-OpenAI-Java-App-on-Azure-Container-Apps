package com.yoshio3.langchain4japp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatExtensionConfiguration;
import com.azure.ai.openai.models.ChatCompletionsResponseFormat;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.yoshio3.AIChatController;
import com.yoshio3.RequestMessage;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiTokenizer;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;

@RestController
public class AIChatControllerLangChain4j {

    private static final Logger LOGGER = LoggerFactory.getLogger(AIChatController.class);

    @Value("${USER_MANAGED_ID_CLIENT_ID}")
    private String userManagedIDClientId;

    @Value("${OPENAI_ENDPOINT}")
    private String openAIEndpoint;

    @Value("${OPENAI_KEY}")
    private String openAIKey;

    @PostMapping("/askAILangChain4j")
    public String chat(@RequestBody RequestMessage message) {
        return getResponse(message.getMessage());
    }

    // The detail of the parameters are as follows:
    // https://platform.openai.com/docs/api-reference/chat/create
    // https://learn.microsoft.com/en-us/java/api/com.azure.ai.openai.models.azurechatextensionconfiguration?view=azure-java-preview
    // https://learn.microsoft.com/en-us/java/api/com.azure.ai.openai.models.azurechatenhancementconfiguration?view=azure-java-preview
    private String getResponse(String message) {
        // OpenAIClient openAIClient = new OpenAIClientBuilder().endpoint(openAIEndpoint)
        // .credential(new AzureKeyCredential(openAIKey)).buildClient();

        ManagedIdentityCredential credential =
                new ManagedIdentityCredentialBuilder().clientId(userManagedIDClientId).build();
        OpenAIClient openAIClient = new OpenAIClientBuilder().credential(credential)
                .endpoint(openAIEndpoint).buildClient();

        AzureOpenAiChatModel model =
                AzureOpenAiChatModel.builder().openAIClient(openAIClient).deploymentName("gpt-4o")
                        .tokenizer(new AzureOpenAiTokenizer()).listeners(initListeners()).build();

        return model.generate(message);
    }



    public List<ChatModelListener> initListeners() {
        List<ChatModelListener> listeners = new ArrayList<>();
        ChatModelListener listener = new ChatModelListener() {

            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                ChatModelRequest request = requestContext.request();
                Map<Object, Object> attributes = requestContext.attributes();
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                ChatModelResponse response = responseContext.response();
                ChatModelRequest request = responseContext.request();
                Map<Object, Object> attributes = responseContext.attributes();
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                Throwable error = errorContext.error();
                ChatModelRequest request = errorContext.request();
                ChatModelResponse partialResponse = errorContext.partialResponse();
                Map<Object, Object> attributes = errorContext.attributes();
            }
        };
        listeners.add(listener);
        return listeners;
    }
}
