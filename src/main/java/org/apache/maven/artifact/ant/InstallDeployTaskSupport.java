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
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;

/**
 * Support for install/deploy tasks.
 *
 * @author <a href="mailto:jdillon@apache.org">Jason Dillon</a>
 * @version $Id$
 */
public abstract class InstallDeployTaskSupport
    extends AbstractArtifactTask
{
    protected File file;

    protected List attachedArtifacts;

    public File getFile()
    {
        return file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    protected Artifact createArtifactFromAttached(final AttachedArtifact attached, final Artifact parent)
    {
        ArtifactFactory factory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );

        Artifact artifact;
        if (attached.getClassifier() != null) {
            artifact = factory.createArtifactWithClassifier(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion(),
                attached.getType(),
                attached.getClassifier()
            );
        }
        else {
            artifact = factory.createArtifact(
                parent.getGroupId(),
                parent.getArtifactId(),
                parent.getVersion(),
                null, // scope
                attached.getType()
            );
        }

        artifact.setFile( attached.getFile() );

        return artifact;
    }

    public AttachedArtifact createAttach()
    {
        if (attachedArtifacts == null) {
            attachedArtifacts = new ArrayList();
        }

        AttachedArtifact attach = new AttachedArtifact();
        attachedArtifacts.add(attach);

        return attach;
    }
}
