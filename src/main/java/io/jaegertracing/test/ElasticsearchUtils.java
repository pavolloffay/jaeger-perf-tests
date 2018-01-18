package io.jaegertracing.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Pavol Loffay
 */
public class ElasticsearchUtils {
  private static final Logger log = LoggerFactory.getLogger(ElasticsearchUtils.class);

  private final String host;
  private final int port;
  private final String spanIndex;

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public ElasticsearchUtils(String host, int port) {
    this.host = host;
    this.port = port;
    this.restClient = getESRestClient();
    this.objectMapper = new ObjectMapper();
    String formattedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    this.spanIndex = "jaeger-span-" + formattedDate;
  }

  public int countSpansUntilNoChange() {
    log.info("Using ElasticSearch index : [" + spanIndex + "]" );
    int spansCount = 0;
    boolean change = true;
    while (change) {
      int previousCount = spansCount;
      spansCount = countSpans();
      refreshSpanIndex();
      log.info("found {} traces in ES", spansCount);
      try {
        TimeUnit.SECONDS.sleep(2);
      } catch (InterruptedException e) {
        log.error("Could not sleep between getting data from ES", e);
        throw new RuntimeException(e);
      }
      if (spansCount == -1) {
        continue;
      }
      change = previousCount != spansCount;
    }
    return spansCount;
  }

  public void refreshSpanIndex() {
    try {
      Response response = restClient.performRequest("GET", "/" + spanIndex + "/_refresh");
      if (response.getStatusLine().getStatusCode() >= 300) {
        throw new RuntimeException("Could not refresh span index");
      }
    } catch (IOException ex) {
      log.error("Could not make request to refresh span index", ex);
//      throw new RuntimeException("Could not make request to refresh span index", ex);
    }
  }

  public int countSpans() {
    try {
      Response response = restClient.performRequest("GET", "/" + spanIndex + "/_count");
      String responseBody = EntityUtils.toString(response.getEntity());
      JsonNode jsonPayload = objectMapper.readTree(responseBody);
      JsonNode count = jsonPayload.get("count");
      return count.asInt();
    } catch (IOException ex) {
      log.error("Could not make request to count span index", ex);
      return -1;
    }
  }

  private RestClient getESRestClient() {
    return RestClient.builder(
        new HttpHost(host, port, "http"))
        .build();
  }
}
