# Oracle Database Embedding Store
This module implements `EmbeddingStore` using Oracle Database.

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

## Usage

Instances of this store can be created by configuring a builder. The builder 
requires that a DataSource and an embedding table be provided. The distance 
between two vectors is calculated using [cosine similarity](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/cosine-similarity.html)
which measures the cosine of the angle between two vectors.

It is recommended to configure a DataSource which pools connections, such as the
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

The builder allows you to create an index on the embedding column by providing
the CreateOption for the vector index using the vector index method. This will 
create an Inverted File Flat (IVF) index on the embedding column with default
configuration.

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
            .vectorIndex(CREATE_OR_REPLACE)
            .build())
    .build();
```

To configure the index, create a Hierarchical Navigable Small World index or
create an index on the JSON keys of the metadata column you can use index 
builders. Index builders allow you to create three types on indexes:
- IVF_VECTOR_INDEX: creates an Inverted File Flat (IVF) index on the embedding
column;
- HNSW_VECTOR_INDEX: creates a Hierarchical Navigable Small World (HNSW) index
on the embedding column;
- FUNCTION_JSON_INDEX: creates a function based index on one or several keys of
the JSON document using the same function used by the embedding store to filter.

```java
JsonIndexBuilder jsonIndexBuilder = (JsonIndexBuilder) embeddingStore.getIndexBuilder(IndexType.FUNCTION_JSON_INDEX);
String indexName = embeddingStore.createIndex(
    jsonIndexBuilder
        .key("author", OracleType.VARCHAR2, JsonIndexBuilder.Order.ASC)
        .key("publication_year", OracleType.NUMBER, JsonIndexBuilder.Order.ASC)
        .createOption(CreateOption.CREATE_OR_REPLACE)
        .build());
```

```java
IVFIndexBuilder ivfIndexBuilder = (IVFIndexBuilder) embeddingStore.getIndexBuilder(IndexType.IVF_VECTOR_INDEX);
String indexName = embeddingStore.createIndex(
    ivfIndexBuilder
        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
        .minVectorsPerPartition(10)
        .neighborPartitions(3)
        .samplePerPartition(15)
        .targetAccuracy(90)
        .build());
```

Indexes can be dropped by calling the dropIndex(String indexName) method:
```java
embeddingStore.dropIndex(indexName);
```

For more information about Oracle AI Vector Search refer to the [documentation](https://docs.oracle.com/en/database/oracle/oracle-database/23/vecse/overview-ai-vector-search.html).

## Running the Test Suite
By default, integration tests will
[run a docker image of Oracle Database using TestContainers](https://java.testcontainers.org/modules/databases/oraclefree/).
Alternatively, the tests can connect to an Oracle Database if the following environment variables are configured:
- ORACLE_JDBC_URL : Set to an [Oracle JDBC URL](https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0), such as `jdbc:oracle:thin@example:1521/serviceName`
- ORACLE_JDBC_USER : Set to the name of a database user. (Optional)
- ORACLE_JDBC_PASSWORD : Set to the password of a database user. (Optional)
