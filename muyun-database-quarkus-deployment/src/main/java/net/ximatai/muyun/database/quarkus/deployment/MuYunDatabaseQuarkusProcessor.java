package net.ximatai.muyun.database.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseQuarkus;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseProducer;

public class MuYunDatabaseQuarkusProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(MuYunDatabaseQuarkus.FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(MuYunDatabaseProducer.class)
                .setUnremovable()
                .build();
    }
}
