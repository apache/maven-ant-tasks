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
import org.apache.maven.artifact.ant.util.AntBuildWriter;
import org.apache.maven.artifact.ant.util.AntTaskModified;
import org.apache.maven.artifact.ant.util.AntUtil;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.model.Dependency;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependencies task, using maven-artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:hboutemy@apache.org">Herve Boutemy</a>
 * @version $Id$
 */
public class DependenciesTask
    extends AbstractArtifactWithRepositoryTask
{
    
    public static final String DEFAULT_ANT_BUILD_FILE = "target/build-dependencies.xml";
    
    private List dependencies = new ArrayList();

    /**
     * The id of the path object containing a list of all dependencies.
     */
    private String pathId;

    /**
     * The id of the fileset object containing a list of all dependencies.
     */
    private String filesetId;

    /**
     * The id of the fileset object containing all resolved source jars for the list of dependencies.
     */
    private String sourcesFilesetId;
    
    /**
     * The id of the fileset object containing all resolved javadoc jars for the list of dependencies.
     */
    private String javadocFilesetId;
    
    /**
     * The id of the object containing a list of all artifact versions.
     * This is used for things like removing the version from the dependency filenames.
     */
    private String versionsId;

    /**
     * A specific maven scope used to determine which dependencies are resolved.
     * This takes only a single scope and uses the standard maven ScopeArtifactFilter.
     */
    private String useScope;

    /**
     * A comma separated list of dependency scopes to include, in the resulting path and fileset.
     */
    private String scopes;

    /**
     * A comma separated list of dependency types to include in the resulting set of artifacts.
     */
    private String type;

    /**
     * The file name to use for the generated Ant build that contains dependency properties and references.
     */
    private String dependencyRefsBuildFile;

    /**
     * Whether to use a generated Ant build file to cache the list of dependency properties and references.
     */
    private boolean cacheDependencyRefs;

    /**
     * Main task execution.  Called by parent execute().
     */
    protected void doExecute()
    {
        
        if ( useScope != null && scopes != null )
        {
            throw new BuildException( "You cannot specify both useScope and scopes in the dependencies task." );
        }
                
        if ( getPom() != null && !this.dependencies.isEmpty() )
        {
            throw new BuildException( "You cannot specify both dependencies and a pom in the dependencies task" );
        }
        
        // Try to load dependency refs from an exsiting Ant cache file
        if ( isCacheDependencyRefs() )
        {
            if ( getDependencyRefsBuildFile() == null )
            {
                setDependencyRefsBuildFile( DEFAULT_ANT_BUILD_FILE );
            }
            if ( checkCachedDependencies() )
            {
                log( "Dependency refs loaded from file: " + getDependencyRefsBuildFile(), Project.MSG_VERBOSE );
                return;
            }
        }
        
        ArtifactRepository localRepo = createLocalArtifactRepository();
        log( "Using local repository: " + localRepo.getBasedir(), Project.MSG_VERBOSE );

        // Look up required resources from the plexus container
        ArtifactResolver resolver = (ArtifactResolver) lookup( ArtifactResolver.ROLE );
        ArtifactFactory artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        MavenMetadataSource metadataSource = (MavenMetadataSource) lookup( ArtifactMetadataSource.ROLE );

        Pom pom = initializePom( localRepo );
        if ( pom != null )
        {
            dependencies = pom.getDependencies();
        }
        else
        {
            // we have to have some sort of Pom object in order to satisfy the requirements for building the
            // originating Artifact below...
            pom = createDummyPom( localRepo );
        }

        if ( dependencies.isEmpty() )
        {
            log( "There were no dependencies specified", Project.MSG_WARN );
        }

        log( "Resolving dependencies...", Project.MSG_VERBOSE );

        ArtifactResolutionResult result;
        Set artifacts;

        List remoteArtifactRepositories = createRemoteArtifactRepositories( pom.getRepositories() );

        try
        {
            artifacts = MavenMetadataSource.createArtifacts( artifactFactory, dependencies, null, null, null );

            Artifact pomArtifact = artifactFactory.createBuildArtifact( pom.getGroupId(), pom.getArtifactId(), 
                pom.getVersion(), pom.getPackaging() );

            List listeners = Collections.singletonList( new AntResolutionListener( getProject() ) );

            Map managedDependencies = pom.getMavenProject().getManagedVersionMap();

            ArtifactFilter filter = null;
            if ( useScope != null )
            {
                filter = new ScopeArtifactFilter( useScope );
            }
            if ( scopes != null )
            {
                filter = new SpecificScopesArtifactFilter( scopes );
            }
            if ( type != null )
            {
                ArtifactFilter typeArtifactFilter = new TypesArtifactFilter( type );
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
            throw new BuildException( "Invalid dependency version: " + e.getMessage(), e );
        }

        FileSet dependencyFileSet = createFileSet();

        FileSet sourcesFileSet = createFileSet();

        FileSet javadocsFileSet = createFileSet();

        Path dependencyPath = new Path( getProject() );
        
        Set versions = new HashSet();
        
        for ( Iterator i = result.getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();

            addArtifactToResult( localRepo, artifact, dependencyFileSet, dependencyPath );

            versions.add( artifact.getVersion() );

            if ( sourcesFilesetId != null )
            {
                resolveSource( artifactFactory, resolver, remoteArtifactRepositories, localRepo,
                               artifact, "sources", sourcesFileSet );
            }

            if ( javadocFilesetId != null )
            {
                resolveSource( artifactFactory, resolver, remoteArtifactRepositories, localRepo,
                               artifact, "javadoc", javadocsFileSet );
            }

        }

        defineFilesetReference( filesetId, dependencyFileSet );

        defineFilesetReference( sourcesFilesetId, sourcesFileSet );
        
        defineFilesetReference( javadocFilesetId, javadocsFileSet );
        
        if ( pathId != null )
        {
            getProject().addReference( pathId, dependencyPath );
        }
        
        if ( versionsId != null )
        {
            String versionsValue = StringUtils.join( versions.iterator(), File.pathSeparator );
            getProject().setNewProperty( versionsId, versionsValue );
        }
        
        // Write the dependency information to an Ant build file.
        if ( getDependencyRefsBuildFile() != null || this.isCacheDependencyRefs() )
        {
            if ( getDependencyRefsBuildFile() == null || getDependencyRefsBuildFile().equals( "default" ) )
            {
                setDependencyRefsBuildFile( DEFAULT_ANT_BUILD_FILE );
            }
            log( "Building ant file: " + getDependencyRefsBuildFile());
            AntBuildWriter antBuildWriter = new AntBuildWriter();
            File antBuildFile = new File( getProject().getBaseDir(), getDependencyRefsBuildFile() );
            try 
            {
                antBuildWriter.openAntBuild( antBuildFile, "maven-dependencies", "init-dependencies" );
                antBuildWriter.openTarget( "init-dependencies" );
                antBuildWriter.writeEcho( "Loading dependency paths from file: " + antBuildFile.getAbsolutePath() );
                
                Iterator i = result.getArtifacts().iterator();
                while (  i.hasNext() )
                {
                    Artifact artifact = (Artifact) i.next();
                    String conflictId = artifact.getDependencyConflictId();
                    antBuildWriter.writeProperty( conflictId, artifact.getFile().getAbsolutePath() );
                    FileSet singleArtifactFileSet = (FileSet)getProject().getReference( conflictId );
                    antBuildWriter.writeFileSet( singleArtifactFileSet, conflictId );
                }
                
                if ( pathId != null )
                {
                    Path thePath = (Path)getProject().getReference( pathId );
                    antBuildWriter.writePath( thePath, pathId );
                }
                
                if ( filesetId != null )
                {
                    antBuildWriter.writeFileSet( dependencyFileSet, filesetId );
                }
                if ( sourcesFilesetId != null )
                {
                    antBuildWriter.writeFileSet( sourcesFileSet, sourcesFilesetId );
                }
                if ( javadocFilesetId != null )
                {
                    antBuildWriter.writeFileSet( sourcesFileSet, javadocFilesetId );
                }
                
                String versionsList = getProject().getProperty( versionsId );
                antBuildWriter.writeProperty( versionsId, versionsList );
                
                antBuildWriter.closeTarget();
                antBuildWriter.closeAntBuild();
            }
            catch ( IOException e )
            {
                throw new BuildException ( "Unable to write ant build: " + e);
            }
        }
    }
    
    /**
     * Check if the cache needs to be updated.
     * 
     * @return true if the dependency refs were successfully loaded, false otherwise
     */
    private boolean checkCachedDependencies()
    {
        File cacheBuildFile = new File( getProject().getBaseDir(), getDependencyRefsBuildFile() );
        if ( ! cacheBuildFile.exists() )
        {
            return false;
        }
        
        File antBuildFile = new File( getProject().getProperty( "ant.file" ) );
        if ( antBuildFile.lastModified() > cacheBuildFile.lastModified() )
        {
            return false;
        }
        
        Pom pom = getPom();
        if ( pom != null )
        {
            File pomFile = pom.getFile();
            if ( pomFile == null || ( pomFile.lastModified() > cacheBuildFile.lastModified() ) )
            {
                return false;
            }
        }
        
        return loadDependenciesFromAntBuildFile();
    }
    
    /**
     * Load the dependency references from the generated ant build file.
     * 
     * @return True if the dependency refs were successfully loaded.
     */
    private boolean loadDependenciesFromAntBuildFile()
    {
        Project currentAntProject = getProject();
        
        // Run the ant build with the dependency refs
        AntTaskModified dependenciesAntBuild = new AntTaskModified();
        dependenciesAntBuild.setAntfile( getDependencyRefsBuildFile() );
        dependenciesAntBuild.setProject( currentAntProject );
        dependenciesAntBuild.execute();
        
        // Copy the properties and refs to the current project
        Project cachedDepsProject = dependenciesAntBuild.getSavedNewProject();
        AntUtil.copyProperties( cachedDepsProject, currentAntProject );
        AntUtil.copyReferences( cachedDepsProject, currentAntProject );
        
        return true;
    }
        
    private FileSet createFileSet()
    {
        FileSet fileSet = new FileSet();
        fileSet.setProject( getProject() );
        fileSet.setDir( getLocalRepository().getPath() );
        return fileSet;
    }

    private void defineFilesetReference( String id, FileSet fileSet )
    {
        if ( id != null )
        {
            if ( !fileSet.hasPatterns() )
            {
                fileSet.createExclude().setName( "**/**" );
            }
            getProject().addReference( id, fileSet );
        }
    }

    private void addArtifactToResult( ArtifactRepository localRepo, Artifact artifact, 
                                      FileSet toFileSet )
    {
        addArtifactToResult( localRepo, artifact, toFileSet, null );
    }
    
    private void addArtifactToResult( ArtifactRepository localRepo, Artifact artifact, 
                                      FileSet toFileSet, Path path )
    {
        String filename = localRepo.pathOf( artifact );

        toFileSet.createInclude().setName( filename );

        getProject().setProperty( artifact.getDependencyConflictId(), artifact.getFile().getAbsolutePath() );
        
        FileSet artifactFileSet = new FileSet();
        artifactFileSet.setProject( getProject() );
        artifactFileSet.setFile( artifact.getFile() );
        getProject().addReference( artifact.getDependencyConflictId(), artifactFileSet );
        
        if ( path != null )
        {
            path.addFileset( artifactFileSet );
        }
    }

    private void resolveSource( ArtifactFactory artifactFactory, ArtifactResolver resolver,
                                List remoteArtifactRepositories, ArtifactRepository localRepo,
                                Artifact artifact, String classifier, FileSet sourcesFileSet )
    {
        Artifact sourceArtifact =
            artifactFactory.createArtifactWithClassifier( artifact.getGroupId(), artifact.getArtifactId(),
                                                          artifact.getVersion(), "java-source", classifier );
        try
        {
            resolver.resolve( sourceArtifact, remoteArtifactRepositories, localRepo );

            addArtifactToResult( localRepo, sourceArtifact, sourcesFileSet );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new BuildException( "Unable to resolve artifact: " + e.getMessage(), e );
        }
        catch ( ArtifactNotFoundException e )
        {
            // no source available: no problem, it's optional
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

    public void setJavadocFilesetId( String filesetId )
    {
        this.javadocFilesetId = filesetId;
    }

    public String getJavadocFilesetId()
    {
        return javadocFilesetId;
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
        getProject().log( "Option \"verbose\" is deprecated.  Please use the standard Ant -v option.",
                          getProject().MSG_WARN );
    }

    /**
     * Use the maven artifact filtering for a particular scope.  This
     * uses the standard maven ScopeArtifactFilter.
     * 
     * @param useScope
     */
    public void setUseScope( String useScope )
    {
        this.useScope = useScope;
    }

    public void setType( String type )
    {
        this.type = type;
    }

    public String getScopes()
    {
        return scopes;
    }

    /**
     * Only include artifacts that fall under one of the specified scopes.
     * 
     * @return
     */
    public void setScopes( String scopes )
    {
        this.scopes = scopes;
    }

    /**
     * @deprecated
     * @param addArtifactFileSetRefs
     */
    public void setAddArtifactFileSetRefs( boolean addArtifactFileSetRefs )
    {
        this.log( "Parameter addArtifactFileSetRefs is deprecated.  A fileset ref is always created" +
        		"for each dependency.", getProject().MSG_WARN );
    }

    public String getDependencyRefsBuildFile()
    {
        return dependencyRefsBuildFile;
    }

    public void setDependencyRefsBuildFile( String dependencyRefsBuildFile )
    {
        this.dependencyRefsBuildFile = dependencyRefsBuildFile;
    }

    public boolean isCacheDependencyRefs()
    {
        return cacheDependencyRefs;
    }

    public void setCacheDependencyRefs( boolean cacheDependencyRefs )
    {
        this.cacheDependencyRefs = cacheDependencyRefs;
    }
}
