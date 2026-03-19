/*

Copyright 2025-2026 Jeffrey J. Weston <jjweston@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/

package io.github.jjweston.omegacodex;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class MarkdownSplitter
{
    private final ProcessBuilderFactory processBuilderFactory;
    private final ThreadedReader stdoutReader;
    private final ThreadedReader stderrReader;

    MarkdownSplitter()
    {
        this( new ProcessBuilderFactory(), new ThreadedReader(), new ThreadedReader() );
    }

    MarkdownSplitter(
            ProcessBuilderFactory processBuilderFactory, ThreadedReader stdoutReader, ThreadedReader stderrReader )
    {
        this.processBuilderFactory = processBuilderFactory;
        this.stdoutReader = stdoutReader;
        this.stderrReader = stderrReader;
    }

    List< String > split( Path inputFilePath )
    {
        if ( inputFilePath == null ) throw new IllegalArgumentException( "Input file path must not be null." );

        File inputFile = inputFilePath.toFile();
        if ( !inputFile.exists() ) throw new IllegalArgumentException( "Input file must exist." );

        Path pythonToolsPath = Paths.get( "python-tools" );
        Path scriptPath = Paths.get( "split_markdown.py" );

        ProcessBuilder processBuilder =
                this.processBuilderFactory.create( "poetry", "run", "python", scriptPath.toString() );
        processBuilder.directory( pythonToolsPath.toFile() );
        processBuilder.redirectInput( inputFile );

        Process process;
        try { process = processBuilder.start(); }
        catch ( IOException e ) { throw new OmegaCodexException( "IOException starting Python process.", e ); }

        int exitCode;
        try ( stdoutReader; stderrReader )
        {
            stdoutReader.start( process.inputReader() );
            stderrReader.start( process.errorReader() );

            try { exitCode = process.waitFor(); }
            catch ( InterruptedException e )
            {
                Thread.currentThread().interrupt();
                throw new OmegaCodexException( e );
            }

            stdoutReader.join();
            stderrReader.join();
        }

        List< OmegaCodexException > exceptions = new LinkedList<>();
        Exception stdoutException = stdoutReader.getException();
        Exception stderrException = stderrReader.getException();

        if ( stdoutException != null )
        {
            exceptions.add(
                    new OmegaCodexException( "Exception occurred while reading standard output.", stdoutException ));
        }

        if ( stderrException != null )
        {
            exceptions.add(
                    new OmegaCodexException( "Exception occurred while reading standard error.", stderrException ));
        }

        if ( !exceptions.isEmpty() )
        {
            if ( exceptions.size() == 1 ) throw exceptions.getFirst();

            OmegaCodexException exception = new OmegaCodexException( "Exceptions occurred while running Python." );
            for ( Exception e : exceptions ) exception.addSuppressed( e );
            throw exception;
        }

        if ( exitCode != 0 )
        {
            StringBuilder exceptionMessage = new StringBuilder( "Error returned from Python. Exit Code: " + exitCode );
            for ( String line : stderrReader.getLines() )
            {
                exceptionMessage.append( "\n" );
                exceptionMessage.append( "Message: " );
                exceptionMessage.append( line );
            }
            throw new OmegaCodexException( exceptionMessage.toString() );
        }

        String responseString = String.join( "\n", stdoutReader.getLines() );
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseNode;
        try { responseNode = objectMapper.readTree( responseString ); }
        catch ( JacksonException e )
        {
            throw new OmegaCodexException( String.format( "Failed to deserialize response:%n%s", responseString ), e );
        }

        List< String > chunks = new LinkedList<>();
        StringBuilder chunk = new StringBuilder();
        Map< String, String > previousMetadata = null;

        for ( JsonNode element : responseNode )
        {
            String currentChunk = element.path( "content" ).asString();
            Map< String, String > currentMetadata = element.path( "metadata" ).propertyStream()
                    .filter( property -> !property.getKey().equals( "Code" ))
                    .collect( Collectors.toMap( Map.Entry::getKey, property -> property.getValue().asString() ));

            if ( currentMetadata.equals( previousMetadata ))
            {
                chunk.append( currentChunk );
            }
            else
            {
                if ( !chunk.isEmpty() ) chunks.add( chunk.toString().trim() + "\n" );
                chunk = new StringBuilder( currentChunk );
            }

            previousMetadata = currentMetadata;
        }

        if ( !chunk.isEmpty() ) chunks.add( chunk.toString().trim() + "\n" );

        return chunks;
    }
}
