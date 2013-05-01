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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.RuntimeInfo;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.TrackableBase;
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
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

/**
 * Base class for artifact tasks.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public abstract class AbstractArtifactTask
    extends Task
{
    private static final String WILDCARD = "*";

    private static final String EXTERNAL_WILDCARD = "external:*";

    private static ClassLoader plexusClassLoader;

    private File userSettingsFile;

    private File globalSettingsFile;

    private Settings settings;

    private ProfileManager profileManager;

    private PlexusContainer container;

    private Pom pom;

    private String pomRefId;

    private LocalRepository localRepository;

    protected ArtifactRepository createLocalArtifactRepository()
    {
        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, getLocalRepository().getLayout() );

        return new DefaultArtifactRepository( "local", "file://" + getLocalRepository().getPath(), repositoryLayout );
    }

    /**
     * Create a core-Maven ArtifactRepositoryFactory from a Maven Ant Tasks's RemoteRepository definition,
     * eventually configured with authentication and proxy information.
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepositoryFactory
     */
    protected ArtifactRepositoryFactory getArtifactRepositoryFactory( RemoteRepository repository )
    {
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

        return (ArtifactRepositoryFactory) lookup( ArtifactRepositoryFactory.ROLE );
    }

    protected void releaseArtifactRepositoryFactory( ArtifactRepositoryFactory repositoryFactory )
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

    /**
     * Create a core-Maven ArtifactRepository from a Maven Ant Tasks's RemoteRepository definition.
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepository
     */
    protected ArtifactRepository createRemoteArtifactRepository( RemoteRepository repository )
    {
        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, repository.getLayout() );

        ArtifactRepositoryFactory repositoryFactory = null;

        ArtifactRepository artifactRepository;

        try
        {
            repositoryFactory = getArtifactRepositoryFactory( repository );

            ArtifactRepositoryPolicy snapshots = buildArtifactRepositoryPolicy( repository.getSnapshots() );
            ArtifactRepositoryPolicy releases = buildArtifactRepositoryPolicy( repository.getReleases() );

            artifactRepository = repositoryFactory.createArtifactRepository( repository.getId(), repository.getUrl(),
                                                                             repositoryLayout, snapshots, releases );
        }
        finally
        {
            releaseArtifactRepositoryFactory( repositoryFactory );
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
            initSettings();
        }
        return settings;
    }

    private File newFile( String parent, String subdir, String filename )
    {
        return new File( new File( parent, subdir ), filename );
    }

    private void initSettings()
    {
        if ( userSettingsFile == null )
        {
            File tempSettingsFile = newFile( System.getProperty( "user.home" ), ".ant", "settings.xml" );
            if ( tempSettingsFile.exists() )
            {
                userSettingsFile = tempSettingsFile;
            }
            else
            {
                tempSettingsFile = newFile( System.getProperty( "user.home" ), ".m2", "settings.xml" );
                if ( tempSettingsFile.exists() )
                {
                    userSettingsFile = tempSettingsFile;
                }
            }
        }
        if ( globalSettingsFile == null )
        {
            File tempSettingsFile = newFile( System.getProperty( "ant.home" ), "etc", "settings.xml" );
            if ( tempSettingsFile.exists() )
            {
                globalSettingsFile = tempSettingsFile;
            }
            else
            {
                // look in ${M2_HOME}/conf
                List<String> env = Execute.getProcEnvironment();
                for ( String var: env )
                {
                    if ( var.startsWith( "M2_HOME=" ) )
                    {
                        String m2Home = var.substring( "M2_HOME=".length() );
                        tempSettingsFile = newFile( m2Home, "conf", "settings.xml" );
                        if ( tempSettingsFile.exists() )
                        {
                            globalSettingsFile = tempSettingsFile;
                        }
                        break;
                    }
                }
            }
        }

        Settings userSettings = loadSettings( userSettingsFile );
        Settings globalSettings = loadSettings( globalSettingsFile );

        SettingsUtils.merge( userSettings, globalSettings, TrackableBase.GLOBAL_LEVEL );
        settings = userSettings;

        if ( StringUtils.isEmpty( settings.getLocalRepository() ) )
        {
            String location = newFile( System.getProperty( "user.home" ), ".m2", "repository" ).getAbsolutePath();
            settings.setLocalRepository( location );
        }

        WagonManager wagonManager = (WagonManager) lookup( WagonManager.ROLE );
        wagonManager.setDownloadMonitor( new AntDownloadMonitor() );
        if ( settings.isOffline() )
        {
            log( "You are working in offline mode.", Project.MSG_INFO );
            wagonManager.setOnline( false );
        }
        else
        {
            wagonManager.setOnline( true );
        }
    }

    private Settings loadSettings( File settingsFile )
    {
        Settings settings = null;
        try
        {
            if ( settingsFile != null )
            {
                log( "Loading Maven settings file: " + settingsFile.getPath(), Project.MSG_VERBOSE );
                settings = readSettings( settingsFile );
            }
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

        if ( settings == null )
        {
            settings = new Settings();
            RuntimeInfo rtInfo = new RuntimeInfo( settings );
            settings.setRuntimeInfo( rtInfo );
        }

        return settings;
    }

    public void setSettingsFile( File settingsFile )
    {
        if ( !settingsFile.exists() )
        {
            throw new BuildException( "settingsFile does not exist: " + settingsFile.getAbsolutePath() );
        }

        userSettingsFile = settingsFile;
        settings = null;
    }

    /**
     * @see org.apache.maven.settings.DefaultMavenSettingsBuilder#readSettings
     */
    private Settings readSettings( File settingsFile )
        throws IOException, XmlPullParserException
    {
        Settings settings = null;
        Reader reader = null;
        try
        {
            reader = ReaderFactory.newXmlReader( settingsFile );
            StringWriter sWriter = new StringWriter();

            IOUtil.copy( reader, sWriter );

            String rawInput = sWriter.toString();

            try
            {
                RegexBasedInterpolator interpolator = new RegexBasedInterpolator();
                interpolator.addValueSource( new EnvarBasedValueSource() );

                rawInput = interpolator.interpolate( rawInput, "settings" );
            }
            catch ( Exception e )
            {
                log( "Failed to initialize environment variable resolver. Skipping environment substitution in "
                     + "settings." );
            }

            StringReader sReader = new StringReader( rawInput );

            SettingsXpp3Reader modelReader = new SettingsXpp3Reader();

            settings = modelReader.read( sReader );

            RuntimeInfo rtInfo = new RuntimeInfo( settings );

            rtInfo.setFile( settingsFile );

            settings.setRuntimeInfo( rtInfo );
        }
        finally
        {
            IOUtil.close( reader );
        }
        return settings;
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
        RemoteRepository r = new RemoteRepository();
        r.setId( pomRepository.getId() );
        r.setUrl( pomRepository.getUrl() );
        r.setLayout( pomRepository.getLayout() );

        return r;
    }

    protected void updateRepositoryWithSettings( RemoteRepository repository )
    {
        // TODO: actually, we need to not funnel this through the ant repository - we should pump settings into wagon
        // manager at the start like m2 does, and then match up by repository id
        // As is, this could potentially cause a problem with 2 remote repositories with different authentication info
        updateRepositoryMirror( repository );
        updateRepositoryAuthentication( repository );
        updateRepositoryProxy( repository );
    }

    protected void updateRepositoryMirror(RemoteRepository repository) {
        Mirror mirror = getMirror( getSettings().getMirrors(), repository );
        if ( mirror != null )
        {
            repository.setUrl( mirror.getUrl() );
            repository.setId( mirror.getId() );
        }
    }

    protected void updateRepositoryAuthentication(RemoteRepository repository) {
        if ( repository.getAuthentication() == null )
        {
            Server server = getSettings().getServer( repository.getId() );
            if ( server != null )
            {
                Authentication authentication = new Authentication( server );
                
                String password = authentication.getPassword();
                
                if (password != null) {
                    try {
                        SecDispatcher securityDispatcher = (SecDispatcher) container.lookup(SecDispatcher.ROLE);
                        password = securityDispatcher.decrypt(password);
                        authentication.setPassword(password);
                    } catch (SecDispatcherException e) {
                        log(e, Project.MSG_ERR);
                    } catch (ComponentLookupException e) {
                        log(e, Project.MSG_ERR);
                    }
                }
                
                repository.addAuthentication( authentication );
            }
        }
    }

    protected void updateRepositoryProxy(RemoteRepository repository) {
        if ( repository.getProxy() == null )
        {
            org.apache.maven.settings.Proxy proxy = getSettings().getActiveProxy();
            if ( proxy != null )
            {
                repository.addProxy( new Proxy( proxy ) );
            }
        }
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

    /**
     * Tries to initialize the pom.  If no pom has been configured, returns null.
     *
     * @param localArtifactRepository
     * @return An initialized pom or null.
     */
    public Pom initializePom( ArtifactRepository localArtifactRepository )
    {

        Pom pom = getPom();
        if ( pom != null )
        {
            MavenProjectBuilder projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
            pom.initialiseMavenProject( projectBuilder, localArtifactRepository );
        }

        return pom;
    }

    protected Pom createDummyPom( ArtifactRepository localRepository )
    {
        Pom pom = new Pom();

        MavenProject minimalProject = createMinimalProject( localRepository );
        // we nulled out these fields to allow inheritance when creating poms, but the dummy
        // needs to be a valid pom, so set them back to something that's OK to resolve
        minimalProject.setGroupId( "org.apache.maven" );
        minimalProject.setArtifactId( "super-pom" );
        minimalProject.setVersion( "2.0" );
        minimalProject.setPackaging( "pom" );
        pom.setMavenProject( minimalProject );

        return pom;
    }

    /**
     * Create a minimal project when no POM is available.
     *
     * @param localRepository
     * @return
     */
    protected MavenProject createMinimalProject( ArtifactRepository localRepository )
    {
        MavenProjectBuilder projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
        DefaultProjectBuilderConfiguration builderConfig = new DefaultProjectBuilderConfiguration( );
        builderConfig.setLocalRepository( localRepository );
        builderConfig.setGlobalProfileManager( getProfileManager() );

        try
        {
            MavenProject mavenProject = projectBuilder.buildStandaloneSuperProject(builderConfig);
            // if we don't null out these fields then the pom that will be created is at the super-pom's
            // GAV coordinates and we will not be able to inherit partial GAV coordinates from a parent GAV.
            mavenProject.setGroupId(null);
            mavenProject.setArtifactId(null);
            mavenProject.setVersion(null);
            return mavenProject;
        }
        catch ( ProjectBuildingException e )
        {
            throw new BuildException( "Unable to create dummy Pom", e );
        }

    }

    protected Artifact createDummyArtifact()
    {
        ArtifactFactory factory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        // TODO: maybe not strictly correct, while we should enforce that packaging has a type handler of the same id, we don't
        return factory.createBuildArtifact( "unspecified", "unspecified", "0.0", "jar" );
    }

    public String[] getSupportedProtocols()
    {
        try
        {
            Map<String,Wagon> wagonMap = getContainer().lookupMap( Wagon.ROLE );
            List<String> protocols = new ArrayList<String>();
            for ( Map.Entry<String,Wagon> entry : wagonMap.entrySet() )
            {
                protocols.add( entry.getKey() );
            }
            return protocols.toArray( new String[protocols.size()] );
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

    /**
     * Try to get the POM from the nested pom element or a pomRefId
     *
     * @return The pom object
     */
    public Pom getPom()
    {
        Pom thePom = this.pom;

        if ( thePom != null && getPomRefId() != null )
        {
            throw new BuildException( "You cannot specify both a nested \"pom\" element and a \"pomrefid\" attribute" );
        }

        if ( getPomRefId() != null )
        {
            Object pomRefObj = getProject().getReference( getPomRefId() );
            if ( pomRefObj instanceof Pom )
            {
                thePom = (Pom) pomRefObj;
            }
            else
            {
                throw new BuildException( "Reference '" + pomRefId + "' was not found." );
            }
        }

        return thePom;
    }

    public String getPomRefId()
    {
        return pomRefId;
    }

    /**
     * Try to get all the poms with id's which have been added to the ANT project
     * @return
     */
    public List/*<Pom>*/ getAntReactorPoms()
    {
        List result = new ArrayList();
        Iterator i = getProject().getReferences().values().iterator();
        while ( i.hasNext() )
        {
            Object ref = i.next();
            if ( ref instanceof Pom )
            {
                result.add( ref );
            }
        }
        return result;
    }

    public void setPomRefId( String pomRefId )
    {
        this.pomRefId = pomRefId;
    }

    public LocalRepository getLocalRepository()
    {
        if ( localRepository == null )
        {
            localRepository = getDefaultLocalRepository();
        }
        return localRepository;
    }

    protected ProfileManager getProfileManager()
    {
        if ( profileManager == null )
        {
            profileManager = new DefaultProfileManager( getContainer(), getSettings(), System.getProperties() );
        }
        return profileManager;
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

    private static RepositoryPolicy convertRepositoryPolicy( org.apache.maven.model.RepositoryPolicy pomRepoPolicy )
    {
        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled( pomRepoPolicy.isEnabled() );
        policy.setUpdatePolicy( pomRepoPolicy.getUpdatePolicy() );
        return policy;
    }

    /** @noinspection RefusedBequest */
    @Override
	public void execute()
    {
        // Display the version if the log level is verbose
        showVersion();

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            if ( plexusClassLoader != null )
            {
                Thread.currentThread().setContextClassLoader( plexusClassLoader );
            }
            initSettings();
            doExecute();
        }
        catch ( BuildException e )
        {
            diagnoseError( e );

            throw e;
        }
        finally
        {
            plexusClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( originalClassLoader );
        }
    }

    /**
     * The main entry point for the task.
     */
    protected abstract void doExecute();

    /**
     * This method finds a matching mirror for the selected repository. If there is an exact match,
     * this will be used. If there is no exact match, then the list of mirrors is examined to see if
     * a pattern applies.
     *
     * @param mirrors The available mirrors.
     * @param repository See if there is a mirror for this repository.
     * @return the selected mirror or null if none is found.
     */
    private Mirror getMirror( List<Mirror> mirrors, RemoteRepository repository )
    {
        String repositoryId = repository.getId();

        if ( repositoryId != null )
        {
            for ( Mirror mirror : mirrors )
            {
                if ( repositoryId.equals( mirror.getMirrorOf() ) )
                {
                    return mirror;
                }
            }

            for ( Mirror mirror : mirrors )
            {
                if ( matchPattern( repository, mirror.getMirrorOf() ) )
                {
                    return mirror;
                }
            }
        }

        return null;
    }

    /**
     * This method checks if the pattern matches the originalRepository. Valid patterns: * =
     * everything external:* = everything not on the localhost and not file based. repo,repo1 = repo
     * or repo1 *,!repo1 = everything except repo1
     *
     * @param originalRepository to compare for a match.
     * @param pattern used for match. Currently only '*' is supported.
     * @return true if the repository is a match to this pattern.
     */
    boolean matchPattern( RemoteRepository originalRepository, String pattern )
    {
        boolean result = false;
        String originalId = originalRepository.getId();

        // simple checks first to short circuit processing below.
        if ( WILDCARD.equals( pattern ) || pattern.equals( originalId ) )
        {
            result = true;
        }
        else
        {
            // process the list
            String[] repos = pattern.split( "," );

            for ( int i = 0; i < repos.length; i++ )
            {
                String repo = repos[i];

                // see if this is a negative match
                if ( repo.length() > 1 && repo.startsWith( "!" ) )
                {
                    if ( originalId.equals( repo.substring( 1 ) ) )
                    {
                        // explicitly exclude. Set result and stop processing.
                        result = false;
                        break;
                    }
                }
                // check for exact match
                else if ( originalId.equals( repo ) )
                {
                    result = true;
                    break;
                }
                // check for external:*
                else if ( EXTERNAL_WILDCARD.equals( repo ) && isExternalRepo( originalRepository ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
                else if ( WILDCARD.equals( repo ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
            }
        }
        return result;
    }

    /**
     * Checks the URL to see if this repository refers to an external repository
     *
     * @param originalRepository
     * @return true if external.
     */
    boolean isExternalRepo( RemoteRepository originalRepository )
    {
        try
        {
            URL url = new URL( originalRepository.getUrl() );
            return !( url.getHost().equals( "localhost" ) || url.getHost().equals( "127.0.0.1" ) || url.getProtocol().equals( "file" ) );
        }
        catch ( MalformedURLException e )
        {
            // bad url just skip it here. It should have been validated already, but the wagon lookup will deal with it
            return false;
        }
    }

    /**
     * Log the current version of the ant-tasks to the verbose output.
     */
    protected void showVersion()
    {

        Properties properties = new Properties();
        final String antTasksPropertiesPath = "META-INF/maven/org.apache.maven/maven-ant-tasks/pom.properties";
        InputStream resourceAsStream = AbstractArtifactTask.class.getClassLoader().getResourceAsStream( antTasksPropertiesPath );

        try
        {
            if ( resourceAsStream != null )
            {
                properties.load( resourceAsStream );
            }

            String version = properties.getProperty( "version", "unknown" );
            String builtOn = properties.getProperty( "builtOn" );
            if ( builtOn != null )
            {
                log( "Maven Ant Tasks version: " + version + " built on " + builtOn, Project.MSG_VERBOSE );
            }
            else
            {
                log( "Maven Ant Tasks version: " + version, Project.MSG_VERBOSE );
            }
        }
        catch ( IOException e )
        {
            log( "Unable to determine version from Maven Ant Tasks JAR file: " + e.getMessage(), Project.MSG_WARN );
        }
    }

}
