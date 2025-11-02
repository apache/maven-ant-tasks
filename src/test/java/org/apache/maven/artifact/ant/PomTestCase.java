package org.apache.maven.artifact.ant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class PomTestCase
{

    @Test
    public void testDefaultRepositoryId()
    {
        RemoteRepository repo = new RemoteRepository();
        repo.setUrl( "file:///home/test/stuff" );

        Pom task = new Pom();
        String defaultId = task.generateDefaultRepositoryId( repo );
        if ( defaultId.equals( repo.getUrl() ) )
        {
            fail( "MD5 digest not calculated" );
        }
    }
}
