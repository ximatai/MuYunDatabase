package net.ximatai.muyun.database.jdbi;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertSame;

class JdbiRecommendedPluginsTest {

    @Test
    void installPostgresLoadsOptionalPluginReflectively() {
        Jdbi jdbi = Jdbi.create(new UnusedDataSource());

        assertSame(jdbi, JdbiRecommendedPlugins.installPostgres(jdbi));
    }

    private static final class UnusedDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("connection is not used by plugin installation test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("connection is not used by plugin installation test");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
