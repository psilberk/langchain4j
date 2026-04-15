package dev.langchain4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import oracle.jdbc.pool.OracleDataSource;

public class Main {
    public static void main(String[] args) throws SQLException, IOException {

        // Build a chat model client (LangChain4j) that will call the OpenRouter endpoint
        ChatModel model = OpenAiChatModel.builder()
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
                .dataSource(OracleWalletDataSourceFactory.createconnection())
                .tableName("hello_hello_helloo")
                .ttl(Duration.ofHours(1))
                .build();

        // A stable identifier for the conversation session/user.

        String memoryId = "user123-session0089000000000000000909900000";

        // In-memory window on top of the persistent store

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(memorystore)
                .build();


        // Build an AI service that maps assistant interface methods to LLM calls
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .tools(new Demotools())
                .build();


        byte[] bytes = Files.readAllBytes(Path.of("/Users/bilallaariny/Downloads/Oracle-Morocco.jpg"));
        String b64 = Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:image/jpg;base64," + b64;

        List<Content> contents = List.of(
                new ImageContent(dataUrl),
                new TextContent("What is in this image?")
        );

        List<ChatMessage> existing = memorystore.getMessages(memoryId);
        List<ChatMessage> merged = new ArrayList<>(existing);
        merged.add(new UserMessage(contents));
        memorystore.updateMessages(memoryId, merged);

        String answer1 = assistant.chat("What time is it right now? Use the currentTimeUtc tool.");
        System.out.println("Bot: " + answer1);
        // 2) Ask the question automatically (no CLI)
        String answer2 = assistant.chat("What is in this image?");

        System.out.println("Bot: " + answer2);


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
