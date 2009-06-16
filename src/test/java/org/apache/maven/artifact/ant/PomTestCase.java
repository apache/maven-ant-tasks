package org.apache.maven.artifact.ant;

import junit.framework.TestCase;

public class PomTestCase
    extends TestCase
{

    public void testDefaultRepositoryId()
    {
        RemoteRepository repo = new RemoteRepository();
        repo.setUrl( "file:///home/test/stuff" );
        
        Pom task = new Pom();
        String defaultId = task.generateDefaultRepositoryId( repo );
        if ( defaultId.equals( repo.getUrl() ) )
        {
            this.fail( "MD5 digest not calculated" );
        }
    }
}
