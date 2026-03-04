package dev.langchain4j;
import dev.langchain4j.service.SystemMessage;

public interface Assistant {
    @SystemMessage("You are a helpful assistant")
    String chat(String userMessage);
}
