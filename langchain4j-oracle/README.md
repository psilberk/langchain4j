# Oracle Database Embedding Store and ChatMemory Store
This module implements `EmbeddingStore` and `ChatMemoryStore` using Oracle Database.

## Requirements
- Oracle Database 23.4 or newer

## Installation
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-oracle</artificatId>
    <version>0.1.0</version>
</dependency>
```

## Oracle Database Embedding Store

Instances of this store can be created by configuring a builder. The builder 
requires that a DataSource and an embedding table be provided. The distance 
between two vectors is calculated using [cosine similarity](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/cosine-similarity.html)
which measures the cosine of the angle between two vectors.

It is recommended to configure a DataSource with pools connections, such as the
Universal Connection Pool or Hikari. A connection pool will avoid the latency of
repeatedly creating new database connections.

If an embedding table already exists in your database provide the table name.

```java
EmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table")
   .build();
```

If the table does not already exist, it can be created by passing a CreateOption
to the builder.

```java
EmbeddingStore embeddingStore = OracleEmbeddingStore.builder()
   .dataSource(myDataSource)
   .embeddingTable("my_embedding_table", CreateOption.CREATE_IF_NOT_EXISTS)
   .build();
```

By default the embedding table will have the following columns:

| Name | Type | Description |
| ---- | ---- | ----------- |
| id | VARCHAR(36) | Primary key. Used to store UUID strings which are generated when the embedding store |
| embedding | VECTOR(*, FLOAT32) | Stores the embedding |
| text | CLOB | Stores the text segment |
| metadata | JSON | Stores the metadata |

If the columns of your existing table do not match the predefined column names 
or you would like to use different column names, you can use a EmbeddingTable 
builder to configure your embedding table.

```java
OracleEmbeddingStore embeddingStore =
OracleEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder()
            .createOption(CREATE_OR_REPLACE) // use NONE if the table already exists
            .name("my_embedding_table")
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .build())
    .build();
```

The builder allows to create an indexes on the embedding and metadata columns of the
EmbeddingTable by providing an instance of the Index class. Two builders allow to 
create instances of the Index class: IVFIndexBuilder and JSONIndexBuilder.

*IVFIndexBuilder* allows to configure an **IVF (Inverted File Flat)** index on the embedding
column of the EmbeddingTable.

```java
OracleEmbeddingStore embeddingStore =
    OracleEmbeddingStore.builder()
        .dataSource(myDataSource)
        .embeddingTable(EmbeddingTable.builder()
            .createOption(CreateOption.CREATE_OR_REPLACE) // use NONE if the table already exists
            .name("my_embedding_table")
            .idColumn("id_column_name")
            .embeddingColumn("embedding_column_name")
            .textColumn("text_column_name")
            .metadataColumn("metadata_column_name")
            .build())
        .index(Index.ivfIndexBuilder().createOption(CreateOption.CREATE_OR_REPLACE).build())
        .build();
```

*JSONIndexBuilder* allows to configure a function-based index on keys of the metadata 
column of the EmbeddingTable.

```java
OracleEmbeddingStore.builder()
    .dataSource(myDataSource)
    .embeddingTable(EmbeddingTable.builder()
        .createOption(CreateOption.CREATE_OR_REPLACE) // use NONE if the table already exists
        .name("my_embedding_table")
        .idColumn("id_column_name")
        .embeddingColumn("embedding_column_name")
        .textColumn("text_column_name")
        .metadataColumn("metadata_column_name")
        .build())
    .index(Index.jsonIndexBuilder()
        .createOption(CreateOption.CREATE_OR_REPLACE)
        .key("name", String.class, JSONIndexBuilder.Order.ASC)
        .key("year", Integer.class, JSONIndexBuilder.Order.DESC)
        .build())
    .build();
```

For more information about Oracle AI Vector Search refer to the [documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/overview-ai-vector-search.html).

## Running the Test Suite
By default, integration tests will
[run a docker image of Oracle Database using TestContainers](https://java.testcontainers.org/modules/databases/oraclefree/).
Alternatively, the tests can connect to an Oracle Database if the following environment variables are configured:
- ORACLE_JDBC_URL : Set to an [Oracle JDBC URL](https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0), such as `jdbc:oracle:thin@example:1521/serviceName`
- ORACLE_JDBC_USER : Set to the name of a database user. (Optional)
- ORACLE_JDBC_PASSWORD : Set to the password of a database user. (Optional)

## Oracle Database Chat Memory Store
This store implements ChatMemoryStore using Oracle Database. It persists and retrieves LangChain4j chat 
history for a given memory ID (memory_id) using an Oracle JSON column.

## Usage
Instances of this store can be created by configuring a builder. 
The builder requires an OracleDataSource and optionally allows configuring:

tableName (default: chat_memory)

ttl (default: Duration.ZERO → expiration disabled)

You may provide your own table name if you want to store chat memory in a specific table. **The table name is configurable, but the column names and types are fixed** and must match the expected schema shown below (`memory_id`, `messages_json`, `expires_at`).

If you enable TTL, a value of **7 days or more** is generally recommended to avoid expiring conversation context too quickly (choose a value that aligns with your product needs and retention policies).

```java

OracleMemoryStore store = OracleMemoryStore.builder()
.oracleDataSource(myOracleDataSource)
.tableName("chat_memory")          // optional
.ttl(Duration.ofDays(7))           // optional; null/<=0 disables expiration
.build();
```
Use it with LangChain4j chat memory:

```java
ChatMemory memory = MessageWindowChatMemory.builder()
.id("user123-sessionB123")
.maxMessages(20)
.chatMemoryStore(store)
.build();
```
## Default Table
On initialization, the store attempts to create the table (if it does not already exist):

| Name | Type         | Description |
| ---- |--------------| ----------- |
| memory_id | VARCHAR(200) | Primary key. Chat session identifier |
| messages_json | JSON         | JSON array of messages (entire conversation history) |
| expires_at | TIMESTAMP NULL         | Expiration timestamp; NULL means no expiration |

## Stored JSON Format

The messages_json column stores a JSON array of message objects. Example:
```json
[
{ "text": "You are a helpful assistant", "type": "SYSTEM" },
{
"contents": [ { "text": "hey", "type": "TEXT" } ],
"type": "USER"
},
{ "text": "Hello! How can I assist you today?", "type": "AI" }
]
```

## Running the Test Suite
By default, the tests can connect to an Oracle Database if the following environment variables are configured:

- ORACLE_JDBC_URL : Set to an [Oracle JDBC URL](https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0) , such as `jdbc:oracle:thin@example:1521/serviceName`
- ORACLE_JDBC_USER : Set to the name of a database user. 
- ORACLE_JDBC_PASSWORD : Set to the password of a database user. 
