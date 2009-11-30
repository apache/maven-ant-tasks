package org.apache.maven.artifact.ant;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

/**
 * Write a POM to a file.
 * 
 * @since 2.1.0
 */
public class WritePomTask
    extends Task
{
    private String pomRefId;

    private File file;
    
    private boolean trim = true;

    public void execute()
    {
        // check valid configuration
        Pom pom = (Pom) getProject().getReference( pomRefId );
        Model model = pom.getModel();
        if ( trim )
        {
            trimModel ( model );            
        }
        writeModel ( model, file );
    }
    
    /**
     * Removes a lot of unnecessary information from the POM.
     * This includes the build section, reporting, repositories, etc.
     */
    public void trimModel( Model model )
    {
        model.setBuild( null );
        model.setReporting( null );
        model.setProperties( null );
        model.setRepositories( null );
        model.setPluginRepositories( null );
        model.setProfiles( null );
        model.setDistributionManagement( null );
        model.setModules( null );
    }

    /**
     * Write a POM model to a file
     * 
     * @param model
     * @return
     * @throws MojoExecutionException
     */
    public void writeModel( Model model, File outputFile )
        throws BuildException
    {
        Writer fw = null;
        try
        {
            fw = WriterFactory.newXmlWriter( outputFile );
            new MavenXpp3Writer().write( fw, model );

        }
        catch ( IOException e )
        {
            throw new BuildException( "Error writing temporary pom file: " + e.getMessage(), e );
        }
        finally
        {
            IOUtil.close( fw );
        }
    }

    public void setPomRefId( String pomRefId )
    {
        this.pomRefId = pomRefId;
    }

    public String getPomRefId()
    {
        return pomRefId;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    public File getFile()
    {
        return file;
    }

    public void setTrim( boolean trim )
    {
        this.trim = trim;
    }

    public boolean isTrim()
    {
        return trim;
    }
}
