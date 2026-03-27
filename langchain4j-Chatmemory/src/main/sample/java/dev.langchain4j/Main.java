package dev.langchain4j;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import oracle.jdbc.pool.OracleDataSource;

public class Main {
    public static void main(String[] args) throws SQLException {

        // Build a chat model client (LangChain4j) that will call the OpenRouter endpoint
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(getOpenAiApiKey())
                .baseUrl("https://openrouter.ai/api/v1")
                .modelName("openai/gpt-4o-mini")
                .maxTokens(512)
                .customHeaders(Map.of(

                        "HTTP-Referer", "https://your-app.example",
                        "X-Title", "langchain4j-demo"
                ))
                .build();

        // Create a memory store backed by Oracle DB using wallet-based datasource/connection

        OracleMemoryStore memorystore = OracleMemoryStore.builder()
                .oracleDataSource(OracleWalletDataSourceFactory.createconnection())
                .tableName("hello_hello_hello")
                .ttl(Duration.ofHours(1))
                .build();

        // A stable identifier for the conversation session/user.

        String memoryId = "user123-sessionB123";

        // In-memory window on top of the persistent store

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(memorystore)
                .build();

        // Build an AI service that maps assistant interface methods to LLM calls
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();

        // Simple CLI loop to chat with the assistant until user types "exit"
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                String user = sc.nextLine();
                if ("exit".equalsIgnoreCase(user)) break;

                // Sends the user message to the LLM (with memory context) and returns the response
                String answer = assistant.chat(user);
                System.out.println("Bot: " + answer);
            }
        }
    }


    static String getOpenAiApiKey() {

        // This reads an environment variable called API_KEY_ENV_NAME
        String envVarName = System.getenv("API_KEY_ENV_NAME");

        if (envVarName == null || envVarName.isBlank()) {

            throw new IllegalStateException("API_KEY_ENV_NAME missing in environment");
        }

        return envVarName;
    }
}
