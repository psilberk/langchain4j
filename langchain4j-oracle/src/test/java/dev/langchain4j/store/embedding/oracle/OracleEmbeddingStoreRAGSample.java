package dev.langchain4j.store.embedding.oracle;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.sql.SQLException;
import java.util.List;


interface Assistant {
    String chat(String userMessage);
}
public class OracleEmbeddingStoreRAGSample {

    public static void main(String[] args) throws SQLException {

        PoolDataSource dataSource = PoolDataSourceFactory.getPoolDataSource();
        dataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        dataSource.setConnectionPoolName("mypool");
        dataSource.setUser("scott");
        dataSource.setPassword("tiger");
        dataSource.setURL("jdbc:oracle:thin:@localhost:1521/FREEPDB1");

        EmbeddingStore<TextSegment> store = OracleEmbeddingStore.builder()
            .dataSource(dataSource)
            .table("sample_vector")
            .dimension(384)
            .useIndex(true)
            .createTable(true)
            .dropTableFirst(true)
            .normalizeVectors(true)
            .build();

        // Add documents
        Document document = UrlDocumentLoader.load("https://en.wikipedia.org/wiki/2022_FIFA_World_Cup_squads", new TextDocumentParser());
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        Embedding embedding = embeddingModel
            .embed(document.toTextSegment())
            .content();
        store.add(embedding, document.toTextSegment());

        // Create the Chain
        Assistant assistant = AiServices.builder(Assistant.class)
            .chatLanguageModel(OpenAiChatModel.withApiKey("sk-proj-JdH2IS27YNW2PsnLjiMLT3BlbkFJtrvO7tOoD9OagdsFr7GH"))
            .chatMemory(MessageWindowChatMemory.withMaxMessages(1))
            .contentRetriever(EmbeddingStoreContentRetriever.from(store))
            .build();

        String answer = assistant.chat("What were the players that won the 2022 Soccer World Cup?");
        System.out.println(answer);
    }
}
