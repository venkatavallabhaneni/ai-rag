package com.venkat.rag.store;


import com.venkat.rag.model.Chunk;
import com.venkat.rag.model.VectorRecord;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.grpc.DataType;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

@Primary
@Component
public class MilvusVectorStore implements VectorStore {

  private final MilvusServiceClient client;
  private final String collection;
  private final int vectorDim;
  private final MetricType metricType;

  // Field names in Milvus
  private static final String F_ID = "id";
  private static final String F_VECTOR = "vector";
  private static final String F_DOC_ID = "documentId";
  private static final String F_TITLE = "documentTitle";
  private static final String F_SOURCE = "source";
  private static final String F_CHUNK_INDEX = "chunkIndex";
  private static final String F_TEXT = "text";

  public MilvusVectorStore(
      @Value("${milvus.host}") String host,
      @Value("${milvus.port}") int port,
      @Value("${milvus.collection}") String collection,
      @Value("${milvus.vectorDim}") int vectorDim,
      @Value("${milvus.metricType:COSINE}") String metricType
  ) {
    this.client = new MilvusServiceClient(
        ConnectParam.newBuilder()
            .withHost(host)
            .withPort(port)
            .build()
    );
    this.collection = collection;
    this.vectorDim = vectorDim;
    this.metricType = MetricType.valueOf(metricType.toUpperCase(Locale.ROOT));

    ensureCollectionAndIndex();
  }

  @Override
  public void upsert(VectorRecord record) {
    // Milvus "upsert" behavior depends on PK settings; simplest:
    // insert with primary key = id. If same id exists and you want replace,
    // delete-by-id then insert (we keep Day-3 simple: insert unique chunk ids).
    if (record.vector().size() != vectorDim) {
      throw new IllegalArgumentException("Embedding dim mismatch. Expected " + vectorDim + " got " + record.vector().size());
    }

    // Convert Double -> Float (Milvus expects float vectors)
    List<Float> floatVec = record.vector().stream().map(Double::floatValue).toList();

    Chunk c = record.payload();

    List<InsertParam.Field> fields = List.of(
        new InsertParam.Field(F_ID, List.of(record.id())),
        new InsertParam.Field(F_VECTOR, List.of(floatVec)),
        new InsertParam.Field(F_DOC_ID, List.of(c.documentId())),
        new InsertParam.Field(F_TITLE, List.of(c.documentTitle())),
        new InsertParam.Field(F_SOURCE, List.of(c.source())),
        new InsertParam.Field(F_CHUNK_INDEX, List.of((long) c.chunkIndex())),
        new InsertParam.Field(F_TEXT, List.of(c.text()))
    );

    R<?> res = client.insert(
        InsertParam.newBuilder()
            .withCollectionName(collection)
            .withFields(fields)
            .build()
    );

    if (res.getStatus() != 0) {
      throw new RuntimeException("Milvus insert failed: " + res.getMessage());
    }

    // Ensure data is searchable
    client.flush(FlushParam.newBuilder().withCollectionNames(List.of(collection)).build());
  }

  @Override
  public List<ScoredRecord> search(List<Double> queryVector, int topK) {
    if (queryVector.size() != vectorDim) {
      throw new IllegalArgumentException("Query embedding dim mismatch. Expected " + vectorDim + " got " + queryVector.size());
    }
    List<Float> q = queryVector.stream().map(Double::floatValue).toList();

    // Load collection into memory (needed for search)
    client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());

    // Return fields (payload)
    List<String> outFields = List.of(F_ID, F_DOC_ID, F_TITLE, F_SOURCE, F_CHUNK_INDEX, F_TEXT);

    // Index/search params:
    // - For HNSW: {"ef": 64}
    // - For IVF_FLAT: {"nprobe": 10}
    // We'll use HNSW here (fast & good).
    String searchParamsJson = "{\"ef\":64}";

    SearchParam searchParam = SearchParam.newBuilder()
        .withCollectionName(collection)
        .withMetricType(metricType)
        .withVectorFieldName(F_VECTOR)
        .withTopK(topK)
        .withVectors(List.of(q))
        .withParams(searchParamsJson)
        .withOutFields(outFields)
        .build();

