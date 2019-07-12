import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.io.InputStream;

class TestUtils {

    private static final String INDEX_NAME = "product";
    private static String hostname = null;
    private static int port = 0;

    private static InputStream resolveSettingsDefinitionStream() {
        return TestUtils.class.getResourceAsStream("/mappings/product.json");
    }

    static void startAndConfigureElasticsearch() {
        ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.2.0");
        elasticsearchContainer.addExposedPorts(9200, 9300);
        elasticsearchContainer.start();
        configHostnameAndPortELS(elasticsearchContainer.getContainerIpAddress(), elasticsearchContainer.getMappedPort(9200));
    }

    static void createIndex() throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPut putAction = new HttpPut(getBaseUrlElasticsearch());
            putAction.setEntity(new InputStreamEntity(resolveSettingsDefinitionStream(), ContentType.APPLICATION_JSON));
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                System.out.println(EntityUtils.toString(entity));
                if (status < 200 || status >= 300) {
                    throw new RuntimeException("Unexpected response status: " + status);
                }
                return "";
            };
            httpclient.execute(putAction, responseHandler);
        }
    }

    private static void configHostnameAndPortELS(String hostname, int port) {
        TestUtils.hostname = hostname;
        TestUtils.port = port;
    }

    static String getBaseUrlElasticsearch() {
        return "http://" + hostname + ":" + port + "/" + INDEX_NAME;
    }

}
