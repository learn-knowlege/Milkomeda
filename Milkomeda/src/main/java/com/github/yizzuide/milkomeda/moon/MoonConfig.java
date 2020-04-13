package com.github.yizzuide.milkomeda.moon;

import com.github.yizzuide.milkomeda.universe.context.WebContext;
import com.github.yizzuide.milkomeda.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * MoonConfig
 *
 * @author yizzuide
 * @since 3.0.0
 * @version 3.0.2
 * Create at 2020/03/28 17:40
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MoonProperties.class)
public class MoonConfig implements ApplicationContextAware {

    @Autowired
    private MoonProperties moonProperties;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        List<MoonProperties.Instance> instances = moonProperties.getInstances();
        if (CollectionUtils.isEmpty(instances)) {
            return;
        }
        for (MoonProperties.Instance instance : instances) {
            if (CollectionUtils.isEmpty(instance.getPhases())) continue;
            String beanName = instance.getName();
            String cacheName = instance.getCacheName();
            Class<MoonStrategy> moonStrategyClazz = instance.getMoonStrategyClazz();
            Moon moon = WebContext.registerBean((ConfigurableApplicationContext) applicationContext, beanName, Moon.class);
            moon.setCacheName(cacheName);
            if (moonStrategyClazz != null) {
                try {
                    MoonStrategy moonStrategy = moonStrategyClazz.newInstance();
                    moon.setMoonStrategy(moonStrategy);
                    if (!CollectionUtils.isEmpty(instance.getProps())) {
                        ReflectUtil.setField(moonStrategy, instance.getProps());
                    }
                } catch (Exception e) {
                    log.error("Moon invoke error with msg: {}", e.getMessage(), e);
                }
            }
            if (CollectionUtils.isEmpty(instance.getPhases())) {
                return;
            }
            moon.add(instance.getPhases().toArray(new Object[0]));
        }
    }
}
