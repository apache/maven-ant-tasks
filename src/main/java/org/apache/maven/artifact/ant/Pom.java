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
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Scm;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.PropertyHelper;
import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A POM typedef.
 *
 * Also an Ant Task that registers a handler called POMPropertyHelper that intercepts all calls to property value
 * resolution and replies instead of Ant to properties that start with the id of the pom.
 *
 * Example: ${maven.project.artifactId}
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @author <a href="mailto:nicolaken@apache.org">Nicola Ken Barozzi</a>
 * @version $Id$
 */
public class Pom extends AbstractArtifactWithRepositoryTask
{
    private String refid;

    private String antId;

    private MavenProject mavenProject;

    private File file;

    private List profiles = new ArrayList();

    /**
     * The property interceptor.
     */
    private final POMPropertyHelper helper = new POMPropertyHelper();

    public String getRefid()
    {
        return refid;
    }

    public void setRefid( String refid )
    {
        this.refid = refid;
    }

    public void setId( String id )
    {
        this.antId = id;
    }

    protected Pom getInstance()
    {
        Pom instance = this;
        if ( refid != null )
        {
            instance = (Pom) getProject().getReference( refid );
            if ( instance == null )
            {
                throw new BuildException( "Invalid reference: '" + refid + "'" );
            }
        }
        return instance;
    }

    public void setMavenProject( MavenProject mavenProject )
    {
        getInstance().mavenProject = mavenProject;
    }

