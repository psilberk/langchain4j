package dev.langchain4j.model.oracleai;

import com.oracle.bmc.ClientConfiguration;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.ChatDetails;
import com.oracle.bmc.generativeaiinference.model.CohereChatRequest;
import com.oracle.bmc.generativeaiinference.model.OnDemandServingMode;
import com.oracle.bmc.generativeaiinference.requests.ChatRequest;
import com.oracle.bmc.generativeaiinference.responses.ChatResponse;
import com.oracle.bmc.retrier.RetryConfiguration;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class OracleAiChatLanguageModel implements ChatLanguageModel {

    private final String endpoint;
    private final String compartmentId;
    private final String modelId;

    private final GenerativeAiInferenceClient generativeAiInferenceClient;

    public OracleAiChatLanguageModel(Builder builder) {

        endpoint = builder.endpoint;
        compartmentId = builder.compartmentId;
        modelId = builder.modelId;

        AuthenticationDetailsProvider provider;
        try {
            // TODO make this configurable, look defaults from OCI Java SDK
            provider = new ConfigFileAuthenticationDetailsProvider("DEFAULT");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ClientConfiguration clientConfiguration = ClientConfiguration
                .builder()
                .readTimeoutMillis(240000)
                .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION)
                .build();

        generativeAiInferenceClient = GenerativeAiInferenceClient
                .builder()
                .endpoint(endpoint)
                .build(provider);

    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        // TODO make model and parameters configurable
        CohereChatRequest chatRequest = CohereChatRequest
                .builder()
                // TODO Check how to work with a list of messages
                // TODO Look what's the replacement for text() (deprecated)
                .message(messages.get(0).text())
                .maxTokens(600)
                .temperature((double) 1)
                .frequencyPenalty((double) 0)
                .topP((double) 0.75)
                // .topK((double)0)
                .isStream(false)
                .build();

        ChatDetails details = ChatDetails
                .builder()
                .servingMode(
                        OnDemandServingMode
                                .builder()
                                // TODO bring models ID from OCI Java SDK or ask user to provide it.
                                .modelId(modelId)
                                .build())
                .compartmentId(compartmentId)
                .chatRequest(chatRequest)
                .build();

        ChatRequest request = ChatRequest
                .builder()
                .chatDetails(details)
                .build();

        ChatResponse response = generativeAiInferenceClient.chat(request);
        AiMessage aiMessage = new AiMessage(response.getChatResult().getChatResponse().toString());
        return Response.from(aiMessage);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private String compartmentId;
        private String modelId;

        public Builder endpoint(String endpoint) {
            this.endpoint = ensureNotNull(endpoint, "endpoint");
            return this;
        }

        public Builder compartmentId(String compartmentId) {
            this.compartmentId = compartmentId;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        private Builder() {
        }

        public OracleAiChatLanguageModel build() {
            return new OracleAiChatLanguageModel(this);
        }
    }
}