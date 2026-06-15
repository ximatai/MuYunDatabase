package net.ximatai.muyun.database.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseRecorder;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseQuarkus;
import net.ximatai.muyun.database.quarkus.MuYunDatabaseProducer;
import net.ximatai.muyun.database.quarkus.MuYunRepository;
import net.ximatai.muyun.database.quarkus.MuYunRepositorySchemaInitializer;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
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
import java.util.stream.Collectors;

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
                .addBeanClass(MuYunRepositorySchemaInitializer.class)
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

    @BuildStep
    List<NativeImageProxyDefinitionBuildItem> repositoryProxiesForNative(CombinedIndexBuildItem index) {
        List<NativeImageProxyDefinitionBuildItem> proxies = new ArrayList<>();
        for (DotName repository : repositoryInterfaces(index.getIndex())) {
            proxies.add(new NativeImageProxyDefinitionBuildItem(repository.toString()));
        }
        return proxies;
    }

    @BuildStep
    ReflectiveClassBuildItem jdbiConfigReflection() {
        return ReflectiveClassBuildItem.builder(
                        "org.jdbi.v3.core.config.internal.ConfigCaches",
                        "org.jdbi.v3.core.config.internal.ConfigCustomizerChain",
                        "org.jdbi.v3.core.Handles",
                        "org.jdbi.v3.core.argument.Arguments",
                        "org.jdbi.v3.core.array.SqlArrayTypes",
                        "org.jdbi.v3.core.collector.JdbiCollectors",
                        "org.jdbi.v3.core.enums.Enums",
                        "org.jdbi.v3.core.extension.Extensions",
                        "org.jdbi.v3.core.internal.OnDemandExtensions",
                        "org.jdbi.v3.core.mapper.ColumnMappers",
                        "org.jdbi.v3.core.mapper.MapEntryMappers",
                        "org.jdbi.v3.core.mapper.MapMappers",
                        "org.jdbi.v3.core.mapper.Mappers",
                        "org.jdbi.v3.core.mapper.RowMappers",
                        "org.jdbi.v3.core.mapper.reflect.ReflectionMappers",
                        "org.jdbi.v3.core.qualifier.Qualifiers",
                        "org.jdbi.v3.core.statement.SqlStatements",
                        "org.jdbi.v3.core.transaction.SerializableTransactionRunner$Configuration",
                        "org.jdbi.v3.sqlobject.Handlers",
                        "org.jdbi.v3.sqlobject.HandlerDecorators",
                        "org.jdbi.v3.sqlobject.SqlObjects",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterBeanMapperImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterBeanMappersImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterColumnMapperImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterColumnMappersImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterConstructorMapperImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterConstructorMappersImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterFieldMapperImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterFieldMappersImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterRowMapperImpl",
                        "org.jdbi.v3.sqlobject.config.internal.RegisterRowMappersImpl",
                        "org.jdbi.v3.sqlobject.customizer.internal.AllowUnusedBindingsFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindBeanFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindBeanListFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindFieldsFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindListFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindMapFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindMethodsFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindMethodsListFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.BindPojoFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.DefineFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.DefineListFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.DefineNamedBindingsFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.FetchSizeFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.MaxRowsFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.OutParameterFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.OutParameterListFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.QueryTimeOutFactory",
                        "org.jdbi.v3.sqlobject.customizer.internal.TimestampedFactory",
                        "org.jdbi.v3.sqlobject.customizer.TimestampedConfig",
                        "org.jdbi.v3.sqlobject.statement.internal.MapToFactory",
                        "org.jdbi.v3.sqlobject.internal.CreateSqlObjectHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlBatchHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlCallHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlQueryHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlScriptsHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlUpdateHandler",
                        "org.jdbi.v3.sqlobject.statement.internal.SqlObjectStatementConfiguration",
                        "org.jdbi.v3.json.JsonConfig",
                        "org.jdbi.v3.jackson2.Jackson2Config",
                        "org.jdbi.v3.postgres.PostgresTypes",
                        "org.jdbi.v3.postgres.PostgresPlugin$VectorEnabler"
                )
                .constructors()
                .reason("Jdbi ConfigRegistry constructs config objects reflectively")
                .build();
    }

    @BuildStep
    GeneratedResourceBuildItem repositorySchemaResource(CombinedIndexBuildItem index) {
        String content = repositoryEntityBindings(index.getIndex()).stream()
                .map(binding -> binding.entityType() + "|" + binding.alignTable().name())
                .collect(Collectors.joining("\n"));
        if (!content.isEmpty()) {
            content = content + "\n";
        }
        return new GeneratedResourceBuildItem(
                MuYunRepositorySchemaInitializer.RESOURCE,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    @BuildStep
    NativeImageResourceBuildItem repositorySchemaNativeResource() {
        return new NativeImageResourceBuildItem(MuYunRepositorySchemaInitializer.RESOURCE);
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

    List<RepositoryEntityBinding> repositoryEntityBindings(IndexView index) {
        List<RepositoryEntityBinding> bindings = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(MUYUN_REPOSITORY)) {
            ClassInfo repository = annotation.target().asClass();
            if (!repository.isInterface()) {
                throw new IllegalStateException("@MuYunRepository must be used on an interface: " + repository.name());
            }
            repositoryEntityType(index, repository.name())
                    .map(entity -> new RepositoryEntityBinding(entity, alignTable(annotation)))
                    .ifPresent(bindings::add);
        }
        return bindings;
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

    private MuYunRepository.AlignTable alignTable(AnnotationInstance annotation) {
        AnnotationValue value = annotation.value("alignTable");
        if (value == null) {
            return MuYunRepository.AlignTable.DEFAULT;
        }
        return MuYunRepository.AlignTable.valueOf(value.asEnum());
    }

    record RepositoryEntityBinding(DotName entityType, MuYunRepository.AlignTable alignTable) {
    }
}
