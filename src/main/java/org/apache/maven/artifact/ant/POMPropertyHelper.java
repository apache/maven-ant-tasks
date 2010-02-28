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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.PropertyHelper;
import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;

/**
 * The property intercepter that handles the calls for "pom." properties in Ant 1.6 - 1.7.1
 */
class POMPropertyHelper
    extends PropertyHelper
{

    protected final Pom pom;

    POMPropertyHelper( Pom pom )
    {
        this.pom = pom;
    }

    /**
     * The method that gets called by Ant 1.6 - 1.7.1 with every request of property
     */
    public Object getPropertyHook( String ns, String name, boolean user )
    {
        String prefix = this.pom.antId + ".";

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

    protected Object getPOMValue( String expression )
    {
        Object value = null;

        try
        {
            if ( expression.startsWith( PROPERTIES_PREFIX ) )
            {
                expression = expression.substring( PROPERTIES_PREFIX.length() );
                value = pom.getMavenProject().getProperties().get( expression );
            }
            else
            {
                value = ReflectionValueExtractor.evaluate( expression, pom.getMavenProject() );
            }
        }
        catch ( Exception e )
        {
            throw new BuildException( "Error extracting expression from POM", e );
        }

        return value;
    }

}
