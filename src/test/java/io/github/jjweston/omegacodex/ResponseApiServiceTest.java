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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
public class ResponseApiServiceTest
{
    @Mock private OpenAiApiCaller       mockOpenAiApiCaller;
    @Mock private EmbeddingCacheService mockEmbeddingCacheService;
    @Mock private EmbeddingService      mockEmbeddingService;
    @Mock private QdrantService         mockQdrantService;
    @Mock private OmegaCodexUtil        mockOmegaCodexUtil;

    @Captor private ArgumentCaptor< Map< String, Object >> requestMapCaptor;

    @Test
    void testConstructor_nullEmbeddingCacheService()
    {
        @SuppressWarnings( "DataFlowIssue" )
        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> new ResponseApiService(
                        null, this.mockEmbeddingService, this.mockQdrantService,
                        this.mockOpenAiApiCaller, this.mockOmegaCodexUtil ));

        assertEquals( "Embedding cache service must not be null.", exception.getMessage() );
    }

    @Test
    void testConstructor_nullEmbeddingService()
    {
        @SuppressWarnings( "DataFlowIssue" )
        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> new ResponseApiService(
                        this.mockEmbeddingCacheService, null, this.mockQdrantService,
                        this.mockOpenAiApiCaller, this.mockOmegaCodexUtil ));

        assertEquals( "Embedding service must not be null.", exception.getMessage() );
    }

    @Test
    void testConstructor_nullQdrantService()
    {
        @SuppressWarnings( "DataFlowIssue" )
        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> new ResponseApiService(
                        this.mockEmbeddingCacheService, this.mockEmbeddingService, null,
                        this.mockOpenAiApiCaller, this.mockOmegaCodexUtil ));

        assertEquals( "Qdrant service must not be null.", exception.getMessage() );
    }

    @Test
    void testConstructor_nullOpenAiCaller()
    {
        @SuppressWarnings( "DataFlowIssue" )
        IllegalArgumentException exception = assertThrowsExactly(
                IllegalArgumentException.class, () -> new ResponseApiService(
                        this.mockEmbeddingCacheService, this.mockEmbeddingService, this.mockQdrantService,
                        null, this.mockOmegaCodexUtil ));

        assertEquals( "OpenAI API caller must not be null.", exception.getMessage() );
    }

    @Test
    void getResponse_nullQuery()
    {
        ResponseApiService responseApiService = this.getResponseApiService();

        IllegalArgumentException exception = assertThrowsExactly( IllegalArgumentException.class,
                () -> responseApiService.getResponse( null ));

        assertEquals( "Query must not be null.", exception.getMessage() );
    }

    @Test
    @SuppressWarnings( "ExtractMethodRecommender" )
    void getResponse_success() throws Exception
    {
        ResponseApiService responseApiService = this.getResponseApiService();

        String queryString = "What is your quest?";
        ImmutableDoubleArray queryVector = new ImmutableDoubleArray( new double[] { 0.5, 0.4, 0.3, 0.2, 0.1 } );
        Embedding queryEmbedding = new Embedding( 42, queryVector );
        SearchResult searchResult = new SearchResult( 7, 0.5f );
        List< SearchResult > searchResults = List.of( searchResult );
        String searchResultInput = "Quest Objective: Holy Grail";
        String expectedResponse = "To seek the Holy Grail!";

        String responseString = String.format(
                """
                {
                  "output":
                  [
                    {
                      "type": "message",
                      "content":
                      [
                        {
                          "text": "%s"
                        }
                      ],
                      "role": "assistant"
                    }
                  ],
                  "usage":
                  {
                    "input_tokens": 2000,
                    "output_tokens": 1000,
                    "total_tokens": 3000
                  }
                }
                """, expectedResponse );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseNode = objectMapper.readTree( responseString );

        when( this.mockEmbeddingService.getEmbedding( queryString )).thenReturn( queryEmbedding );
        when( this.mockQdrantService.search( queryVector )).thenReturn( searchResults );
        when( this.mockEmbeddingCacheService.getInput( searchResult.id() )).thenReturn( searchResultInput );
        when( this.mockOpenAiApiCaller
                .getResponse( any(), any(), this.requestMapCaptor.capture(), any(), anyBoolean() ))
                .thenReturn( responseNode );

        String actualResponse = responseApiService.getResponse( queryString );

        assertEquals( expectedResponse, actualResponse );

        verify( this.mockOmegaCodexUtil ).println(
                "Response API Call, Input Tokens: 2,000, Output Tokens: 1,000, Total Tokens: 3,000" );

        Map< String, Object > requestMap = this.requestMapCaptor.getValue();
        @SuppressWarnings( "unchecked" ) List< ObjectNode > input = (List< ObjectNode >) requestMap.get( "input" );
        String userMessage = input.get( 1 ).get( "content" ).asText();
        assertTrue( userMessage.contains( searchResultInput ), "Context not found." );
        assertTrue( userMessage.contains( queryString ), "Query not found." );
    }

    @Test
    void getResponseMessage_noMessage() throws Exception
    {
        ResponseApiService responseApiService = this.getResponseApiService();

        String queryString = "What is your quest?";
        ImmutableDoubleArray queryVector = new ImmutableDoubleArray( new double[] { 0.5, 0.4, 0.3, 0.2, 0.1 } );
        Embedding queryEmbedding = new Embedding( 42, queryVector );
        List< SearchResult > searchResults = new LinkedList<>();

        String responseString =
                """
                {
                  "output":
                  [
                  ],
                  "usage":
                  {
                    "input_tokens": 2000,
                    "output_tokens": 1000,
                    "total_tokens": 3000
                  }
                }
                """;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseNode = objectMapper.readTree( responseString );

        when( this.mockEmbeddingService.getEmbedding( queryString )).thenReturn( queryEmbedding );
        when( this.mockQdrantService.search( queryVector )).thenReturn( searchResults );
        when( this.mockOpenAiApiCaller.getResponse( any(), any(), any(), any(), anyBoolean() ))
                .thenReturn( responseNode );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> responseApiService.getResponse( queryString ));

        String expectedMessage =
                "Failed to find response message:" +
                System.lineSeparator() +
                responseNode.path( "output" ).toPrettyString();

        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    @SuppressWarnings( "ExtractMethodRecommender" )
    void getResponseMessage_multipleMessages() throws Exception
    {
        ResponseApiService responseApiService = this.getResponseApiService();

        String queryString = "What is your quest?";
        ImmutableDoubleArray queryVector = new ImmutableDoubleArray( new double[] { 0.5, 0.4, 0.3, 0.2, 0.1 } );
        Embedding queryEmbedding = new Embedding( 42, queryVector );
        List< SearchResult > searchResults = new LinkedList<>();

        String responseString =
                """
                {
                  "output":
                  [
                    {
                      "type": "message",
                      "content":
                      [
                        {
                          "text": "First Message"
                        }
                      ],
                      "role": "assistant"
                    },
                    {
                      "type": "message",
                      "content":
                      [
                        {
                          "text": "Second Message"
                        }
                      ],
                      "role": "assistant"
                    }
                  ],
                  "usage":
                  {
                    "input_tokens": 2000,
                    "output_tokens": 1000,
                    "total_tokens": 3000
                  }
                }
                """;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseNode = objectMapper.readTree( responseString );

        when( this.mockEmbeddingService.getEmbedding( queryString )).thenReturn( queryEmbedding );
        when( this.mockQdrantService.search( queryVector )).thenReturn( searchResults );
        when( this.mockOpenAiApiCaller.getResponse( any(), any(), any(), any(), anyBoolean() ))
                .thenReturn( responseNode );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> responseApiService.getResponse( queryString ));

        String expectedMessage =
                "Found more than one response message:" +
                System.lineSeparator() +
                responseNode.path( "output" ).toPrettyString();

        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    @SuppressWarnings( "ExtractMethodRecommender" )
    void getResponseMessage_multipleContentElements() throws Exception
    {
        ResponseApiService responseApiService = this.getResponseApiService();

        String queryString = "What is your quest?";
        ImmutableDoubleArray queryVector = new ImmutableDoubleArray( new double[] { 0.5, 0.4, 0.3, 0.2, 0.1 } );
        Embedding queryEmbedding = new Embedding( 42, queryVector );
        List< SearchResult > searchResults = new LinkedList<>();

        ArrayNode contentNode = JsonNodeFactory.instance.arrayNode();
        for ( int i = 0; i < 1_024; i++ ) contentNode.add( JsonNodeFactory.instance.objectNode().put( "text", "foo" ));

        String responseString = String.format(
                """
                {
                  "output":
                  [
                    {
                      "type": "message",
                      "content": %s,
                      "role": "assistant"
                    }
                  ],
                  "usage":
                  {
                    "input_tokens": 2000,
                    "output_tokens": 1000,
                    "total_tokens": 3000
                  }
                }
                """, contentNode );

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseNode = objectMapper.readTree( responseString );

        when( this.mockEmbeddingService.getEmbedding( queryString )).thenReturn( queryEmbedding );
        when( this.mockQdrantService.search( queryVector )).thenReturn( searchResults );
        when( this.mockOpenAiApiCaller.getResponse( any(), any(), any(), any(), anyBoolean() ))
                .thenReturn( responseNode );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> responseApiService.getResponse( queryString ));

        String expectedMessage =
                "Expected 1 content element, but received 1,024:" +
                System.lineSeparator() +
                responseNode.path( "output" ).toPrettyString();

        assertEquals( expectedMessage, exception.getMessage() );
    }

    private ResponseApiService getResponseApiService()
    {
        return new ResponseApiService(
                this.mockEmbeddingCacheService, this.mockEmbeddingService, this.mockQdrantService,
                this.mockOpenAiApiCaller, this.mockOmegaCodexUtil );
    }
}
