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
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;

import java.util.Iterator;

/**
 * Deploy task, using maven-artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class DeployTask
    extends InstallDeployTaskSupport
{
    private RemoteRepository remoteRepository;

    private RemoteRepository remoteSnapshotRepository;

    private boolean uniqueVersion = true;

    /**
     * Create a core-Maven deployment ArtifactRepository from a Maven Ant Tasks's RemoteRepository definition.
     * @param repository the remote repository as defined in Ant
     * @return the corresponding ArtifactRepository
     */
    protected ArtifactRepository createDeploymentArtifactRepository( RemoteRepository repository )
    {
        updateRepositoryWithSettings( repository );

        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, repository.getLayout() );

        ArtifactRepositoryFactory repositoryFactory = null;

        ArtifactRepository artifactRepository;

        try
        {
            repositoryFactory = getArtifactRepositoryFactory( repository );

            artifactRepository = repositoryFactory.createDeploymentArtifactRepository( repository.getId(), repository.getUrl(),
                                                                             repositoryLayout, uniqueVersion );
        }
        finally
        {
            releaseArtifactRepositoryFactory( repositoryFactory );
        }

        return artifactRepository;
    }


    protected void doExecute()
    {
        ArtifactRepository localRepo = createLocalArtifactRepository();

        Pom pom = buildPom( localRepo );

        if ( pom == null )
        {
            throw new BuildException( "A POM element is required to deploy to the repository" );
        }
        
        Artifact artifact = pom.getArtifact();

        // Deploy the POM
        boolean isPomArtifact = "pom".equals( pom.getPackaging() );
        if ( !isPomArtifact )
        {
            ArtifactMetadata metadata = new ProjectArtifactMetadata( artifact, pom.getFile() );
            artifact.addMetadata( metadata );
        }

        ArtifactRepository deploymentRepository = getDeploymentRepository( pom, artifact );

        log( "Deploying to " + deploymentRepository.getUrl() );
        ArtifactDeployer deployer = (ArtifactDeployer) lookup( ArtifactDeployer.ROLE );
        try
        {
            if ( !isPomArtifact )
            {
                deployer.deploy( file, artifact, deploymentRepository, localRepo );
            }
            else
            {
                deployer.deploy( pom.getFile(), artifact, deploymentRepository, localRepo );
            }

            // Deploy any attached artifacts
            if ( attachedArtifacts != null )
            {
                Iterator iter = pom.getAttachedArtifacts().iterator();

                while ( iter.hasNext() )
                {
                    Artifact attachedArtifact = (Artifact) iter.next();
                    deployer.deploy( attachedArtifact.getFile(), attachedArtifact, deploymentRepository, localRepo );
                }
            }
        }
        catch ( ArtifactDeploymentException e )
        {
            throw new BuildException(
                "Error deploying artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e );
        }
    }
    
    private ArtifactRepository getDeploymentRepository( Pom pom, Artifact artifact )
    {
        DistributionManagement distributionManagement = pom.getDistributionManagement();

        if ( remoteSnapshotRepository == null && remoteRepository == null )
        {
            if ( distributionManagement != null )
            {
                if ( distributionManagement.getSnapshotRepository() != null )
                {
                    remoteSnapshotRepository = createAntRemoteRepositoryBase( distributionManagement
                        .getSnapshotRepository() );
                    uniqueVersion = distributionManagement.getSnapshotRepository().isUniqueVersion();
                }
                if ( distributionManagement.getRepository() != null )
                {
                    remoteRepository = createAntRemoteRepositoryBase( distributionManagement.getRepository() );
                }
            }
        }

        if ( remoteSnapshotRepository == null )
        {
            remoteSnapshotRepository = remoteRepository;
        }

        ArtifactRepository deploymentRepository;
        if ( artifact.isSnapshot() && remoteSnapshotRepository != null )
        {
            deploymentRepository = createDeploymentArtifactRepository( remoteSnapshotRepository );
        }
        else if ( remoteRepository != null )
        {
            deploymentRepository = createDeploymentArtifactRepository( remoteRepository );
        }
        else
        {
            throw new BuildException(
                "A distributionManagement element or remoteRepository element is required to deploy" );
        }

        return deploymentRepository;
    }

    public RemoteRepository getRemoteRepository()
    {
        return remoteRepository;
    }

    public void addRemoteSnapshotRepository( RemoteRepository remoteSnapshotRepository )
    {
        this.remoteSnapshotRepository = remoteSnapshotRepository;
    }

    public void addRemoteRepository( RemoteRepository remoteRepository )
    {
        this.remoteRepository = remoteRepository;
    }

    public void setUniqueVersion( boolean uniqueVersion )
    {
        this.uniqueVersion = uniqueVersion;
    }

    public boolean getUniqueVersion()
    {
        return uniqueVersion;
    }
}
