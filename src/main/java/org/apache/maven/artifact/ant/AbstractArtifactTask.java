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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.usability.diagnostics.ErrorDiagnostics;
import org.apache.maven.wagon.Wagon;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Execute;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.embed.Embedder;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base class for artifact tasks.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public abstract class AbstractArtifactTask
    extends Task
{
    private Settings settings;

    private PlexusContainer container;

    private Pom pom;

    private String pomRefId;

    private LocalRepository localRepository;

    protected ArtifactRepository createLocalArtifactRepository()
    {
        if ( localRepository == null )
        {
            localRepository = getDefaultLocalRepository();
        }

        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, localRepository.getLayout() );

        return new DefaultArtifactRepository( "local", "file://" + localRepository.getPath(), repositoryLayout );
    }

    protected ArtifactRepository createRemoteArtifactRepository( RemoteRepository repository )
    {
        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, repository.getLayout() );

        WagonManager manager = (WagonManager) lookup( WagonManager.ROLE );

        Authentication authentication = repository.getAuthentication();
        if ( authentication != null )
        {
            manager.addAuthenticationInfo( repository.getId(), authentication.getUserName(),
                                           authentication.getPassword(), authentication.getPrivateKey(),
                                           authentication.getPassphrase() );
        }

        Proxy proxy = repository.getProxy();
        if ( proxy != null )
        {
            manager.addProxy( proxy.getType(), proxy.getHost(), proxy.getPort(), proxy.getUserName(),
                              proxy.getPassword(), proxy.getNonProxyHosts() );
        }

        ArtifactRepositoryFactory repositoryFactory = null;

        ArtifactRepository artifactRepository;

        try
        {
            repositoryFactory = (ArtifactRepositoryFactory) lookup( ArtifactRepositoryFactory.ROLE );

            ArtifactRepositoryPolicy snapshots = buildArtifactRepositoryPolicy( repository.getSnapshots() );
            ArtifactRepositoryPolicy releases = buildArtifactRepositoryPolicy( repository.getReleases() );

            artifactRepository = repositoryFactory.createArtifactRepository( repository.getId(), repository.getUrl(),
                                                                             repositoryLayout, snapshots, releases );
        }
        finally
        {
            try
            {
                getContainer().release( repositoryFactory );
            }
            catch ( ComponentLifecycleException e )
            {
                // TODO: Warn the user, or not?
            }
        }

        return artifactRepository;
    }

    private static ArtifactRepositoryPolicy buildArtifactRepositoryPolicy( RepositoryPolicy policy )
    {
        boolean enabled = true;
        String updatePolicy = null;
        String checksumPolicy = null;

        if ( policy != null )
        {
            enabled = policy.isEnabled();
            if ( policy.getUpdatePolicy() != null )
            {
                updatePolicy = policy.getUpdatePolicy();
            }
            if ( policy.getChecksumPolicy() != null )
            {
                checksumPolicy = policy.getChecksumPolicy();
            }
        }

        return new ArtifactRepositoryPolicy( enabled, updatePolicy, checksumPolicy );
    }

    protected LocalRepository getDefaultLocalRepository()
    {
        Settings settings = getSettings();
        LocalRepository localRepository = new LocalRepository();
        localRepository.setId( "local" );
        localRepository.setPath( new File( settings.getLocalRepository() ) );
        return localRepository;
    }

    protected synchronized Settings getSettings()
    {
        if ( settings == null )
        {
            settings = new Settings();

            File settingsFile = new File( System.getProperty( "user.home" ), ".ant/settings.xml" );
            if ( !settingsFile.exists() )
            {
                settingsFile = new File( System.getProperty( "user.home" ), ".m2/settings.xml" );
            }
            if ( !settingsFile.exists() )
            {
                settingsFile = new File( System.getProperty( "ant.home" ), "etc/settings.xml" );
            }
            if ( !settingsFile.exists() )
            { // look in ${M2_HOME}/conf
                List env = Execute.getProcEnvironment();
                for ( Iterator iter = env.iterator(); iter.hasNext(); )
                {
                    String var = (String) iter.next();
                    if ( var.startsWith( "M2_HOME=" ) )
                    {
                        String m2_home = var.substring( "M2_HOME=".length() );
                        settingsFile = new File( m2_home, "conf/settings.xml" );
                        break;
                    }
                }
            }

            if ( settingsFile.exists() )
            {
                loadSettings(settingsFile);
            }

            if ( StringUtils.isEmpty( settings.getLocalRepository() ) )
            {
                String location = new File( System.getProperty( "user.home" ), ".m2/repository" ).getAbsolutePath();
                settings.setLocalRepository( location );
            }
        }
        return settings;
    }

    private void loadSettings(File settingsFile) {
        FileReader reader = null;
        try
        {
            log( "Loading Maven settings file: " + settingsFile.getPath(), Project.MSG_VERBOSE );
            reader = new FileReader( settingsFile );

            SettingsXpp3Reader modelReader = new SettingsXpp3Reader();

            settings = modelReader.read( reader );
        }
        catch ( IOException e )
        {
            log( "Error reading settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage(),
                 Project.MSG_WARN );
        }
        catch ( XmlPullParserException e )
        {
            log( "Error parsing settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage(),
                 Project.MSG_WARN );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }
    
    public void setSettingsFile(File settingsFile) {
        if (!settingsFile.exists()) throw new BuildException("settingsFile does not exist: " + settingsFile.getAbsolutePath());
        settings = new Settings();
        loadSettings(settingsFile);
    }

    protected RemoteRepository createAntRemoteRepository( org.apache.maven.model.Repository pomRepository )
    {
        RemoteRepository r = createAntRemoteRepositoryBase( pomRepository );

        if ( pomRepository.getSnapshots() != null )
        {
            r.addSnapshots( convertRepositoryPolicy( pomRepository.getSnapshots() ) );
        }
        if ( pomRepository.getReleases() != null )
        {
            r.addReleases( convertRepositoryPolicy( pomRepository.getReleases() ) );
        }

        return r;
    }

    protected RemoteRepository createAntRemoteRepositoryBase( org.apache.maven.model.RepositoryBase pomRepository )
    {
        // TODO: actually, we need to not funnel this through the ant repository - we should pump settings into wagon
        // manager at the start like m2 does, and then match up by repository id
        // As is, this could potentially cause a problem with 2 remote repositories with different authentication info

        RemoteRepository r = new RemoteRepository();
        r.setId( pomRepository.getId() );
        r.setUrl( pomRepository.getUrl() );
        r.setLayout( pomRepository.getLayout() );

        Server server = getSettings().getServer( pomRepository.getId() );
        if ( server != null )
        {
            r.addAuthentication( new Authentication( server ) );
        }

        org.apache.maven.settings.Proxy proxy = getSettings().getActiveProxy();
        if ( proxy != null )
        {
            r.addProxy( new Proxy( proxy ) );
        }

        Mirror mirror = getSettings().getMirrorOf( pomRepository.getId() );
        if ( mirror != null )
        {
            r.setUrl( mirror.getUrl() );
        }
        return r;
    }

    protected Object lookup( String role )
    {
        try
        {
            return getContainer().lookup( role );
        }
        catch ( ComponentLookupException e )
        {
            throw new BuildException( "Unable to find component: " + role, e );
        }
    }

    protected Object lookup( String role,
                             String roleHint )
    {
        try
        {
            return getContainer().lookup( role, roleHint );
        }
        catch ( ComponentLookupException e )
        {
            throw new BuildException( "Unable to find component: " + role + "[" + roleHint + "]", e );
        }
    }

    protected static RemoteRepository getDefaultRemoteRepository()
    {
        // TODO: could we utilise the super POM for this?
        RemoteRepository remoteRepository = new RemoteRepository();
        remoteRepository.setId( "central" );
        remoteRepository.setUrl( "http://repo1.maven.org/maven2" );
        RepositoryPolicy snapshots = new RepositoryPolicy();
        snapshots.setEnabled( false );
        remoteRepository.addSnapshots( snapshots );
        return remoteRepository;
    }

    protected synchronized PlexusContainer getContainer()
    {
        if ( container == null )
        {
            container = (PlexusContainer) getProject().getReference( PlexusContainer.class.getName() );

            if ( container == null )
            {
                try
                {
                    ClassWorld classWorld = new ClassWorld();

                    classWorld.newRealm( "plexus.core", getClass().getClassLoader() );

                    Embedder embedder = new Embedder();

                    embedder.start( classWorld );

                    container = embedder.getContainer();
                }
                catch ( PlexusContainerException e )
                {
                    throw new BuildException( "Unable to start embedder", e );
                }
                catch ( DuplicateRealmException e )
                {
                    throw new BuildException( "Unable to create embedder ClassRealm", e );
                }

                getProject().addReference( PlexusContainer.class.getName(), container );
            }
        }

        return container;
    }

    public Pom buildPom( MavenProjectBuilder projectBuilder,
                         ArtifactRepository localArtifactRepository )
    {
        if ( pomRefId != null && pom != null )
        {
            throw new BuildException( "You cannot specify both a POM element and a pomrefid element" );
        }

        Pom pom = this.pom;
        if ( pomRefId != null )
        {
            pom = (Pom) getProject().getReference( pomRefId );
            if ( pom == null )
            {
                throw new BuildException( "Reference '" + pomRefId + "' was not found." );
            }
        }

        if ( pom != null )
        {
            pom.initialise( projectBuilder, localArtifactRepository );
        }
        return pom;
    }

    protected Pom createDummyPom()
    {
        Model mavenModel = new Model();

        mavenModel.setGroupId( "unspecified" );
        mavenModel.setArtifactId( "unspecified" );
        mavenModel.setVersion( "0.0" );
        mavenModel.setPackaging( "jar" );

        MavenProject mavenProject = new MavenProject( mavenModel );

        Pom pom = new Pom();

        pom.setMavenProject( mavenProject );

        return pom;
    }
    
    public String[] getSupportedProtocols()
    {
        try
        {
            Map wagonMap = getContainer().lookupMap( Wagon.ROLE );
            List protocols = new ArrayList();
            for ( Iterator iter = wagonMap.entrySet().iterator(); iter.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) iter.next();
                protocols.add( entry.getKey() );
            }
            return (String[]) protocols.toArray( new String[protocols.size()] );
        }
        catch ( ComponentLookupException e )
        {
            throw new BuildException( "Unable to lookup Wagon providers", e );
        }
    }

    public String getSupportedProtocolsAsString()
    {
        return StringUtils.join( getSupportedProtocols(), ", " );
    }
    
    public void diagnoseError( Throwable error )
    {
        try
        {
            ErrorDiagnostics diagnostics = (ErrorDiagnostics) container.lookup( ErrorDiagnostics.ROLE );

            StringBuffer message = new StringBuffer();

            message.append( "An error has occurred while processing the Maven artifact tasks.\n" );
            message.append( " Diagnosis:\n\n" );

            message.append( diagnostics.diagnose( error ) );

            message.append( "\n\n" );

            log( message.toString(), Project.MSG_INFO );
        }
        catch ( ComponentLookupException e )
        {
            log( "Failed to retrieve error diagnoser.", Project.MSG_DEBUG );
        }
    }

    public void addPom( Pom pom )
    {
        this.pom = pom;
    }

    public String getPomRefId()
    {
        return pomRefId;
    }

    public void setPomRefId( String pomRefId )
    {
        this.pomRefId = pomRefId;
    }

    public LocalRepository getLocalRepository()
    {
        return localRepository;
    }

    public void addLocalRepository( LocalRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    public void setProfiles( String profiles )
    {
        if ( profiles != null )
        {
            // TODO: not sure this is the best way to do this...
            log( "Profiles not yet supported, ignoring profiles '" + profiles + "'", Project.MSG_WARN );
//            System.setProperty( ProfileActivationUtils.ACTIVE_PROFILE_IDS, profiles );
        }
    }

    protected Artifact createArtifact( Pom pom )
    {
        ArtifactFactory factory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        // TODO: maybe not strictly correct, while we should enfore that packaging has a type handler of the same id, we don't
        return factory.createBuildArtifact( pom.getGroupId(), pom.getArtifactId(), pom.getVersion(),
                                            pom.getPackaging() );
    }

    private static RepositoryPolicy convertRepositoryPolicy( org.apache.maven.model.RepositoryPolicy pomRepoPolicy )
    {
        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled( pomRepoPolicy.isEnabled() );
        policy.setUpdatePolicy( pomRepoPolicy.getUpdatePolicy() );
        return policy;
    }

    /** @noinspection RefusedBequest */
    public void execute()
    {
        try
        {
            doExecute();
        }
        catch ( BuildException e )
        {
            diagnoseError( e );

            throw e;
        }
    }

    protected abstract void doExecute();
}
