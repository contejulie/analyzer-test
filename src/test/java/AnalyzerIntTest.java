import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AnalyzerIntTest {

    private static Map<String, String[]> cases = new Hashtable<String, String[]>();

    static {
        cases.put("confiture d'abricot", new String[]{"confitur", "abricot"});
        cases.put("sucre au miel", new String[]{"sucr", "miel"});
        cases.put("citron", new String[]{"citron", "farin", "chocolat"});
        cases.put("chaussure 1999 6kg", new String[]{"chausur", "kg"});
        cases.put("je mangeais des glaces", new String[]{"mangeai", "glac"});
        cases.put("en de la le les un une des à et au aux", new String[]{});
    }


    @Before
    public void setup() throws IOException {
        TestUtils.startAndConfigureElasticsearch();
        TestUtils.createIndex();
    }

    @Test
    public void testAnalyzerWithAllFilters() {
        String analyzer = "default";
        cases.forEach((textToAnalyse, expectedTokens) -> analyzerTestWithInput(textToAnalyse, expectedTokens, analyzer));
    }

    @Test
    public void testSpecificCaseForDigitCharacter() {
        String textToAnalyse = "12324-A";
        String[] expectedTokens = new String[1];
        expectedTokens[0] = "12324";
        String analyzer = "product_id";
        analyzerTestWithInput(textToAnalyse, expectedTokens, analyzer);
    }

    @Test
    public void testSpecificCaseForNoFilter() {
        String analyzer = "without_filter";

        Map<String, String[]> cases = new HashMap<>();
        cases.put("je mangeais des glaces", new String[]{"je", "mangeais", "des", "glaces"});

        cases.forEach((textToAnalyse, expectedTokens) -> analyzerTestWithInput(textToAnalyse, expectedTokens, analyzer));
    }

    private void analyzerTestWithInput(String textToAnalyse, String[] expectedTokens, String analyzer) {
        try {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpPost postAction = new HttpPost(TestUtils.getBaseUrlElasticsearch() + "/_analyze");
                String body = "{\n" +
                        "  \"analyzer\": \"" + analyzer + "\",\n" +
                        "  \"text\": \"" + textToAnalyse + "\"\n" +
                        "}";
                postAction.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
                ResponseHandler<String[]> responseHandler = response -> {
                    int status = response.getStatusLine().getStatusCode();
                    if (status < 200 || status >= 300) {
                        throw new RuntimeException("Unexpected response status: " + status);
                    }
                    String responseBody = EntityUtils.toString(response.getEntity());
                    ObjectMapper mapper = new ObjectMapper();
                    Map map = mapper.readValue(responseBody, Map.class);
                    List<Map> tokensMap = (List) (map.get("tokens"));
                    return tokensMap.stream().map(t -> t.get("token")).toArray(String[]::new);
                };
                String[] tokens = httpclient.execute(postAction, responseHandler);

                assertThat(tokens).containsExactlyInAnyOrder(expectedTokens);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
