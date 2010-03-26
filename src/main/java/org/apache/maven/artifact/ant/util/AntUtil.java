package org.apache.maven.artifact.ant.util;

import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.tools.ant.Project;

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

/**
 * Utility stuff for dealing with Ant.
 */
public class AntUtil
{

    /**
     * Copies all properties from the given project to the new project - omitting those that have already been set in the
     * new project as well as properties named basedir or ant.file.
     *
     * @param fromProject copy from
     * @param toProject copy to
     */
    public static void copyProperties( Project fromProject, Project toProject )
    {
        copyProperties( fromProject.getProperties(), toProject );
    }

    /**
     * Copies all properties from the given table to the new project - omitting those that have already been set in the
     * new project as well as properties named basedir or ant.file.
     *
     * @param props properties <code>Hashtable</code> to copy to the new project.
     * @param project the project where the properties are added
     */
    public static void copyProperties( Hashtable props, Project project )
    {
        Enumeration e = props.keys();
        while ( e.hasMoreElements() )
        {
            String key = e.nextElement().toString();
            if ( "basedir".equals( key ) || "ant.file".equals( key ) )
            {
                // basedir and ant.file get special treatment in execute()
                continue;
            }

            String value = props.get( key ).toString();
            // don't re-set user properties, avoid the warning message
            if ( project.getProperty( key ) == null )
            {
                // no user property
                project.setNewProperty( key, value );
            }
        }
    }

    /**
     * Copy references from one project to another.
     *
     * @param fromProject
     * @param toProject
     */
    public static void copyReferences( Project fromProject, Project toProject )
    {
        copyReferences( fromProject.getReferences(), toProject );
    }

    /**
     * Copy references from a hashtable to a project.  Will not
     * overwrite existing references.
     *
     * @param refs
     * @param project
     */
    public static void copyReferences( Hashtable refs, Project project )
    {
        Enumeration e = refs.keys();
        while ( e.hasMoreElements() )
        {
            String key = e.nextElement().toString();
            // don't overwrite existing references
            if ( project.getReference( key ) == null )
            {
                project.addReference( key, refs.get( key ) );
            }
        }
    }

}
