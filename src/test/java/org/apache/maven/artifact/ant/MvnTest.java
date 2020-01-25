package org.apache.maven.artifact.ant;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MvnTest
{

    private static final String ROOT_RESOURCES_PATH = "target/test-classes/mvnTest/";
    private static final String POM_PATH = ROOT_RESOURCES_PATH + "external-dependencies.xml";
    private static final String OUTPUT_PATH = "output/libs"; // needs to be relative to the external-dependencies.xml file

    private static final String MVN_VERSION = "3.2.5";

    private Mvn task = new Mvn();

    private File outputDir;

    @Before
    public void setUp()
    {
        File pom = new File(POM_PATH);
        task.setPom(pom);

        task.setFork(true);
        task.setFailonerror(true);
        task.setMavenVersion(MVN_VERSION);
        task.setArgs("dependency:copy-dependencies -DoutputDirectory=" + OUTPUT_PATH
            + " -DoverWriteReleases=true -DoverWriteSnapshots=true -DoverWriteIfNewer=true -DexcludeTransitive=true"
            + " -DremoteRepositories=swa::default::https://repo1.maven.org/maven2,swa_http::default::http://repo1.maven.org/maven2"
        );

        Project project = new Project();
        project.setName("mvnTest");
        task.setProject(project);

        outputDir = new File(ROOT_RESOURCES_PATH + OUTPUT_PATH);
        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
    }

    @Test
    public void testExecuteTask()
    {
        task.execute();

        assertTrue(outputDir.exists());

        String[] outputContent = outputDir.list();
        assertNotNull(outputContent);
        assertEquals(1, outputContent.length);
        assertEquals("commons-math3-3.6.1.jar", outputContent[0]);
    }

    @After
    public void tearDown()
    {
        FileUtils.deleteQuietly(outputDir);
    }
}