package net.ximatai.muyun.database.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseRecorder;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseQuarkus;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseProducer;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MuYunDatabaseQuarkusProcessor {

    private static final DotName MUYUN_REPOSITORY = DotName.createSimple(MuYunRepository.class);
    private static final DotName ENTITY_DAO = DotName.createSimple(EntityDao.class);

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

    @BuildStep
    List<ReflectiveClassBuildItem> repositoryEntitiesForReflection(CombinedIndexBuildItem index) {
        List<ReflectiveClassBuildItem> reflectiveEntities = new ArrayList<>();
        for (DotName repository : repositoryInterfaces(index.getIndex())) {
            repositoryEntityType(index.getIndex(), repository)
                    .map(DotName::toString)
                    .map(entity -> ReflectiveClassBuildItem.builder(entity)
                            .constructors()
                            .methods()
                            .fields()
                            .reason("@MuYunRepository EntityDao entity")
                            .build())
                    .ifPresent(reflectiveEntities::add);
        }
        return reflectiveEntities;
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

    Optional<DotName> repositoryEntityType(IndexView index, DotName repositoryName) {
        ClassInfo repository = index.getClassByName(repositoryName);
        if (repository == null) {
            return Optional.empty();
        }
        return resolveEntityDaoType(index, repository, Map.of());
    }

    private Optional<DotName> resolveEntityDaoType(IndexView index,
                                                  ClassInfo type,
                                                  Map<String, Type> bindings) {
        for (Type interfaceType : type.interfaceTypes()) {
            Optional<DotName> direct = resolveEntityDaoType(index, interfaceType, bindings);
            if (direct.isPresent()) {
                return direct;
            }
        }
        return Optional.empty();
    }

    private Optional<DotName> resolveEntityDaoType(IndexView index,
                                                  Type interfaceType,
                                                  Map<String, Type> bindings) {
        if (interfaceType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterized = interfaceType.asParameterizedType();
            if (ENTITY_DAO.equals(parameterized.name())) {
                return resolveClassType(parameterized.arguments().getFirst(), bindings);
            }

            ClassInfo rawInterface = index.getClassByName(parameterized.name());
            if (rawInterface == null) {
                return Optional.empty();
            }
            Map<String, Type> nextBindings = new HashMap<>(bindings);
            List<Type> arguments = parameterized.arguments();
            List<org.jboss.jandex.TypeVariable> parameters = rawInterface.typeParameters();
            for (int i = 0; i < Math.min(arguments.size(), parameters.size()); i++) {
                nextBindings.put(parameters.get(i).identifier(), resolveBoundType(arguments.get(i), bindings));
            }
            return resolveEntityDaoType(index, rawInterface, nextBindings);
        }

        if (interfaceType.kind() == Type.Kind.CLASS) {
            ClassInfo rawInterface = index.getClassByName(interfaceType.name());
            if (rawInterface == null) {
                return Optional.empty();
            }
            return resolveEntityDaoType(index, rawInterface, bindings);
        }

        return Optional.empty();
    }

    private Optional<DotName> resolveClassType(Type type, Map<String, Type> bindings) {
        Type resolved = resolveBoundType(type, bindings);
        if (resolved.kind() == Type.Kind.CLASS || resolved.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            return Optional.of(resolved.name());
        }
        return Optional.empty();
    }

    private Type resolveBoundType(Type type, Map<String, Type> bindings) {
        if (type.kind() == Type.Kind.TYPE_VARIABLE) {
            return bindings.getOrDefault(type.asTypeVariable().identifier(), type);
        }
        return type;
    }
}
