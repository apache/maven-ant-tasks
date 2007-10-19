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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.resolver.filter.TypeArtifactFilter;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileList;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Dependencies task, using maven-artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class DependenciesTask
    extends AbstractArtifactWithRepositoryTask
{
    private List dependencies = new ArrayList();

    private String pathId;

    private String filesetId;

    private String sourcesFilesetId;
    
    private String versionsId;

    private String useScope;

    private String type;

    private boolean verbose;

    protected void doExecute()
    {
        showVersion();
        
        ArtifactRepository localRepo = createLocalArtifactRepository();
        log( "Using local repository: " + localRepo.getBasedir(), Project.MSG_VERBOSE );

        ArtifactResolver resolver = (ArtifactResolver) lookup( ArtifactResolver.ROLE );
        ArtifactFactory artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        MavenMetadataSource metadataSource = (MavenMetadataSource) lookup( ArtifactMetadataSource.ROLE );

        List dependencies = this.dependencies;

        Pom pom = buildPom( localRepo );
        if ( pom != null )
        {
            if ( !dependencies.isEmpty() )
            {
                throw new BuildException( "You cannot specify both dependencies and a pom in the dependencies task" );
            }

            dependencies = pom.getDependencies();

            for ( Iterator i = pom.getRepositories().iterator(); i.hasNext(); )
            {
                Repository pomRepository = (Repository) i.next();

                addRemoteRepository( createAntRemoteRepository( pomRepository ) );
            }
        }
        else
        {
            // we have to have some sort of Pom object in order to satisfy the requirements for building the
            // originating Artifact below...
            pom = createDummyPom();
        }

        if ( dependencies.isEmpty() )
        {
            log( "There were no dependencies specified", Project.MSG_WARN );
        }

        log( "Resolving dependencies...", Project.MSG_VERBOSE );

        WagonManager wagonManager = (WagonManager) lookup( WagonManager.ROLE );
        wagonManager.setDownloadMonitor( new AntDownloadMonitor() );

        ArtifactResolutionResult result;
        Set artifacts;

        List remoteArtifactRepositories = createRemoteArtifactRepositories();

        try
        {
            artifacts = MavenMetadataSource.createArtifacts( artifactFactory, dependencies, null, null, null );

            Artifact pomArtifact = artifactFactory.createBuildArtifact( pom.getGroupId(), pom.getArtifactId(), pom
                .getVersion(), pom.getPackaging() );

            List listeners = Collections.singletonList( new AntResolutionListener( getProject(), verbose ) );

            // TODO: managed dependencies
            Map managedDependencies = Collections.EMPTY_MAP;

            ArtifactFilter filter = null;
            if ( useScope != null )
            {
                filter = new ScopeArtifactFilter( useScope );
            }
            if ( type != null )
            {
                TypeArtifactFilter typeArtifactFilter = new TypeArtifactFilter( type );
                if ( filter != null )
                {
                    AndArtifactFilter andFilter = new AndArtifactFilter();
                    andFilter.add( filter );
                    andFilter.add( typeArtifactFilter );
                    filter = andFilter;
                }
                else
                {
                    filter = typeArtifactFilter;
                }
            }

            result = resolver.resolveTransitively( artifacts, pomArtifact, managedDependencies, localRepo,
                                                   remoteArtifactRepositories, metadataSource, filter, listeners );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new BuildException( "Unable to resolve artifact: " + e.getMessage(), e );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new BuildException( "Dependency not found: " + e.getMessage(), e );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new BuildException( e.getMessage(), e );
        }

        /*
        MANTTASKS-37: Do what other ant tasks do and just override the path id.
        if ( pathId != null && getProject().getReference( pathId ) != null )
        {
            throw new BuildException( "Reference ID " + pathId + " already exists" );
        }

        if ( filesetId != null && getProject().getReference( filesetId ) != null )
        {
            throw new BuildException( "Reference ID " + filesetId + " already exists" );
        }

        if ( sourcesFilesetId != null && getProject().getReference( sourcesFilesetId ) != null )
        {
            throw new BuildException( "Reference ID " + sourcesFilesetId + " already exists" );
        }
        */

        FileList fileList = new FileList();
        fileList.setDir( getLocalRepository().getPath() );

        FileSet fileSet = new FileSet();
        fileSet.setProject( getProject() );
        fileSet.setDir( fileList.getDir( getProject() ) );

        FileList sourcesFileList = new FileList();
        sourcesFileList.setDir( getLocalRepository().getPath() );

        FileSet sourcesFileSet = new FileSet();
        sourcesFileSet.setDir( sourcesFileList.getDir( getProject() ) );

        Set versions = new HashSet();
        
        if ( result.getArtifacts().isEmpty() )
        {
            fileSet.createExclude().setName( "**/**" );
        }
        else
        {
            for ( Iterator i = result.getArtifacts().iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                artifact.isSnapshot(); // MNG-2961: DefaultArtifact getBaseVersion is changed to "xxxx-SNAPSHOT" only if you first call isSnapshot()
                String filename = localRepo.pathOf( artifact );

                FileList.FileName file = new FileList.FileName();
                file.setName( filename );

                fileList.addConfiguredFile( file );

                fileSet.createInclude().setName( filename );
                
                versions.add( artifact.getVersion() );

                if ( sourcesFilesetId != null )
                {
                    log( "Resolving dependencies sources...", Project.MSG_VERBOSE );
                    // get sources
                    Artifact sourcesArtifact =
                        artifactFactory.createArtifactWithClassifier( artifact.getGroupId(), artifact.getArtifactId(),
                                                                      artifact.getVersion(), "java-source", "sources" );
                    if ( sourcesArtifact != null )
                    {
                        try
                        {
                            resolver.resolve( sourcesArtifact, remoteArtifactRepositories, localRepo );
                            String sourcesFilename = localRepo.pathOf( sourcesArtifact );

                            FileList.FileName sourcesFile = new FileList.FileName();
                            sourcesFile.setName( sourcesFilename );

                            sourcesFileList.addConfiguredFile( sourcesFile );

                            sourcesFileSet.createInclude().setName( sourcesFilename );
                        }
                        catch ( ArtifactResolutionException e )
                        {
                            throw new BuildException( "Unable to resolve artifact: " + e.getMessage(), e );
                        }
                        catch ( ArtifactNotFoundException e )
                        {
                            // no sources available: no problem
                        }
                    }
                }
            }
        }

        if ( pathId != null )
        {
            Path path = new Path( getProject() );
            path.addFilelist( fileList );
            getProject().addReference( pathId, path );
        }

        if ( filesetId != null )
        {
            getProject().addReference( filesetId, fileSet );
        }

        if ( sourcesFilesetId != null )
        {
            getProject().addReference( sourcesFilesetId, sourcesFileSet );
        }
        
        if ( versionsId != null )
        {
            String versionsValue = StringUtils.join( versions.iterator(), File.pathSeparator );
            getProject().setNewProperty( versionsId, versionsValue );
        }
    }

    public List getDependencies()
    {
        return dependencies;
    }

    public void addDependency( Dependency dependency )
    {
        dependencies.add( dependency );
    }

    public String getPathId()
    {
        return pathId;
    }

    public void setPathId( String pathId )
    {
        this.pathId = pathId;
    }

    public String getFilesetId()
    {
        return filesetId;
    }

    public void setSourcesFilesetId( String filesetId )
    {
        this.sourcesFilesetId = filesetId;
    }

    public String getSourcesFilesetId()
    {
        return sourcesFilesetId;
    }

    public void setFilesetId( String filesetId )
    {
        this.filesetId = filesetId;
    }

    public String getVersionsId()
    {
        return versionsId;
    }

    public void setVersionsId( String versionsId )
    {
        this.versionsId = versionsId;
    }

    public void setVerbose( boolean verbose )
    {
        this.verbose = verbose;
    }

    public void setUseScope( String useScope )
    {
        this.useScope = useScope;
    }

    public void setType( String type )
    {
        this.type = type;
    }

    private void showVersion()
    {
        InputStream resourceAsStream;
        try
        {
            Properties properties = new Properties();
            resourceAsStream = DependenciesTask.class.getClassLoader().getResourceAsStream(
                "META-INF/maven/org.apache.maven/maven-ant-tasks/pom.properties" );
            properties.load( resourceAsStream );

            if ( properties.getProperty( "builtOn" ) != null )
            {
                log( "Maven Ant Tasks version: " + properties.getProperty( "version", "unknown" ) + " built on "
                                + properties.getProperty( "builtOn" ), Project.MSG_VERBOSE );
            }
            else
            {
                log( "Maven Ant Tasks version: " + properties.getProperty( "version", "unknown" ), Project.MSG_VERBOSE );
            }
        }
        catch ( IOException e )
        {
            log( "Unable determine version from Maven Ant Tasks JAR file: " + e.getMessage(), Project.MSG_WARN );
        }
    }
}
