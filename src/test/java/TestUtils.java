import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.Base58;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

class TestUtils {

    private static final String INDEX_NAME = "product";
    private static String hostname = null;
    private static int port = 0;

    private static InputStream resolveMappingDefinitionStream() {
        return TestUtils.class.getResourceAsStream("/mappings/product.json");
    }

    static void startAndConfigureElasticsearch() {
        GenericContainer elasticsearchContainer = new GenericContainer(
                new ImageFromDockerfile()
                        .withFileFromClasspath("synonymes.txt", "synonymes.txt")
                        .withFileFromClasspath("Dockerfile", "Dockerfile")
        ).withNetworkAliases("elasticsearch-" + Base58.randomString(6))
                .withEnv("discovery.type", "single-node");
        elasticsearchContainer.addExposedPorts(9200, 9300);
        elasticsearchContainer.setWaitStrategy((new HttpWaitStrategy())
                .forPort(9200)
                .forStatusCodeMatching((response) -> response == 200 || response == 401)
                .withStartupTimeout(Duration.ofMinutes(2L)));
        elasticsearchContainer.start();
        configHostnameAndPortELS(elasticsearchContainer.getContainerIpAddress(), elasticsearchContainer.getMappedPort(9200));
    }

    static void createIndex() throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPut putAction = new HttpPut(getBaseUrlElasticsearch());
            putAction.setEntity(new InputStreamEntity(resolveMappingDefinitionStream(), ContentType.APPLICATION_JSON));
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

    private static void configHostnameAndPortELS(String hostname, int port){
        TestUtils.hostname = hostname;
        TestUtils.port = port;
    }

    static String getBaseUrlElasticsearch() {
        return "http://" + hostname + ":" + port + "/" + INDEX_NAME;
    }

}
