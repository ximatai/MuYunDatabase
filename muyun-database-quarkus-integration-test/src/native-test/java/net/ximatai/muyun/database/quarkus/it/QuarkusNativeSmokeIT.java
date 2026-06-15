package net.ximatai.muyun.database.quarkus.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusIntegrationTest
class QuarkusNativeSmokeIT {

    @Test
    void nativeApplicationStarts() {
        assertTrue(true);
    }
}
