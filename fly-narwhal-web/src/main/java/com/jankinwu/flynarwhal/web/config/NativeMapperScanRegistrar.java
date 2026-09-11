package com.jankinwu.flynarwhal.web.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

/**
 * Replacement for mybatis-spring's @MapperScan registrar under GraalVM native-image
 * (mybatis/spring#929): the stock scanner leaves MapperFactoryBean definitions on by-type /
 * constructor autowiring with a raw Class&lt;?&gt; "mapperInterface" value, and at image runtime
 * Spring then fails to resolve java.lang.Class&lt;?&gt; as an autowire candidate for every mapper.
 * This registrar scans the same packages and registers each mapper with fully explicit wiring —
 * indexed Class constructor argument, no injection fallback anywhere.
 */
public class NativeMapperScanRegistrar implements ImportBeanDefinitionRegistrar {

    static final String MAPPER_PACKAGE = "com.jankinwu.flynarwhal.web.mapper";

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(Mapper.class));
        Set<org.springframework.beans.factory.config.BeanDefinition> candidates = scanner.findCandidateComponents(MAPPER_PACKAGE);
        for (org.springframework.beans.factory.config.BeanDefinition candidate : candidates) {
            String className = ((AbstractBeanDefinition) candidate).getBeanClassName();
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            String beanName = unmcapitalize(simpleName.charAt(0) + simpleName.substring(1));
            RootBeanDefinition definition = new RootBeanDefinition(MapperFactoryBean.class);
            definition.setTargetType(ResolvableType.forClass(MapperFactoryBean.class));
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, className);
            definition.getPropertyValues().add("addToConfig", true);
            // AUTOWIRE_NO means nothing is resolved by type at image runtime, so the session
            // factory must be wired explicitly — the same reference the stock scanner resolves.
            definition.getPropertyValues().add("sqlSessionFactory", new RuntimeBeanReference("sqlSessionFactory"));
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_NO);
            definition.setLazyInit(false);
            registry.registerBeanDefinition(beanName, definition);
        }
    }

    private static String unmcapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        if (Character.isUpperCase(name.charAt(0))
                && name.length() > 1
                && Character.isUpperCase(name.charAt(1))) {
            return name; // acronym-style names keep their form (matches Spring's convention)
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
