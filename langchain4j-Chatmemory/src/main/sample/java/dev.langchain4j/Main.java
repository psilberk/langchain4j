package dev.langchain4j;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Scanner;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

public class Main {
    public static void main(String[] args) throws SQLException {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(getOpenAiApiKey())
                .baseUrl("https://openrouter.ai/api/v1")

                .modelName("openai/gpt-4o-mini")
                .maxTokens(512)

                .customHeaders(Map.of(
                        "HTTP-Referer", "https://your-app.example",  // can be your internal site/repo URL
                        "X-Title", "langchain4j-demo"
                ))
                .build();
        OracleMemoryStore memorystore=new OracleMemoryStore(Duration.ofHours(1));
        String memoryId = "user123-sessionB";
        ChatMemory chatMemory=MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(memorystore)
                .build();
        Assistant assistant= AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.print("You: ");
                String user = sc.nextLine();
                if ("exit".equalsIgnoreCase(user)) break;

                String answer = assistant.chat(user);
                System.out.println("Bot: " + answer);
            }
        }

    }
    static String getOpenAiApiKey() {


            String envVarName = System.getenv("API_KEY_ENV_NAME");
            if (envVarName == null || envVarName.isBlank()) {
                throw new IllegalStateException("API_KEY_ENV_NAME missing in app.properties");
            }

            return envVarName;

    }}
