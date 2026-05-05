package dev.langchain4j;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Scanner;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class Main {
    public static void main(String[] args) throws SQLException {

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
                .tableName("hello_hello_hellooooooo")
                .ttl(Duration.ofSeconds(40))
                .build();

        // A stable identifier for the conversation session/user.

        String memoryId = "user123-session00890000000909900000";

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
                .tools(Demotools.class)
                .build();

        System.out.println("Chat started. Type your message and press Enter.");
        System.out.println("Type 'exit' or 'quit' to stop.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String userInput = scanner.nextLine().trim();
                if (userInput.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(userInput) || "quit".equalsIgnoreCase(userInput)) {
                    System.out.println("Bye.");
                    break;
                }

                String answer = assistant.chat(userInput);
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
