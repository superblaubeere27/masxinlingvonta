package net.superblaubeere27.masxinlingvonta.test.framework;

/**
 * Test Milestone - equivalent to a dead-man-switch. The code compilation might fail completely and the tested code might
 * not even be generated. To prevent this, a test can specify milestones which have to be reached.
 */
public @interface TestMilestone {
    String value();

    /**
     * @return The number of times the milestone has to be reached
     */
    int min() default 1;

    /**
     * @return The maximum number of times the milestone may be reached.
     */
    int max() default 1;
}
