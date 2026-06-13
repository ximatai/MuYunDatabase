package net.ximatai.muyun.database.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseQuarkus;

public class MuYunDatabaseQuarkusProcessor {

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(MuYunDatabaseQuarkus.FEATURE);
    }
}
