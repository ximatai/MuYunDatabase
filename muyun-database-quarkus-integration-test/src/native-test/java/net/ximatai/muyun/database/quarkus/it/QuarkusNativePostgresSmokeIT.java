package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusIntegrationTest
class QuarkusNativePostgresSmokeIT {

    @TestHTTPResource("/muyun/native-probe/repository")
    URI repositoryUri;

    @TestHTTPResource("/muyun/native-probe/transaction")
    URI transactionUri;

    @Test
    void nativeApplicationRunsRepositoryProbeAgainstPostgres() throws Exception {
        assumeTrue(Boolean.getBoolean("muyun.native.postgres.enabled"));

        assertEquals("repository:renamed", QuarkusNativeSmokeIT.get(repositoryUri));
        assertEquals("transaction:rolled-back", QuarkusNativeSmokeIT.get(transactionUri));
    }
}
