package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusIntegrationTest
class QuarkusNativeSmokeIT {

    @TestHTTPResource("/muyun/native-probe/repository")
    URI repositoryUri;

    @TestHTTPResource("/muyun/native-probe/transaction")
    URI transactionUri;

    @Test
    void nativeApplicationRunsRepositoryProbe() throws Exception {
        assertEquals("repository:renamed", get(repositoryUri));
        assertEquals("transaction:rolled-back", get(transactionUri));
    }

    static String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }
}
