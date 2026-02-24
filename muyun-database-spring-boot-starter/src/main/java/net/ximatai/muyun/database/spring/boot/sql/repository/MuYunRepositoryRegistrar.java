package net.ximatai.muyun.database.spring.boot.sql.repository;

import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;

public class MuYunRepositoryRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableMuYunRepositories.class.getName(), false)
        );
        if (attributes == null) {
            return;
        }

        Set<String> basePackages = resolveBasePackages(importingClassMetadata, attributes);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(MuYunRepository.class));

        Set<String> repositoryInterfaces = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                registerRepositoryFactoryBean(className, registry);
                if (StringUtils.hasText(className)) {
                    repositoryInterfaces.add(className);
                }
            }
        }
        registerRepositoryCatalog(importingClassMetadata.getClassName(), repositoryInterfaces, registry);
    }

    private void registerRepositoryFactoryBean(String className, BeanDefinitionRegistry registry) {
        if (!StringUtils.hasText(className)) {
            return;
        }
        String beanName = className;
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        RootBeanDefinition definition = new RootBeanDefinition(MuYunRepositoryFactoryBean.class);
        definition.getPropertyValues().add("daoInterfaceName", className);
        definition.setLazyInit(false);
        registry.registerBeanDefinition(beanName, definition);
    }

    private void registerRepositoryCatalog(String importingClassName,
                                           Set<String> repositoryInterfaces,
                                           BeanDefinitionRegistry registry) {
        if (repositoryInterfaces.isEmpty()) {
            return;
        }
        String beanName = MuYunRepositoryCatalog.class.getName() + "#" + importingClassName;
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        RootBeanDefinition definition = new RootBeanDefinition(MuYunRepositoryCatalog.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, repositoryInterfaces);
        definition.setLazyInit(false);
        registry.registerBeanDefinition(beanName, definition);
    }

    private Set<String> resolveBasePackages(AnnotationMetadata importingClassMetadata, AnnotationAttributes attributes) {
        Set<String> packages = new LinkedHashSet<>();

        String[] basePackages = attributes.getStringArray("basePackages");
        for (String basePackage : basePackages) {
            if (StringUtils.hasText(basePackage)) {
                packages.add(basePackage.trim());
            }
        }

        Class<?>[] packageClasses = attributes.getClassArray("basePackageClasses");
        for (Class<?> packageClass : packageClasses) {
            packages.add(ClassUtils.getPackageName(packageClass));
        }

        if (packages.isEmpty()) {
            packages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        return packages;
    }
}
