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

import org.apache.tools.ant.PropertyHelper;

/**
 * POM Property Delegate, for Ant 1.8.0.
 *
 * @since maven-ant-tasks 2.1.1
 */
class POMPropertyEvaluator
    extends POMPropertyHelper
    implements PropertyHelper.PropertyEvaluator
{

    public static void register( Pom pom, PropertyHelper propertyHelper )
    {
        propertyHelper.add( new POMPropertyEvaluator( pom ) );
    }

    private POMPropertyEvaluator( Pom pom )
    {
        super( pom );
    }

    public Object evaluate( String property, PropertyHelper propertyHelper )
    {
        String prefix = pom.antId + ".";

        if ( !property.startsWith( prefix ) )
        {
            return null;
        }

        try
        {
            // else handle the property resolution
            String expression = property.substring( prefix.length() );
            return getPOMValue( "project." + expression );
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
            return null;
        }
    }

}
