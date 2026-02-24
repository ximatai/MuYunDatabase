package net.ximatai.muyun.database.samples.starterminimal;

import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableMuYunRepositories(basePackageClasses = DemoUserRepository.class)
public class StarterMinimalApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarterMinimalApplication.class, args);
    }

    @Bean
    CommandLineRunner demoRunner(TxDemoService txDemoService) {
        return args -> txDemoService.runRollbackDemo();
    }
}