    var res = client.search(searchParam);
    if (res.getStatus() != 0) {
      throw new RuntimeException("Milvus search failed: " + res.getMessage());
    }

    SearchResultsWrapper wrapper = new SearchResultsWrapper(res.getData().getResults());

    // Single query vector -> results at index 0
    List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

    // Extract payload fields
    List<?> docIds = wrapper.getFieldData(F_DOC_ID, 0);
    List<?> titles = wrapper.getFieldData(F_TITLE, 0);
    List<?> sources = wrapper.getFieldData(F_SOURCE, 0);
    List<?> chunkIdxs = wrapper.getFieldData(F_CHUNK_INDEX, 0);
    List<?> texts = wrapper.getFieldData(F_TEXT, 0);

    List<ScoredRecord> out = new ArrayList<>();

    for (int i = 0; i < idScores.size(); i++) {
      String id = idScores.get(i).getStrID() != null ? idScores.get(i).getStrID() : String.valueOf(idScores.get(i).getLongID());
      double score = idScores.get(i).getScore(); // for COSINE, higher is better

      String docId = String.valueOf(docIds.get(i));
      String title = String.valueOf(titles.get(i));
      String source = String.valueOf(sources.get(i));
      int chunkIndex = (int) ((Number) chunkIdxs.get(i)).longValue();
      String text = String.valueOf(texts.get(i));

      Chunk chunk = new Chunk(id, docId, title, source, chunkIndex, text);
      VectorRecord rec = new VectorRecord(id, List.of(), chunk);

      out.add(new ScoredRecord(rec, score));
    }

    return out;
  }

  // -------------------- Setup --------------------

  private void ensureCollectionAndIndex() {
    // Check if collection exists
    R<Boolean> has = client.hasCollection(
        HasCollectionParam.newBuilder().withCollectionName(collection).build()
    );
    if (has.getStatus() != 0) {
      throw new RuntimeException("Milvus hasCollection failed: " + has.getMessage());
    }
    if (Boolean.TRUE.equals(has.getData())) {
      return; // assume schema already correct for Day-3 demo
    }

    // Create schema
    List<FieldType> fields = new ArrayList<>();

    fields.add(FieldType.newBuilder()
        .withName(F_ID)
        .withDataType(DataType.VarChar)
        .withMaxLength(256)
        .withPrimaryKey(true)
        .withAutoID(false)
        .build());

    fields.add(FieldType.newBuilder()
        .withName(F_VECTOR)
        .withDataType(DataType.FloatVector)
        .withDimension(vectorDim)
        .build());

    fields.add(FieldType.newBuilder().withName(F_DOC_ID).withDataType(DataType.VarChar).withMaxLength(256).build());
    fields.add(FieldType.newBuilder().withName(F_TITLE).withDataType(DataType.VarChar).withMaxLength(512).build());
    fields.add(FieldType.newBuilder().withName(F_SOURCE).withDataType(DataType.VarChar).withMaxLength(256).build());
    fields.add(FieldType.newBuilder().withName(F_CHUNK_INDEX).withDataType(DataType.Int64).build());
    fields.add(FieldType.newBuilder().withName(F_TEXT).withDataType(DataType.VarChar).withMaxLength(65535).build());

    CreateCollectionParam create = CreateCollectionParam.newBuilder()
        .withCollectionName(collection)
        .withShardsNum(2)
        .withFieldTypes(fields)
        .build();

    R<?> createRes = client.createCollection(create);
    if (createRes.getStatus() != 0) {
      throw new RuntimeException("Milvus createCollection failed: " + createRes.getMessage());
    }

    // Create index (HNSW is great for cosine)
    String indexParams = "{\"M\":16,\"efConstruction\":200}";

    CreateIndexParam index = CreateIndexParam.newBuilder()
        .withCollectionName(collection)
        .withFieldName(F_VECTOR)
        .withIndexType(IndexType.HNSW)
        .withMetricType(metricType)
        .withExtraParam(indexParams)
        .withSyncMode(Boolean.TRUE)
        .build();

    R<?> idxRes = client.createIndex(index);
    if (idxRes.getStatus() != 0) {
      throw new RuntimeException("Milvus createIndex failed: " + idxRes.getMessage());
    }

    // Load (optional here; search() will load too)
    client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build());
  }

}
 
