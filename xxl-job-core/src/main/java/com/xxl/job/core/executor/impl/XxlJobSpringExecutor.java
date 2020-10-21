package com.xxl.job.core.executor.impl;

import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.annotation.JobFlag;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;


/**
 * executor (for spring)
 *
 * @author xuxueli 2018-11-01 09:24:52
 */
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSpringExecutor.class);


    // start
    @Override
    public void afterSingletonsInstantiated() {

        // init JobHandler Repository
        /*initJobHandlerRepository(applicationContext);*/

        // init JobHandler Repository (for method)
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        GlueFactory.refreshInstance(1);

        // super start
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // destroy
    @Override
    public void destroy() {
        super.destroy();
    }


    /*private void initJobHandlerRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler) {
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("jobhandler[" + name + "] naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
    }*/

    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        // init job handler from method
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {
            Object bean = applicationContext.getBean(beanDefinitionName);

            Map<Method, JobFlag> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
            try {
                annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<JobFlag>() {
                            @Override
                            public JobFlag inspect(Method method) {
                                return AnnotatedElementUtils.findMergedAnnotation(method, JobFlag.class);
                            }
                        });
            } catch (Throwable ex) {
                logger.error("method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
            }
            if (annotatedMethods==null || annotatedMethods.isEmpty()) {
                continue;
            }

            for (Map.Entry<Method, JobFlag> methodXxlJobEntry : annotatedMethods.entrySet()) {
                Method method = methodXxlJobEntry.getKey();
                JobFlag jobFlag = methodXxlJobEntry.getValue();
                if (jobFlag == null) {
                    continue;
                }

                String name = jobFlag.value();
                if (name.trim().length() == 0) {
                    throw new RuntimeException("method-jobhandler name invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                }
                if (loadJobHandler(name) != null) {
                    throw new RuntimeException("jobhandler[" + name + "] naming conflicts.");
                }

                // execute method
                if (!(method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(String.class))) {
                    throw new RuntimeException("method-jobhandler param-classtype invalid, for[" + bean.getClass() + "#" + method.getName() + "] , " +
                            "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                }

                method.setAccessible(true);

                // init and destory
                Method initMethod = null;
                Method destroyMethod = null;

                if (jobFlag.init().trim().length() > 0) {
                    try {
                        initMethod = bean.getClass().getDeclaredMethod(jobFlag.init());
                        initMethod.setAccessible(true);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("method-jobhandler initMethod invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                    }
                }
                if (jobFlag.destroy().trim().length() > 0) {
                    try {
                        destroyMethod = bean.getClass().getDeclaredMethod(jobFlag.destroy());
                        destroyMethod.setAccessible(true);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("method-jobhandler destroyMethod invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                    }
                }

                // registry jobhandler
                registJobHandler(name, new MethodJobHandler(bean, method, initMethod, destroyMethod));
            }
        }

    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
