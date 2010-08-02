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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileList;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.XMLWriter;
import org.codehaus.plexus.util.xml.XmlWriterUtil;

/**
 * Utility class for writing an Ant build file.
 */
public class AntBuildWriter
{
    public static final String DEFAULT_FILE_ENCODING = "UTF-8";

    /**
     * The default line indenter
     */
    protected static final int DEFAULT_INDENTATION_SIZE = XmlWriterUtil.DEFAULT_INDENTATION_SIZE;

    private XMLWriter writer;

    private OutputStreamWriter outputStreamWriter;

    /**
     * Open an Ant build file for writing. Opens the file and prints the opening project tag.
     *
     * @param buildFile
     * @param name
     * @param defaultTarget
     * @throws IOException
     */
    public void openAntBuild( File dependenciesBuildFile, String name, String defaultTarget )
        throws IOException
    {
        String encoding = DEFAULT_FILE_ENCODING;

        if ( ! dependenciesBuildFile.getParentFile().exists() )
        {
            dependenciesBuildFile.getParentFile().mkdirs();
        }
        outputStreamWriter = new OutputStreamWriter( new FileOutputStream( dependenciesBuildFile ), encoding );

        writer =
            new PrettyPrintXMLWriter( outputStreamWriter, StringUtils.repeat( " ", DEFAULT_INDENTATION_SIZE ),
                                      encoding, null );
        writer.startElement( "project" );
        writer.addAttribute( "name", name );
        writer.addAttribute( "default", defaultTarget );

        XmlWriterUtil.writeLineBreak( writer );
    }

    /**
     * Close the ant build writer
     *
     * @throws IOException
     */
    public void closeAntBuild()
        throws IOException
    {
        writer.endElement();

        XmlWriterUtil.writeLineBreak( writer );

        IOUtil.close( outputStreamWriter );
    }

    /**
     * Open a target tag
     *
     * @param targetName
     * @throws IOException
     */
    public void openTarget( String targetName )
        throws IOException
    {
        writer.startElement( "target" );
        writer.addAttribute( "name", targetName );
    }

    /**
     * Close a tag.
     *
     * @throws IOException
     */
    public void closeTarget()
        throws IOException
    {
        writer.endElement();
    }

    /**
     * Write an Ant fileset
     *
     * @param fileSet
     * @param id
     */
    public void writeFileSet( FileSet fileSet, String id )
    {
        writer.startElement( "fileset" );

        if ( id != null )
        {
            writer.addAttribute( "id", id );
        }

        File dir = fileSet.getDir( fileSet.getProject() );
        writer.addAttribute( "dir", dir.getAbsolutePath() );

        DirectoryScanner scanner = fileSet.getDirectoryScanner( fileSet.getProject() );
        scanner.scan();
        String[] files = scanner.getIncludedFiles();

        for ( int i = 0; i < files.length; ++i )
        {
            writer.startElement( "include" );
            writer.addAttribute( "name", files[i] );
            writer.endElement();
        }

        writer.endElement();
    }

    /**
     * Write an ant property
     *
     * @param name
     * @param value
     */
    public void writeProperty( String name, String value )
    {
        writer.startElement( "property" );

        writer.addAttribute( "name", name );
        writer.addAttribute( "value", value );

        writer.endElement();
    }

    /**
     * Write an Ant echo task
     *
     * @param message
     */
    public void writeEcho( String message )
    {
        writer.startElement( "echo" );

        writer.addAttribute( "message", message );

        writer.endElement();
    }

    /**
     * Write an Ant file list
     *
     * @param fileList
     * @param id
     */
    public void writeFileList( FileList fileList, String id )
    {
        writer.startElement( "filelist" );
        writer.addAttribute( "id", id );
        File dir = fileList.getDir( fileList.getProject() );
        writer.addAttribute( "dir", dir.getAbsolutePath() );

        String[] files = fileList.getFiles( fileList.getProject() );
        for ( int i = 0; i < files.length; ++i )
        {
            writer.startElement( "file" );
            writer.addAttribute( "name", files[i] );
            writer.endElement();
        }
        writer.endElement();
    }

    /**
     * Write a path.
     *
     * @param path
     * @param pathId
     */
    public void writePath( Path path, String pathId )
    {
        writer.startElement( "path" );
        writer.addAttribute( "id", pathId );
        String[] paths = path.list();
        for ( int i = 0; i < paths.length; ++i )
        {
            writer.startElement( "pathelement" );
            writer.addAttribute( "path", paths[i] );
            writer.endElement();
        }
        writer.endElement();
    }
}
