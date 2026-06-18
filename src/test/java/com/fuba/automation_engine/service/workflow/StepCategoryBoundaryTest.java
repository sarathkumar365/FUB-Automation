package com.fuba.automation_engine.service.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * Executable form of RD-010 / RD-007's step boundary: CONTROL and UTILITY step
 * types are kernel code and must not depend on host/business packages. Scans the
 * classpath so new step types are covered automatically.
 */
class StepCategoryBoundaryTest {

    private static final String APP_ROOT = "com.fuba.automation_engine";
    private static final String KERNEL_PACKAGE = "com.fuba.automation_engine.service.workflow";

    @Test
    void controlAndUtilityStepsDependOnlyOnKernelPackages() {
        List<String> violations = new ArrayList<>();

        for (Class<?> stepClass : findStepTypeClasses()) {
            WorkflowStepType step = instantiate(stepClass);
            StepCategory category = step.category();
            assertThat(category)
                    .as("Step type %s must declare a category (RD-010)", stepClass.getSimpleName())
                    .isNotNull();

            if (category == StepCategory.BUSINESS) {
                continue;
            }
            for (Field field : stepClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String dependencyPackage = field.getType().getPackageName();
                if (dependencyPackage.startsWith(APP_ROOT)
                        && !dependencyPackage.startsWith(KERNEL_PACKAGE)) {
                    violations.add(stepClass.getSimpleName() + "." + field.getName()
                            + " -> " + field.getType().getName());
                }
            }
        }

        assertThat(violations)
                .as("CONTROL/UTILITY steps must not depend on host packages (RD-010); "
                        + "either move the dependency into the kernel or reclassify the step as BUSINESS")
                .isEmpty();
    }

    private List<Class<?>> findStepTypeClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(WorkflowStepType.class));
        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents(APP_ROOT)) {
            try {
                classes.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        assertThat(classes).as("classpath scan should find the step types").isNotEmpty();
        return classes;
    }

    /**
     * Steps are constructor-injected POJOs; null collaborators are fine because
     * category() returns a constant and nothing else is invoked.
     */
    private WorkflowStepType instantiate(Class<?> stepClass) {
        Constructor<?> ctor = Arrays.stream(stepClass.getDeclaredConstructors())
                .min((a, b) -> Integer.compare(a.getParameterCount(), b.getParameterCount()))
                .orElseThrow();
        ctor.setAccessible(true);
        Object[] args = new Object[ctor.getParameterCount()];
        try {
            return (WorkflowStepType) ctor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Could not instantiate " + stepClass.getName()
                            + " with null collaborators for the category boundary check; "
                            + "if its constructor now validates arguments, adjust this test",
                    e);
        }
    }
}