    public File getFile()
    {
        return getInstance().file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    public List getProfiles()
    {
    	return profiles;
    }

    public void addProfile(Profile activeProfile)
    {
    	this.profiles.add(activeProfile);
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

    public List getAttachedArtifacts()
    {
        return getMavenProject().getAttachedArtifacts();
    }

    void initialise( MavenProjectBuilder builder, ArtifactRepository localRepository )
    {
        if ( mavenProject != null )
        {
            log( "POM is already initialized for: " + mavenProject.getId(), Project.MSG_DEBUG );

            return;
        }
        // TODO: should this be in execute() too? Would that work when it is used as a type?
        if ( file != null )
        {
            addAntRepositoriesToProfileManager();

            try
            {

                mavenProject = builder.build( file, localRepository, getActivatedProfiles() );
            }
            catch ( ProjectBuildingException e )
            {
                throw new BuildException( "Unable to initialize POM " + file.getName() + ": " + e.getMessage(), e );
            }
        }
        else if ( refid != null )
        {
            getInstance().initialise( builder, localRepository );
        }
    }

    protected MavenProject getMavenProject()
    {
        return getInstance().mavenProject;
    }

    public String getArtifactId()
    {
        return getMavenProject().getArtifactId();
    } // -- String getArtifactId()

    public Build getBuild()
    {
        return getMavenProject().getBuild();
    } // -- Build getBuild()

    public CiManagement getCiManagement()
    {
        return getMavenProject().getCiManagement();
    } // -- CiManagement getCiManagement()

    public List getContributors()
    {
        return getMavenProject().getContributors();
    } // -- List getContributors()

    public List getDependencies()
    {
        return getMavenProject().getDependencies();
    } // -- List getDependencies()

    public DependencyManagement getDependencyManagement()
    {
        return getMavenProject().getDependencyManagement();
    } // -- DependencyManagement getDependencyManagement()

    public String getDescription()
    {
        return getMavenProject().getDescription();
    } // -- String getDescription()

    public List getDevelopers()
    {
        return getMavenProject().getDevelopers();
    } // -- List getDevelopers()

    public DistributionManagement getDistributionManagement()
    {
        return getMavenProject().getDistributionManagement();
    } // -- DistributionManagement getDistributionManagement()

    public String getGroupId()
    {
        return getMavenProject().getGroupId();
    } // -- String getGroupId()

    public String getInceptionYear()
    {
        return getMavenProject().getInceptionYear();
    } // -- String getInceptionYear()

    public IssueManagement getIssueManagement()
    {
        return getMavenProject().getIssueManagement();
    } // -- IssueManagement getIssueManagement()

    public List getLicenses()
    {
        return getMavenProject().getLicenses();
    } // -- List getLicenses()

    public List getMailingLists()
    {
        return getMavenProject().getMailingLists();
    } // -- List getMailingLists()

    public String getModelVersion()
    {
        return getMavenProject().getModelVersion();
    } // -- String getModelVersion()

    public List getModules()
    {
        return getMavenProject().getModules();
    } // -- List getModules()

    public String getName()
    {
        return getMavenProject().getName();
    } // -- String getName()

    public Organization getOrganization()
    {
        return getMavenProject().getOrganization();
    } // -- Organization getOrganization()

    public String getPackaging()
    {
        return getMavenProject().getPackaging();
    } // -- String getPackaging()

    public List getPluginRepositories()
    {
        return getMavenProject().getPluginRepositories();
    } // -- List getPluginRepositories()

    public Reporting getReporting()
    {
        return getMavenProject().getReporting();
    } // -- Reports getReports()

    public List getRepositories()
    {
        return getMavenProject().getRepositories();
    } // -- List getRepositories()

    public Scm getScm()
    {
        return getMavenProject().getScm();
    } // -- Scm getScm()

    public String getUrl()
    {
        return getMavenProject().getUrl();
    } // -- String getUrl()

    public String getVersion()
    {
        return getMavenProject().getVersion();
    } // -- String getVersion()

    public String getId()
    {
        return getMavenProject().getId();
    }

    /**
     * Registers POMPropertyHelper as a property interceptor
     */
    protected void doExecute()
    {
        ArtifactRepository localRepo = createLocalArtifactRepository();
        MavenProjectBuilder projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
        initialise( projectBuilder, localRepo );

        Project project = getProject();

        // Add a reference to this task/type
        project.addReference( antId, this );

        // Register the property interceptor
        PropertyHelper phelper = PropertyHelper.getPropertyHelper( project );
        helper.setNext( phelper.getNext() );
        helper.setProject( project );
        phelper.setNext( helper );
    }

    /**
     * The property interceptor that handles the calls for "pom." properties
     */
    private class POMPropertyHelper extends PropertyHelper
    {
        /**
         * The method that gets called by Ant with every request of property
         */
        public Object getPropertyHook( String ns, String name, boolean user )
        {
            String prefix = antId + ".";

            if ( !name.startsWith( prefix ) )
            {
                // pass on to next interceptor
                return super.getPropertyHook( ns, name, user );
            }
            try
            {
                // else handle the property resolution
                String expression = name.substring( prefix.length() );
                return getPOMValue( "project." + expression );
            }
            catch ( Exception ex )
            {
                ex.printStackTrace();
                return null;
            }
        }

        private static final String PROPERTIES_PREFIX = "project.properties.";

        private Object getPOMValue( String expression )
        {
            Object value = null;

            try
            {
                if ( expression.startsWith( PROPERTIES_PREFIX ) )
                {
                    expression = expression.substring( PROPERTIES_PREFIX.length() );
                    value = getMavenProject().getProperties().get( expression );
                }
                else
                {
                    value = ReflectionValueExtractor.evaluate( expression, getMavenProject() );
                }
            }
            catch ( Exception e )
            {
                throw new BuildException( "Error extracting expression from POM", e );
            }

            return value;
        }

    }

    /**
     * The repositories defined in the ant "pom" task need to be added manually to the profile manager. Otherwise they
     * won't be available when resolving the parent pom. MANTTASKS-87
     */
    private void addAntRepositoriesToProfileManager()
    {
        List remoteRepositories = this.getRemoteRepositories();

        if ( remoteRepositories == null || remoteRepositories.isEmpty() )
        {
            return;
        }
        org.apache.maven.model.Profile repositoriesProfile = new org.apache.maven.model.Profile();
        repositoriesProfile.setId( "maven-ant-tasks-repo-profile" );

        Iterator iter = remoteRepositories.iterator();
        while ( iter.hasNext() )
        {
            RemoteRepository antRepo = (RemoteRepository) iter.next();
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

        Iterator it = getProfiles().iterator();
        while ( it.hasNext() )
        {
            Profile profile = (Profile) it.next();

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
}
