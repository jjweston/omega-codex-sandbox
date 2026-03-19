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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
class MarkdownSplitterTest
{
    @Mock private ProcessBuilderFactory mockProcessBuilderFactory;
    @Mock private ThreadedReader mockStdoutReader;
    @Mock private ThreadedReader mockStderrReader;
    @Mock private Path mockPath;
    @Mock private File mockFile;
    @Mock private ProcessBuilder mockProcessBuilder;
    @Mock private Process mockProcess;

    private MarkdownSplitter markdownSplitter;

    @BeforeEach
    void setUp()
    {
        this.markdownSplitter = new MarkdownSplitter(
                this.mockProcessBuilderFactory, this.mockStdoutReader, this.mockStderrReader );
    }

    @Test
    void testSplit_nullInputFile()
    {
        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> this.markdownSplitter.split( null ));

        assertEquals( "Input file path must not be null.", exception.getMessage() );
    }

    @Test
    void testSplit_missingInputFile()
    {
        when( this.mockPath.toFile() ).thenReturn( this.mockFile );
        when( this.mockFile.exists() ).thenReturn( false );

        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> this.markdownSplitter.split( this.mockPath ));

        assertEquals( "Input file must exist.", exception.getMessage() );
    }

    @Test
    void testSplit_pythonError() throws Exception
    {
        int exitCode = 42;

        String stderr =
                """
                This is the first line of an error message.
                This is the second line of an error message.
                """;

        this.mockProcess( exitCode, null, null, null, stderr );

        OmegaCodexException exception = assertThrowsExactly(
                OmegaCodexException.class, () -> this.markdownSplitter.split( this.mockPath ));

        String expectedMessage =
                """
                Error returned from Python. Exit Code: 42
                Message: This is the first line of an error message.
                Message: This is the second line of an error message.
                """;

        assertEquals( expectedMessage.trim(), exception.getMessage() );
    }

    @Test
    void testSplit_stdoutReaderException() throws Exception
    {
        Exception stdoutException = new Exception( "Exception Test: stdout" );

        this.mockProcess( 0, stdoutException, null, null, null );

        OmegaCodexException exception = assertThrowsExactly(
                OmegaCodexException.class, () -> this.markdownSplitter.split( this.mockPath ));

        assertEquals( "Exception occurred while reading standard output.", exception.getMessage() );
        assertEquals( stdoutException, exception.getCause() );
    }

    @Test
    void testSplit_stderrReaderException() throws Exception
    {
        Exception stderrException = new Exception( "Exception Test: stderr" );

        this.mockProcess( 0, null, stderrException, null, null );

        OmegaCodexException exception = assertThrowsExactly(
                OmegaCodexException.class, () -> this.markdownSplitter.split( this.mockPath ));

        assertEquals( "Exception occurred while reading standard error.", exception.getMessage() );
        assertEquals( stderrException, exception.getCause() );
    }

    @Test
    void testSplit_multipleReaderExceptions() throws Exception
    {
        Exception stdoutException = new Exception( "Exception Test: stdout" );
        Exception stderrException = new Exception( "Exception Test: stderr" );

        this.mockProcess( 0, stdoutException, stderrException, null, null );

        OmegaCodexException exception = assertThrowsExactly(
                OmegaCodexException.class, () -> this.markdownSplitter.split( this.mockPath ));

        assertEquals( "Exceptions occurred while running Python.", exception.getMessage() );

        Throwable[] suppressedExceptions = exception.getSuppressed();
        assertEquals( 2, suppressedExceptions.length );

        assertEquals( OmegaCodexException.class, suppressedExceptions[ 0 ].getClass() );
        assertEquals( "Exception occurred while reading standard output.", suppressedExceptions[ 0 ].getMessage() );
        assertEquals( stdoutException, suppressedExceptions[ 0 ].getCause() );

        assertEquals( OmegaCodexException.class, suppressedExceptions[ 1 ].getClass() );
        assertEquals( "Exception occurred while reading standard error.", suppressedExceptions[ 1 ].getMessage() );
        assertEquals( stderrException, suppressedExceptions[ 1 ].getCause() );
    }

    @Test
    void testSplit_emptyInputFile() throws Exception
    {
        List< String > expectedChunks = new LinkedList<>();
        this.mockProcess( 0, null, null, "[]", null );
        List< String > actualChunks = this.markdownSplitter.split( this.mockPath );
        assertThat( actualChunks ).as( "Chunks" ).isEqualTo( expectedChunks );
    }

    @Test
    void testSplit_invalidResponse() throws Exception
    {
        String stdout =
                """
                This is not valid JSON.
                """;

        List< String > expectedChunks = List.of(
                """
                # Test Markdown

                This is a test Markdown file.
                """ );

        this.mockProcess( 0, null, null, stdout, null );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> this.markdownSplitter.split( this.mockPath ));

        String expectedMessage =
                "Failed to deserialize response:" +
                System.lineSeparator() +
                "This is not valid JSON.";

        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    void testSplit_success() throws Exception
    {
        String stdout =
                """
                [
                    {
                        "content": "# Test Markdown\\n\\nThis is a test Markdown file.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown"
                        }
                    },
                    {
                        "content": "## Code Section\\n\\nThis contains code blocks.\\n\\nThe first is Java.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown",
                            "Header 2": "Code Section"
                        }
                    },
                    {
                        "content": "```java\\nSystem.out.println( \\"Hello, from Java!\\" );\\n```\\n",
                        "metadata": {
                            "Code": "java",
                            "Header 1": "Test Markdown",
                            "Header 2": "Code Section"
                        }
                    },
                    {
                        "content": "\\nThe second is Python.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown",
                            "Header 2": "Code Section"
                        }
                    },
                    {
                        "content": "```python\\nprint( \\"Hello, from Python!\\" )\\n```\\n",
                        "metadata": {
                            "Code": "python",
                            "Header 1": "Test Markdown",
                            "Header 2": "Code Section"
                        }
                    },
                    {
                        "content": "\\nThis is the end of the code section.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown",
                            "Header 2": "Code Section"
                        }
                    },
                    {
                        "content": "## Duplicate Section\\n\\nThis section tests duplicate headers.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown",
                            "Header 2": "Duplicate Section"
                        }
                    },
                    {
                        "content": "## Duplicate Section\\n\\nDuplicate headers should be combined.\\n\\n",
                        "metadata": {
                            "Header 1": "Test Markdown",
                            "Header 2": "Duplicate Section"
                        }
                    }
                ]
                """;

        List< String > expectedChunks = List.of(
                """
                # Test Markdown

                This is a test Markdown file.
                """,
                """
                ## Code Section

                This contains code blocks.

                The first is Java.

                ```java
                System.out.println( "Hello, from Java!" );
                ```

                The second is Python.

                ```python
                print( "Hello, from Python!" )
                ```

                This is the end of the code section.
                """,
                """
                ## Duplicate Section

                This section tests duplicate headers.

                ## Duplicate Section

                Duplicate headers should be combined.
                """ );

        this.mockProcess( 0, null, null, stdout, null );
        List< String > actualChunks = this.markdownSplitter.split( this.mockPath );
        assertThat( actualChunks ).as( "Chunks" ).isEqualTo( expectedChunks );
    }

    private void mockProcess( int exitCode, Exception stdoutException, Exception stderrException,
                              String stdout, String stderr ) throws Exception
    {
        when( this.mockPath.toFile() ).thenReturn( this.mockFile );
        when( this.mockFile.exists() ).thenReturn( true );
        when( this.mockProcessBuilderFactory.create( any( String[].class ))).thenReturn( mockProcessBuilder );
        when( this.mockProcessBuilder.start() ).thenReturn( this.mockProcess );
        when( this.mockProcess.waitFor() ).thenReturn( exitCode );
        when( this.mockStdoutReader.getException() ).thenReturn( stdoutException );
        when( this.mockStderrReader.getException() ).thenReturn( stderrException );
        if ( stdout != null ) when( this.mockStdoutReader.getLines() ).thenReturn( stdout.lines().toList() );
        if ( stderr != null ) when( this.mockStderrReader.getLines() ).thenReturn( stderr.lines().toList() );
    }
}
