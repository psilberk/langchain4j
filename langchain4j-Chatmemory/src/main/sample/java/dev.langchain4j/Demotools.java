package dev.langchain4j;

import dev.langchain4j.agent.tool.Tool;
import java.time.Instant;

public class Demotools {

    @Tool("Returns the current time in UTC as ISO-8601")
    public String currentTimeUtc() {
        return Instant.now().toString();
    }
}
