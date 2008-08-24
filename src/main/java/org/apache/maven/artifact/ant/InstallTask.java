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
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;

import java.util.Iterator;

/**
 * Install task, using maven-artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 * @todo should be able to incorporate into the install mojo?
 */
public class InstallTask
    extends InstallDeployTaskSupport
{
    protected void doExecute()
    {
        ArtifactRepository localRepo = createLocalArtifactRepository();

        Pom pom = buildPom( localRepo );

        if ( pom == null )
        {
            throw new BuildException( "A POM element is required to install to the local repository" );
        }
        
        Artifact artifact = pom.getArtifact();

        boolean isPomArtifact = "pom".equals( pom.getPackaging() );
        if ( !isPomArtifact )
        {
            ArtifactMetadata metadata = new ProjectArtifactMetadata( artifact, pom.getFile() );
            artifact.addMetadata( metadata );
        }

        ArtifactInstaller installer = (ArtifactInstaller) lookup( ArtifactInstaller.ROLE );
        try
        {
            if ( !isPomArtifact )
            {
                if ( file == null )
                {
                    throw new BuildException( "You must specify a file to install to the local repository." );
                }

                installer.install( file, artifact, localRepo );
            }
            else
            {
                installer.install( pom.getFile(), artifact, localRepo );
            }

            // Install any attached artifacts
            if ( attachedArtifacts != null )
            {
                Iterator iter = pom.getAttachedArtifacts().iterator();

                while ( iter.hasNext() )
                {
                    Artifact attachedArtifact = (Artifact) iter.next();
                    installer.install( attachedArtifact.getFile(), attachedArtifact, localRepo );
                }
            }
        }
        catch ( ArtifactInstallationException e )
        {
            throw new BuildException(
                "Error installing artifact '" + artifact.getDependencyConflictId() + "': " + e.getMessage(), e );
        }
    }
}
