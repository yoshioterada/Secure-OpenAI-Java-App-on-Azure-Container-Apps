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
            LOGGER.debug("{} is input from user.", message);
            LOGGER.debug("OpenAI Endpoint is {} ", openAIEndpoint);
            LOGGER.debug("userManagedIDClientId is {} ", userManagedIDClientId);

            ManagedIdentityCredential credential =
                    new ManagedIdentityCredentialBuilder().clientId(userManagedIDClientId).build();
            LOGGER.debug("Managed Identity Credential was created.");
            // Create OpenAI client with Managed Identity
            OpenAIClient openAIClient = new OpenAIClientBuilder().credential(credential)
                    .endpoint(openAIEndpoint).buildClient();
            LOGGER.debug("OpenAI Client Instance was created.");

            // Create OpenAI client without Managed Identity (For local development)
            // OpenAIClient openAIClient = new OpenAIClientBuilder().endpoint(openAIEndpoint)
            // .credential(new AzureKeyCredential(openAIKey)).buildClient();

            // Create Chat Request Messages
            List<ChatRequestMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatRequestSystemMessage(
                    "You are a helpful assistant. You will talk like a pirate."));
            chatMessages.add(new ChatRequestUserMessage("Can you help me?"));
            chatMessages.add(
                    new ChatRequestAssistantMessage("Of course, me hearty! What can I do for ye?"));
            chatMessages.add(new ChatRequestUserMessage(message));
            debugChatRequestMessageLog(chatMessages );

            ChatCompletionsOptions chatCompletionsOptions =
                    new ChatCompletionsOptions(chatMessages);
            LOGGER.debug("Prompt message was created.");

            LOGGER.debug("OpenAI Chat Model is {} ", OPEN_AI_CHAT_MODEL);            
            // Invoke OpenAI Chat API
            ChatCompletions chatCompletions =
                    openAIClient.getChatCompletions(OPEN_AI_CHAT_MODEL, chatCompletionsOptions);

            StringBuilder response = new StringBuilder();
            chatCompletions.getChoices()
                    .forEach(choice -> response.append(choice.getMessage().getContent()));

            LOGGER.debug("Response from OpenAI is : {}", response);
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

    private void debugChatRequestMessageLog(List<ChatRequestMessage> chatMessages) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Convert object to JSON string
            String jsonString = objectMapper.writeValueAsString(chatMessages);
            LOGGER.debug("Chat messages are : {}", jsonString);   
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
