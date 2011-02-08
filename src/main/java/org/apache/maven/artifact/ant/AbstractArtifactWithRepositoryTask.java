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

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Repository;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

/**
 * Base class for artifact tasks that are able to download artifact from remote repositories.
 * @version $Id$
 */
public abstract class AbstractArtifactWithRepositoryTask
    extends AbstractArtifactTask
{
    /**
     * List of Ant Tasks RemoteRepository-ies
     */
    private List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

    /**
     * Get the default remote repository.
     * @return central repository
     */
    private static RemoteRepository getDefaultRemoteRepository()
    {
        // TODO: could we utilize the super POM for this?
        RemoteRepository remoteRepository = new RemoteRepository();
        remoteRepository.setId( "central" );
        remoteRepository.setUrl( "http://repo1.maven.org/maven2" );
        RepositoryPolicy snapshots = new RepositoryPolicy();
        snapshots.setEnabled( false );
        remoteRepository.addSnapshots( snapshots );
        return remoteRepository;
    }

    private static String statusAsString( RepositoryPolicy policy )
    {
        return ( policy == null ) || policy.isEnabled() ? "enabled" : "disabled";
    }

    protected List<ArtifactRepository> createRemoteArtifactRepositories()
    {
        return createRemoteArtifactRepositories( null );
    }

    /**
     * Create the list of ArtifactRepository-ies where artifacts can be downloaded. If
     * no remote repository has been configured, adds central repository.
     * 
     * @param pomRepositories additional repositories defined in pom (or null if none)
     * @return the list of ArtifactRepository-ies
     * @see #createRemoteArtifactRepository(RemoteRepository)
     */
    protected List<ArtifactRepository> createRemoteArtifactRepositories( List<Repository> pomRepositories )
    {
        List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

        // First, add repositories configured in Ant
        remoteRepositories.addAll( getRemoteRepositories() );

        // Add repositories configured in POM
        if ( pomRepositories != null )
        {
            for ( Repository pomRepository : pomRepositories )
            {
                remoteRepositories.add( createAntRemoteRepository( pomRepository ) );
            }
        }

        // Only add default repository if no repositories were configured otherwise
        if ( remoteRepositories.isEmpty() )
        {
            remoteRepositories.add( getDefaultRemoteRepository() );
        }

        log( "Using remote repositories:", Project.MSG_VERBOSE );
        List<ArtifactRepository> list = new ArrayList<ArtifactRepository>();
        Set<String> ids = new HashSet<String>();
        for ( RemoteRepository remoteRepository : remoteRepositories )
        {
            if ( !ids.add( remoteRepository.getId() ) )
            {
                // repository id already added to the list: ignore it, since it has been overridden
                continue;
            }
            updateRepositoryWithSettings( remoteRepository );

            StringBuffer msg = new StringBuffer();
            msg.append( "  - id=" + remoteRepository.getId() );
            msg.append( ", url=" + remoteRepository.getUrl() );
            msg.append( ", releases=" + statusAsString( remoteRepository.getReleases() ) );
            msg.append( ", snapshots=" + statusAsString( remoteRepository.getSnapshots() ) );
            if ( remoteRepository.getAuthentication() != null )
            {
                msg.append( ", authentication=" + remoteRepository.getAuthentication().getUserName() );
            }
            if ( remoteRepository.getProxy() != null )
            {
                msg.append( ", proxy=" + remoteRepository.getProxy().getHost() );
            }
            getProject().log( msg.toString(), Project.MSG_VERBOSE );

            list.add( createRemoteArtifactRepository( remoteRepository ) );
        }
        return list;
    }

    /**
     * The repositories configured in the Ant task
     * 
     * @return The list of repositories
     */
    public List<RemoteRepository> getRemoteRepositories()
    {
        return remoteRepositories;
    }

    /**
     * This is called automatically by ant when the task is initialized.
     * Need to use "addConfigured..." instead of "add..." because the
     * repository Id and URL need to be set before the method is called.
     *
     * @param remoteRepository
     */
    public void addConfiguredRemoteRepository( RemoteRepository remoteRepository )
    {
        if ( remoteRepository.getRefid() != null )
        {
            // check that the refid points to a repository that is properly defined
            String refid = remoteRepository.getRefid();
            if ( getProject().getReference( refid ) == null )
            {
                throw new BuildException( "Unknown remote repository refid='" + refid + "'." );
            }
        }
        // Validate the url and id parameters before adding the repository
        if ( remoteRepository.getUrl() == null )
        {
            throw new BuildException( "Each remote repository must specify a url." );
        }
        if ( remoteRepository.getId() == null || remoteRepository.getId().equals( remoteRepository.getUrl() ) )
        {
            log( "Each remote repository must specify a unique id. For backward-compatibility, "
                 + "a default id will be used. In future releases, a missing repository id will raise an error.",
                  Project.MSG_WARN );
            remoteRepository.setId( generateDefaultRepositoryId( remoteRepository ) );
        }
        remoteRepositories.add( remoteRepository );
    }

    public final String MD5_ALGO_NAME = "MD5";

    public final String UTF_ENC_NAME = "UTF-8";

    /**
     * Generates an MD5 digest based on the url of the repository.
     * This is safer to use for the id than the url.
     * MANTTASKS-142
     *
     * @param repository
     * @return
     */
    public String generateDefaultRepositoryId( RemoteRepository repository )
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance( MD5_ALGO_NAME );
            md.update( repository.getUrl().getBytes( UTF_ENC_NAME ) );
            BigInteger digest = new BigInteger( md.digest() );
            return digest.toString( 16 );
        }
        catch ( Exception e )
        {
            log( "Unable to generate unique repository Id: " + e, Project.MSG_WARN );
            return "default";
        }
    }
}