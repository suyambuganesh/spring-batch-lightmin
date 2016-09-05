package org.tuxdevelop.spring.batch.lightmin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StringUtils;
import org.tuxdevelop.spring.batch.lightmin.admin.domain.*;
import org.tuxdevelop.spring.batch.lightmin.admin.listener.FolderListener;
import org.tuxdevelop.spring.batch.lightmin.admin.listener.Listener;
import org.tuxdevelop.spring.batch.lightmin.exception.SpringBatchLightminApplicationException;
import org.tuxdevelop.spring.batch.lightmin.exception.SpringBatchLightminConfigurationException;
import org.tuxdevelop.spring.batch.lightmin.util.BeanRegistrar;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Marcel Becker
 * @since 0.3
 */
@Slf4j
public class DefaultListenerService implements ListenerService, InitializingBean {

    private ApplicationContext applicationContext;

    private final BeanRegistrar beanRegistrar;
    private final JobRegistry jobRegistry;
    private final JobRepository jobRepository;

    @Autowired
    public void setApplicationContext(final ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public DefaultListenerService(final BeanRegistrar beanRegistrar, final JobRegistry jobRegistry, final JobRepository jobRepository) {
        this.beanRegistrar = beanRegistrar;
        this.jobRegistry = jobRegistry;
        this.jobRepository = jobRepository;
    }

    @Override
    public String registerListenerForJob(final JobConfiguration jobConfiguration) {
        final JobListenerType jobListenerType = jobConfiguration.getJobListenerConfiguration().getJobListenerType();
        final String beanName;
        switch (jobListenerType) {
            case LOCAL_FOLDER_LISTENER:
                beanName = registerFolderListener(jobConfiguration);
                break;
            default:
                throw new SpringBatchLightminApplicationException("Unknown ListenerType: " + jobListenerType);
        }
        return beanName;
    }

    @Override
    public void unregisterListenerForJob(final String beanName) {
        beanRegistrar.unregisterBean(beanName);
    }

    @Override
    public void refreshListenerForJob(final JobConfiguration jobConfiguration) {
        unregisterListenerForJob(jobConfiguration.getJobListenerConfiguration().getBeanName());
        registerListenerForJob(jobConfiguration);
    }

    @Override
    public void activateListener(final String beanName, final Boolean forceActivation) {
        if (applicationContext.containsBean(beanName)) {
            final Listener listener = applicationContext.getBean(beanName, Listener.class);
            if (ListenerStatus.ACTIVE.equals(listener.getListenerStatus()) && Boolean.FALSE.equals(forceActivation)) {
                log.info("Listener: {} already running", beanName);
            } else {
                listener.start();
            }
        }
    }

    @Override
    public void terminateListener(final String beanName) {
        if (applicationContext.containsBean(beanName)) {
            final Listener listener = applicationContext.getBean(beanName, Listener.class);
            listener.stop();
        } else {
            throw new SpringBatchLightminConfigurationException("Could not terminate bean with name: " + beanName);
        }
    }

    private String registerFolderListener(final JobConfiguration jobConfiguration) {
        final String beanName;
        try {
            final ListenerConstructorWrapper listenerConstructorWrapper = new ListenerConstructorWrapper();
            final JobListenerConfiguration jobListenerConfiguration = jobConfiguration.getJobListenerConfiguration();
            final JobLauncher jobLauncher = ServiceUtil.createJobLauncher(jobListenerConfiguration.getTaskExecutorType(), jobRepository);
            final JobParameters jobParameters = ServiceUtil.mapToJobParameters(jobConfiguration.getJobParameters());
            final Job job = jobRegistry.getJob(jobConfiguration.getJobName());
            listenerConstructorWrapper.setJob(job);
            listenerConstructorWrapper.setJobParameters(jobParameters);
            listenerConstructorWrapper.setJobLauncher(jobLauncher);
            listenerConstructorWrapper.setJobConfiguration(jobConfiguration);
            listenerConstructorWrapper.setJobIncrementer(jobConfiguration.getJobIncrementer());
            if (!StringUtils.hasText(jobListenerConfiguration.getBeanName())) {
                beanName = generateSchedulerBeanName(jobConfiguration.getJobName(),
                        jobConfiguration.getJobConfigurationId(), jobListenerConfiguration.getJobListenerType());
            } else {
                beanName = jobListenerConfiguration.getBeanName();
            }
            final Set<Object> constructorValues = new HashSet<>();
            constructorValues.add(listenerConstructorWrapper);
            beanRegistrar.registerBean(FolderListener.class, beanName, constructorValues, null, null, null, null);
            final FolderListener folderListener = applicationContext.getBean(beanName, FolderListener.class);
        } catch (final Exception e) {
            throw new SpringBatchLightminConfigurationException(e, e.getMessage());
        }
        return beanName;
    }

    private String generateSchedulerBeanName(final String jobName,
                                             final Long id,
                                             final JobListenerType listenerType) {
        return jobName + listenerType.name() + id;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        assert beanRegistrar != null : "BeanRegistrar must not be null";
    }
}