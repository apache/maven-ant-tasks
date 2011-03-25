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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Scm;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.DefaultProjectBuilderConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilderConfiguration;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.ModelInheritanceAssembler;
import org.apache.maven.project.injection.ModelDefaultsInjector;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * A POM typedef. Also an Ant Task that registers a handler called POMPropertyHelper that intercepts all calls to
 * property value resolution and replies instead of Ant to properties that start with the id of the pom. Example:
 * ${maven.project.artifactId}
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:nicolaken@apache.org">Nicola Ken Barozzi</a>
 * @version $Id$
 */
public class Pom
    extends AbstractArtifactWithRepositoryTask
{
    /**
     * The id referring to an existing pom object in the current Ant project.
     */
    private String refid;

    /**
     * The id of this pom object to be stored in the current Ant project.
     */
    String antId;

    /**
     * The maven project represented by this pom
     */
    private MavenProject mavenProject;

    /**
     * The file from which the pom was loaded.
     */
    private File file;

    /**
     * The list of profiles to either activate or deactivate for this pom.
     */
    private List<Profile> profiles = new ArrayList<Profile>();

    private boolean inheritAllProperties = true;

    /**
     * The property intercepter.
     */
    private final POMPropertyHelper helper = new POMPropertyHelper( this );

    public String getRefid()
    {
        return refid;
    }

    /**
     * The ID used to retrieve this pom object from the Ant project.
     *
     * @param refid
     */
    public void setRefid( String refid )
    {
        this.refid = refid;
    }

    /**
     * The ID used to store this pom object in the Ant project.
     *
     * @param id
     */
    public void setId( String id )
    {
        this.antId = id;
    }

    /**
     * Retrieve the pom object from the current Ant project using the configured refid.
     *
     * @param refid
     * @return
     */
    protected void getPomFromAntProject( String refid )
    {
        if ( refid == null )
        {
            throw new BuildException( "POM refid is null." );
        }

        if ( getProject().getReference( refid ) == null )
        {
            throw new BuildException( "Unable to locate POM reference: '" + refid + "'" );
        }

        Pom thePom = (Pom) getProject().getReference( refid );
        mavenProject = thePom.getMavenProject();
        file = thePom.getFile();
    }

    public void setMavenProject( MavenProject mavenProject )
    {
        this.mavenProject = mavenProject;
    }

    public File getFile()
    {
        return file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    public List<Profile> getProfiles()
    {
        return profiles;
    }

    public void addProfile( Profile activeProfile )
    {
        this.profiles.add( activeProfile );
    }

    public Artifact getArtifact()
    {
        return getMavenProject().getArtifact();
    }

    public void attach( AttachedArtifact attached )
    {
        MavenProjectHelper helper = (MavenProjectHelper) lookup( MavenProjectHelper.ROLE );
        MavenProject project = getMavenProject();
        if ( attached.getClassifier() != null )
        {
            helper.attachArtifact( project, attached.getType(), attached.getClassifier(), attached.getFile() );
        }
        else
        {
            helper.attachArtifact( project, attached.getType(), attached.getFile() );
        }
    }

    public List<Artifact> getAttachedArtifacts()
    {
        return getMavenProject().getAttachedArtifacts();
    }

    public void initialiseMavenProject( MavenProjectBuilder builder, ArtifactRepository localRepository )
    {
        if ( file != null )
        {
            addAntRepositoriesToProfileManager();
            ProjectBuilderConfiguration builderConfig = this.createProjectBuilderConfig( localRepository );
            try
            {
                mavenProject = builder.build( file, builderConfig );

                builder.calculateConcreteState( mavenProject, builderConfig, false );
            }
            catch ( ProjectBuildingException pbe )
            {
                throw new BuildException( "Unable to initialize POM " + file.getName() + ": " + pbe.getMessage(), pbe );
            }
            catch ( ModelInterpolationException mie )
            {
                throw new BuildException( "Unable to interpolate POM " + file.getName() + ": " + mie.getMessage(), mie );
            }
        }
        else if ( refid != null )
        {
            this.getPomFromAntProject( refid );
        }
        else if ( mavenProject != null )
        {
            addAntRepositoriesToProfileManager();
            ProjectBuilderConfiguration builderConfig = this.createProjectBuilderConfig( localRepository );
            try
            {
                builder.calculateConcreteState( mavenProject, builderConfig, false );
            }
            catch ( ModelInterpolationException mie )
            {
                throw new BuildException( "Unable to interpolate POM " + file.getName() + ": " + mie.getMessage(), mie );
            }

        }
        if ( mavenProject != null && mavenProject.getModel().getParent() != null )
        {
            String parentGroupId = mavenProject.getModel().getParent().getGroupId();
            String parentArtifactId = mavenProject.getModel().getParent().getArtifactId();
            String parentVersion = mavenProject.getModel().getParent().getVersion();
            Iterator i = getAntReactorPoms().iterator();
            while ( i.hasNext() )
            {
                Pom pom = (Pom)i.next();
                if ( StringUtils.equals( parentGroupId, pom.getGroupId() )
                        && StringUtils.equals( parentArtifactId, pom.getArtifactId() )
                        && StringUtils.equals( parentVersion, pom.getVersion() ) )
                {
                    pom.initialiseMavenProject( builder, localRepository );
                    mavenProject.setParent( pom.getMavenProject() );
                    ModelInheritanceAssembler modelInheritanceAssembler =
                            (ModelInheritanceAssembler) lookup( ModelInheritanceAssembler. ROLE );
                    modelInheritanceAssembler.assembleModelInheritance( mavenProject.getModel(), pom.getModel() );
                    break;
                }
            }
        }
        ModelDefaultsInjector modelDefaultsInjector = (ModelDefaultsInjector) lookup( ModelDefaultsInjector.ROLE );
        modelDefaultsInjector.injectDefaults(mavenProject.getModel());
    }

    protected MavenProject getMavenProject()
    {
        if ( mavenProject == null )
        {
            mavenProject = createMinimalProject( createLocalArtifactRepository() );
        }
        return mavenProject;
    }

    public String getArtifactId()
    {
        return getMavenProject().getArtifactId();
    }

    public Build getBuild()
    {
        return getMavenProject().getBuild();
    }

    public CiManagement getCiManagement()
    {
        return getMavenProject().getCiManagement();
    }

    public List getContributors()
    {
        return getMavenProject().getContributors();
    }

    public List<Dependency> getDependencies()
    {
        return getMavenProject().getDependencies();
    }

    public DependencyManagement getDependencyManagement()
    {
        return getMavenProject().getDependencyManagement();
    }

    public String getDescription()
    {
        return getMavenProject().getDescription();
    }

    public List getDevelopers()
    {
        return getMavenProject().getDevelopers();
    }

    public DistributionManagement getDistributionManagement()
    {
        return getMavenProject().getDistributionManagement();
    }

    public String getGroupId()
    {
        return getMavenProject().getGroupId();
    }

    public String getInceptionYear()
    {
        return getMavenProject().getInceptionYear();
    }

    public IssueManagement getIssueManagement()
    {
        return getMavenProject().getIssueManagement();
    }

    public List getLicenses()
    {
        return getMavenProject().getLicenses();
    }

    public List getMailingLists()
    {
        return getMavenProject().getMailingLists();
    }

    public String getModelVersion()
    {
        return getMavenProject().getModelVersion();
    }

    public List getModules()
    {
        return getMavenProject().getModules();
    }

    public String getName()
    {
        return getMavenProject().getName();
    }

    public Organization getOrganization()
    {
        return getMavenProject().getOrganization();
    }

    public String getPackaging()
    {
        return getMavenProject().getPackaging();
    }

    public List getPluginRepositories()
    {
        return getMavenProject().getPluginRepositories();
    }

    public Reporting getReporting()
    {
        return getMavenProject().getReporting();
    }

    public List<Repository> getRepositories()
    {
        return getMavenProject().getRepositories();
    }

    public Scm getScm()
    {
        return getMavenProject().getScm();
    }

    public String getUrl()
    {
        return getMavenProject().getUrl();
    }

    public String getVersion()
    {
        return getMavenProject().getVersion();
    }

    public String getId()
    {
        return getMavenProject().getId();
    }

    /**
     * Registers POMPropertyHelper as a property interceptor in Ant 1.6 - 1.7.1, or property delegate in Ant 1.8.0
     */
    protected void doExecute()
    {
        if ( getId() == null )
        {
            throw new BuildException( "id required for pom task" );
        }
        ArtifactRepository localRepo = createLocalArtifactRepository();
        MavenProjectBuilder projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
        initialiseMavenProject( projectBuilder, localRepo );

        Project antProject = getProject();

        // Add a reference to this task/type
        antProject.addReference( antId, this );

        // Register the property intercepter or delegate
        PropertyHelper phelper = PropertyHelper.getPropertyHelper( antProject );
        try
        {
            // Ant 1.8.0 delegate
            POMPropertyEvaluator.register( this, phelper );
        }
        catch ( LinkageError e )
        {
            // fallback to 1.6 - 1.7.1 intercepter chaining
            helper.setNext( phelper.getNext() );
            helper.setProject( antProject );
            phelper.setNext( helper );
        }
    }

    /**
     * The repositories defined in the ant "pom" task need to be added manually to the profile manager. Otherwise they
     * won't be available when resolving the parent pom. MANTTASKS-87
     */
    private void addAntRepositoriesToProfileManager()
    {
        List<RemoteRepository> remoteRepositories = this.getRemoteRepositories();

        if ( remoteRepositories == null || remoteRepositories.isEmpty() )
        {
            return;
        }
        org.apache.maven.model.Profile repositoriesProfile = new org.apache.maven.model.Profile();
        repositoriesProfile.setId( "maven-ant-tasks-repo-profile" );

        for ( RemoteRepository antRepo : remoteRepositories )
        {
            Repository mavenRepo = new Repository();
            mavenRepo.setId( antRepo.getId() );
            mavenRepo.setUrl( antRepo.getUrl() );
            repositoriesProfile.addRepository( mavenRepo );
        }

        getProfileManager().addProfile( repositoriesProfile );
        getProfileManager().explicitlyActivate( repositoriesProfile.getId() );
    }

    private ProfileManager getActivatedProfiles()
    {
        ProfileManager profileManager = getProfileManager();

        for ( Profile profile : getProfiles() )
        {
            if ( profile.getId() == null )
            {
                throw new BuildException( "Attribute \"id\" is required for profile in pom type." );
            }

            if ( profile.getActive() == null || Boolean.valueOf( profile.getActive() ).booleanValue() )
            {
                profileManager.explicitlyActivate( profile.getId() );
            }
            else
            {
                profileManager.explicitlyDeactivate( profile.getId() );
            }

        }
        return profileManager;
    }

    /**
     * Create a project builder configuration to be used when initializing the maven project.
     *
     * @return
     */
    private ProjectBuilderConfiguration createProjectBuilderConfig( ArtifactRepository localArtifactRepository )
    {
        ProjectBuilderConfiguration builderConfig = new DefaultProjectBuilderConfiguration();
        builderConfig.setLocalRepository( localArtifactRepository );
        builderConfig.setGlobalProfileManager( this.getActivatedProfiles() );
        builderConfig.setUserProperties( getAntProjectProperties() );
        builderConfig.setExecutionProperties( getAntProjectProperties() );

        return builderConfig;
    }

    /**
     * Convert the Hashtable of Ant project properties to a Properties object
     *
     * @return The Ant project properties
     */
    public Properties getAntProjectProperties()
    {
        Properties properties = new Properties();
        Hashtable propsTable = null;
        if ( this.isInheritAllProperties() )
        {
            propsTable = getProject().getProperties();
        }
        else
        {
            propsTable = getProject().getUserProperties();
        }
        Iterator propsIter = propsTable.keySet().iterator();

        while ( propsIter.hasNext() )
        {
            String key = (String) propsIter.next();
            String value = (String) propsTable.get( key );
            properties.setProperty( key, value );
        }

        return properties;
    }

    /**
     * If set to true, all properties are passed to the maven pom. If set to false, only user properties are passed to
     * the pom.
     *
     * @param inheritAllProperties
     */
    public void setInheritAllProperties( boolean inheritAllProperties )
    {
        this.inheritAllProperties = inheritAllProperties;
    }

    public boolean isInheritAllProperties()
    {
        return inheritAllProperties;
    }

    public Model getModel()
    {
        return getMavenProject().getModel();
    }

    public void setGroupId( String groupId )
    {
        getMavenProject().setGroupId( groupId );
    }

    public void setArtifactId( String artifactId )
    {
        getMavenProject().setArtifactId( artifactId );
    }

    public void setVersion( String version )
    {
        getMavenProject().setVersion( version );
    }

    public void addConfiguredParent( Parent parent )
    {
        getMavenProject().getModel().setParent( parent );
    }

    public void addConfiguredCiManagement( CiManagement ciManagement )
    {
        getMavenProject().setCiManagement( ciManagement );
    }

    public void addConfiguredContributor ( Contributor contributor )
    {
        getMavenProject().addContributor( contributor );
    }

    public void addConfiguredDependency( Dependency dependency )
    {
        getMavenProject().getDependencies().add( dependency );
    }

    public void addConfiguredDependencyManagement( DependencyManagement dependencyManagement )
    {
        if ( getMavenProject().getDependencyManagement() == null )
        {
            // is is a bit disappointing that we have to access the encapsulated model to fix the NPE
            getMavenProject().getModel().setDependencyManagement(new DependencyManagement());
        }
        getMavenProject().getDependencyManagement().setDependencies( dependencyManagement.getDependencies() );
    }

    public void setDescription( String description )
    {
        getMavenProject().setDescription( description );
    }

    public void addConfiguredDeveloper( Developer developer )
    {
        getMavenProject().addDeveloper( developer );
    }

    public void setInceptionYear( String inceptionYear )
    {
        getMavenProject().setInceptionYear( inceptionYear );
    }

    public void addConfiguredIssueManagement( IssueManagement issueManagement )
    {
        getMavenProject().setIssueManagement( issueManagement );
    }

    public void addConfiguredLicense ( License license )
    {
        getMavenProject().addLicense( license );
    }

    public void addConfiguredMailingLists( MailingList mailingList )
    {
        getMavenProject().addMailingList( mailingList );
    }

    public void setName( String name )
    {
        getMavenProject().setName( name );
    }

    public void addConfiguredOrganization( Organization organization )
    {
        getMavenProject().setOrganization( organization );
    }

    public void setPackaging( String packaging )
    {
        getMavenProject().setPackaging( packaging );
    }

    public void addConfiguredScm( Scm scm )
    {
        getMavenProject().setScm( scm );
    }

    public void setUrl( String url )
    {
        getMavenProject().setUrl( url );
    }

}
