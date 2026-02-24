package net.ximatai.muyun.database.spring.boot.sql;

import net.ximatai.muyun.database.core.IDatabaseOperations;
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.MuYunDatabaseAutoConfiguration;
import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Param;
import net.ximatai.muyun.database.spring.boot.sql.annotation.Select;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MuYunRepositoryScanIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MuYunDatabaseAutoConfiguration.class))
            .withPropertyValues(
                    "sample.db.schema=public",
                    "muyun.database.repository-schema-mode=NONE"
            )
            .withUserConfiguration(RepositoryScanConfig.class);

    @Test
    void shouldRegisterMuYunRepositoryInterfaceViaEnableAnnotation() {
        contextRunner.run(context -> {
            assertNotNull(context.getBean(ScanHybridRepository.class));
            ScanHybridRepository repo = context.getBean(ScanHybridRepository.class);

            ScanUser user = new ScanUser();
            user.id = "u-1";
            user.name = "alice";
            String id = repo.insert(user);
            Integer count = repo.countByName("alice");

            assertEquals("u-1", id);
            assertEquals(1, count);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMuYunRepositories(basePackageClasses = ScanHybridRepository.class)
    static class RepositoryScanConfig {

        @Bean
        DataSource dataSource() {
            return Mockito.mock(DataSource.class);
        }

        @Bean
        IDatabaseOperations<?> operations() {
            @SuppressWarnings("unchecked")
            IDatabaseOperations<Object> ops = (IDatabaseOperations<Object>) Mockito.mock(IDatabaseOperations.class);
            when(ops.insertItem(anyString(), anyString(), anyMap())).thenAnswer(invocation -> {
                Map<String, Object> body = invocation.getArgument(2);
                return body.get("id");
            });
            when(ops.row(anyString(), anyMap())).thenReturn(Map.of("cnt", 1));
            return ops;
        }
    }
}

@MuYunRepository
interface ScanHybridRepository extends EntityDao<ScanUser, String> {

    @Select("select count(*) as cnt from ${sample.db.schema}.demo_user where v_name = #{name}")
    Integer countByName(@Param("name") String name);
}

@Table(name = "demo_user")
class ScanUser {
    @Id
    @Column(length = 64)
    public String id;

    @Column(name = "v_name", length = 64)
    public String name;
}
