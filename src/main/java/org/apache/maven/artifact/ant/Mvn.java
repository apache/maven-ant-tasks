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
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
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
            Pattern oldMaven = Pattern.compile("(2\\.0)|(2\\.0-.*)|(2\\.0\\.[1-9])");
            if ( oldMaven.matcher( getMavenVersion() ).matches() )
            {
                throw new BuildException( "The requested Maven version '" + getMavenVersion() + "' is prior to " +
                                          "version '2.0.10'. In order to launch the requested version you need to " +
                                          "use a local Maven installation and point to that installation with the " +
                                          "mavenHome attribute." );
            }
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
        Dependency apacheMaven = new Dependency();
        apacheMaven.setGroupId( "org.apache.maven" );
        apacheMaven.setArtifactId( "apache-maven" );
        apacheMaven.setVersion( getMavenVersion() );
        apacheMaven.setType( "pom" );
        
        DependenciesTask depsTask = new DependenciesTask();
        depsTask.addLocalRepository( getLocalRepository() );
        depsTask.setProject( getProject() );
        depsTask.setPathId( "apache-maven-dependencies" );
        depsTask.addDependency( apacheMaven );
        depsTask.setType( "pom,jar" );
        depsTask.setPathType( "jar" );

        addRemoteRepositoriesFromCommandLine(depsTask);

        depsTask.execute();
        
        this.setClasspath( (Path) getProject().getReference( "apache-maven-dependencies" ) );
        
        this.setClassname( "org.apache.maven.cli.MavenCli" );
    }

    private void addRemoteRepositoriesFromCommandLine(DependenciesTask depsTask)
    {
        String remoteReposArgValue = getRemoteRepositoryArgValue();
        if (remoteReposArgValue == null || remoteReposArgValue.length() == 0)
        {
            return;
        }

        log("Found remote repositories (-DremoteRepositories) argument: " + remoteReposArgValue, Project.MSG_VERBOSE);

        String[] remoteRepos = remoteReposArgValue.split(",");
        for (String remoteRepoCfg : remoteRepos)
        {
            RemoteRepository repoSettings = extractRepoSettings(remoteRepoCfg);
            if (repoSettings != null)
            {
                depsTask.addConfiguredRemoteRepository(repoSettings);
            }
        }
    }

    private String getRemoteRepositoryArgValue()
    {
        String[] args = getCommandLine().getJavaCommand().getArguments();
        log("Used args: " + Arrays.toString(args), Project.MSG_VERBOSE);

        String remoteReposArgPrefix = "-DremoteRepositories=";

        String remoteReposArgValue = null;

        for (String arg: args)
        {
            if (arg.startsWith(remoteReposArgPrefix))
            {
                remoteReposArgValue = arg.substring(remoteReposArgPrefix.length());
                break;
            }
        }

        return remoteReposArgValue;
    }

    private RemoteRepository extractRepoSettings(String remoteRepoCfg)
    {
        String[] repoSettings = remoteRepoCfg.split("::");

        RemoteRepository repository = null;

        if (repoSettings.length == 1) // only the URL is set
        {
            repository = new RemoteRepository();
            repository.setUrl(repoSettings[0]);
        }
        else if (repoSettings.length == 3) // full format id::layout::url is set
        {
            repository = new RemoteRepository();
            repository.setId(repoSettings[0]);
            if (repoSettings[1].length() > 0) // layout is specified
            {
                repository.setLayout(repoSettings[1]);
            }
            repository.setUrl(repoSettings[2]);
        }

        return repository;
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
