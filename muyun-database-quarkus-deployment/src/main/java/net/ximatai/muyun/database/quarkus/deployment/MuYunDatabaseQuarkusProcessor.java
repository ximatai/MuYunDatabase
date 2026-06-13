package net.ximatai.muyun.database.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseRecorder;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseQuarkus;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseProducer;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

public class MuYunDatabaseQuarkusProcessor {

    private static final DotName MUYUN_REPOSITORY = DotName.createSimple(MuYunRepository.class);

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

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    List<SyntheticBeanBuildItem> repositories(CombinedIndexBuildItem index, MuYunDatabaseRecorder recorder) {
        List<SyntheticBeanBuildItem> repositories = new ArrayList<>();
        for (DotName repository : repositoryInterfaces(index.getIndex())) {
            repositories.add(SyntheticBeanBuildItem.configure(repository)
                    .types(Type.create(repository, Type.Kind.CLASS))
                    .scope(ApplicationScoped.class)
                    .unremovable()
                    .supplier(recorder.repositorySupplier(repository.toString()))
                    .setRuntimeInit()
                    .done());
        }
        return repositories;
    }

    List<DotName> repositoryInterfaces(IndexView index) {
        List<DotName> repositories = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(MUYUN_REPOSITORY)) {
            ClassInfo repository = annotation.target().asClass();
            if (!repository.isInterface()) {
                throw new IllegalStateException("@MuYunRepository must be used on an interface: " + repository.name());
            }
            repositories.add(repository.name());
        }
        return repositories;
    }
}
