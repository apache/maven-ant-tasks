package org.apache.maven.artifact.ant.util;

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

import java.lang.reflect.Field;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Ant;

/**
 * A modified version of the Ant task that allows access to the
 * new sub project.
 *
 * @author pgier
 */
public class AntTaskModified
    extends Ant
{

    /**
     * The Ant tasks sets newProject to null at the end of execute(), so
     * we need to save this object to a different place.
     */
    private Project savedNewProject;

    public void init()
    {
        super.init();
        savedNewProject = saveNewProject();
    }

    /**
     * This is a hack to get access to the private variable "newProject" in the Ant task. This should not be used.
     * Note: This may not work with later versions of Ant
     *
     * @return
     */
    private Project saveNewProject()
    {
        try
        {
            Field newProjectField = Ant.class.getDeclaredField( "newProject" );
            newProjectField.setAccessible( true );

            return (Project) newProjectField.get( this );
        }
        catch ( Exception e )
        {
            throw new BuildException( "Unable to load cache: " + e, e );
        }
    }

    /**
     * Get the new Ant project created by this task.
     *
     * @return
     */
    public Project getSavedNewProject()
    {
        return savedNewProject;
    }

}
