package br.com.investlog.server.config.core

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import kotlin.test.Test
import kotlin.test.assertTrue

class SchedulingConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration::class.java))
        .withUserConfiguration(SchedulingConfiguration::class.java)

    @Test
    fun `the scheduler runs on more than one thread so one stuck job cannot starve the others`() {
        contextRunner.run { context ->
            val scheduler = context.getBean(ThreadPoolTaskScheduler::class.java)
            val corePoolSize = scheduler.scheduledThreadPoolExecutor.corePoolSize

            assertTrue(corePoolSize > 1, "the scheduler pool has $corePoolSize thread(s)")
        }
    }
}
