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

import org.apache.maven.model.Dependency;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Environment.Variable;

/**
 * Ant task to execute a maven build.
 * 
 * @author pgier
 *
 */
public class Mvn
    extends Java
{

    public final String BATCH_MODE = "-B";
    
    private File pom;
    
    private File mavenHome;
    
    private final String DEFAULT_MAVEN_VERSION = "2.0.10";
    
    private String mavenVersion = DEFAULT_MAVEN_VERSION;
    
    private boolean batchMode = true;
    
    private LocalRepository localRepository;
    
    public void execute()
        throws BuildException
    {
        if ( batchMode )
        {
            this.createArg().setValue( BATCH_MODE );
        }
        
        if ( pom != null )
        {
            createArg().setValue( "-f" + pom.getAbsolutePath() );
        }
        
        if ( localRepository != null )
        {
            this.createJvmarg().setValue( "-Dmaven.repo.local=" + localRepository.getPath().getAbsolutePath() );
        }
        
        if ( mavenHome == null )
        {
            downloadAndConfigureMaven();
        }
        else
        {
            setupLocalMaven();
        }
        
        super.execute();
    }
    
    private void downloadAndConfigureMaven()
    {
        Dependency mavenCore = new Dependency();
        mavenCore.setGroupId( "org.apache.maven" );
        mavenCore.setArtifactId( "maven-core" );
        mavenCore.setVersion( getMavenVersion() );
        
        DependenciesTask depsTask = new DependenciesTask();
        depsTask.addLocalRepository( getLocalRepository() );
        depsTask.setProject( getProject() );
        depsTask.setPathId( "maven-core-dependencies" );
        depsTask.addDependency( mavenCore );
        
        depsTask.execute();
        
        this.setClasspath( (Path) getProject().getReference( "maven-core-dependencies" ) );
        
        this.setClassname( "org.apache.maven.cli.MavenCli" );
    }
    
    private void setupLocalMaven()
    {
        // Set the required properties
        Variable classworldsConfProp = new Variable();
        classworldsConfProp.setKey( "classworlds.conf" );
        File classworldsPath = new File( mavenHome, "bin/m2.conf" );
        classworldsConfProp.setValue( classworldsPath.getAbsolutePath() );
        this.addSysproperty( classworldsConfProp );
        
        Variable mavenHomeProp = new Variable();
        mavenHomeProp.setKey( "maven.home" );
        mavenHomeProp.setValue( mavenHome.getAbsolutePath() );
        this.addSysproperty( mavenHomeProp );
        
        // Set the boot classpath
        FileSet bootDir = new FileSet();
        bootDir.setDir( new File ( mavenHome, "boot" ) );
        bootDir.setIncludes( "*.jar" );
        
        Path mavenClasspath = new Path( getProject() );
        mavenClasspath.addFileset( bootDir );
        
        this.setClasspath( mavenClasspath );
        
        this.setClassname( "org.codehaus.classworlds.Launcher" );
    }

    public void setPom( File pom )
    {
        this.pom = pom;
    }

    public File getPom()
    {
        return pom;
    }

    public void setMavenHome( File mavenHome )
    {
        this.mavenHome = mavenHome;
    }

    public File getMavenHome()
    {
        return mavenHome;
    }

    public void setBatchMode( boolean batchMode )
    {
        this.batchMode = batchMode;
    }

    public boolean isBatchMode()
    {
        return batchMode;
    }

    public void addLocalRepository( LocalRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    public LocalRepository getLocalRepository()
    {
        return localRepository;
    }

    public void setMavenVersion( String mavenVersion )
    {
        this.mavenVersion = mavenVersion;
    }

    public String getMavenVersion()
    {
        return mavenVersion;
    }

}
