package net.ximatai.muyun.database.spring.boot.sql.repository;

import net.ximatai.muyun.database.spring.boot.sql.MuYunRepositoryFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

public class MuYunRepositoryFactoryBean<T> implements FactoryBean<T>, InitializingBean, BeanClassLoaderAware, ApplicationContextAware {

    private String daoInterfaceName;
    private Class<T> daoType;
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;

    public void setDaoInterfaceName(String daoInterfaceName) {
        this.daoInterfaceName = daoInterfaceName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(daoInterfaceName, "daoInterfaceName must not be blank");
        @SuppressWarnings("unchecked")
        Class<T> resolved = (Class<T>) Class.forName(daoInterfaceName, false, classLoader);
        if (!resolved.isInterface()) {
            throw new IllegalStateException("MuYun repository must be an interface: " + daoInterfaceName);
        }
        this.daoType = resolved;
    }

    @Override
    public T getObject() {
        MuYunRepositoryFactory factory = applicationContext.getBean(MuYunRepositoryFactory.class);
        return factory.create(daoType);
    }

    @Override
    public Class<?> getObjectType() {
        return daoType;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
