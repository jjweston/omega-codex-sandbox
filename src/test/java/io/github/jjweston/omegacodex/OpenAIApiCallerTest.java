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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith( MockitoExtension.class )
public class OpenAIApiCallerTest
{
    private final String testTaskName      = "OpenAIApiCallerTest";
    private final String testApiEndpoint   = "https://example.org/v1/test";
    private final String testApiKeyVarName = "OMEGACODEX_TEST_API_KEY";

    @Mock private Environment            mockEnvironment;
    @Mock private HttpRequestBuilder     mockHttpRequestBuilder;
    @Mock private HttpClient             mockHttpClient;
    @Mock private OmegaCodexUtil         mockOmegaCodexUtil;
    @Mock private HttpResponse< String > mockHttpResponse;

    @Captor private ArgumentCaptor< String > requestBodyCaptor;

    @Test
    void testGetResponse_nullTaskName()
    {
        OpenAiApiCaller openAiApiCaller = this.createOpenAiApiCaller();
        Map< String, Object > requestMap = new HashMap<>();

        IllegalArgumentException exception = assertThrowsExactly( IllegalArgumentException.class,
                () -> openAiApiCaller.getResponse( null, this.testApiEndpoint, requestMap, null, true ));

        assertEquals( "Task name must not be null.", exception.getMessage() );
    }

    @Test
    void testGetResponse_nullApiEndpoint()
    {
        OpenAiApiCaller openAiApiCaller = this.createOpenAiApiCaller();
        Map< String, Object > requestMap = new HashMap<>();

        IllegalArgumentException exception = assertThrowsExactly( IllegalArgumentException.class,
                () -> openAiApiCaller.getResponse( this.testTaskName, null, requestMap, null, true ));

        assertEquals( "API endpoint must not be null.", exception.getMessage() );
    }

    @Test
    void testGetResponse_nullRequestMap()
    {
        OpenAiApiCaller openAiApiCaller = this.createOpenAiApiCaller();

        IllegalArgumentException exception = assertThrowsExactly( IllegalArgumentException.class,
                () -> openAiApiCaller.getResponse( this.testTaskName, this.testApiEndpoint, null, null, true ));

        assertEquals( "Request map must not be null.", exception.getMessage() );
    }

    @Test
    void testGetResponse_error_noMessage() throws Exception
    {
        OpenAiApiCaller openAiApiCaller  = this.createOpenAiApiCaller();
        Map< String, Object > requestMap = new HashMap<>();
        int statusCode                   = 500;

        String response =
                """
                {
                }
                """;

        this.mockApiCall( statusCode, response );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> openAiApiCaller.getResponse( this.testTaskName, this.testApiEndpoint, requestMap, null, true ));

        String expectedMessage = "OpenAIApiCallerTest, Error Returned, Status Code: 500";
        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    void testGetResponse_error_withMessage() throws Exception
    {
        OpenAiApiCaller openAiApiCaller  = this.createOpenAiApiCaller();
        Map< String, Object > requestMap = new HashMap<>();
        int statusCode                   = 401;

        String response =
                """
                {
                    "error":
                    {
                        "message": "Invalid API key provided.",
                        "type": "invalid_request_error",
                        "param": null,
                        "code": "invalid_api_key"
                    }
                }
                """;

        this.mockApiCall( statusCode, response );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> openAiApiCaller.getResponse( this.testTaskName, this.testApiEndpoint, requestMap, null, true ));

        String expectedMessage =
                "OpenAIApiCallerTest, Error Returned, Status Code: 401, Error Message: Invalid API key provided.";

        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    void testGetResponse_invalidResponse() throws Exception
    {
        OpenAiApiCaller openAiApiCaller = this.createOpenAiApiCaller();
        int statusCode = 402;

        Map< String, Object > requestMap = new HashMap<>();

        String responseString = "This is not valid JSON.";

        this.mockApiCall( statusCode, responseString );

        OmegaCodexException exception = assertThrowsExactly( OmegaCodexException.class,
                () -> openAiApiCaller.getResponse( this.testTaskName, this.testApiEndpoint, requestMap, null, true ));

        String expectedMessage =
                "OpenAIApiCallerTest, Failed to deserialize response. Status Code: 402, Response:" +
                System.lineSeparator() +
                "This is not valid JSON.";

        assertEquals( expectedMessage, exception.getMessage() );
    }

    @Test
    void testGetResponse_success() throws Exception
    {
        OpenAiApiCaller openAiApiCaller = this.createOpenAiApiCaller();
        int statusCode = 200;

        Map< String, String > requestMap = new HashMap<>();
        requestMap.put( "query", "What is your favorite food?" );

        String responseString =
                """
                {
                  "adjective": "frozen",
                  "noun": "yogurt"
                }
                """;

        this.mockApiCall( statusCode, responseString );

        JsonNode actualResponseNode = openAiApiCaller.getResponse(
                this.testTaskName, this.testApiEndpoint, requestMap, "Start Message", true );

        String actualRequestString = this.requestBodyCaptor.getValue();
        TypeReference< HashMap< String, String >> typeRef = new TypeReference<>() {};
        ObjectMapper objectMapper = new ObjectMapper();
        Map< String, String > actualRequestMap = objectMapper.readValue( actualRequestString, typeRef );
        assertThat( actualRequestMap ).as( "Request Map" ).containsExactlyInAnyOrderEntriesOf( requestMap );

        JsonNode expectedResponseNode = JsonNodeFactory.instance.objectNode()
                .put( "adjective", "frozen" )
                .put( "noun", "yogurt" );

        assertEquals( expectedResponseNode, actualResponseNode );

        verify( this.mockOmegaCodexUtil ).println( "OpenAIApiCallerTest, Starting, Start Message" );
    }

    private OpenAiApiCaller createOpenAiApiCaller()
    {
        TaskRunner testTaskRunner = new TaskRunner( 0, this.mockOmegaCodexUtil );

        return new OpenAiApiCaller( this.testApiKeyVarName, this.mockEnvironment, this.mockHttpRequestBuilder,
                                    this.mockHttpClient, this.mockOmegaCodexUtil, testTaskRunner );
    }

    private void mockApiCall( int statusCode, String response ) throws Exception
    {
        String testApiKey = "Test API Key";

        when( this.mockEnvironment.getString( this.testApiKeyVarName )).thenReturn( testApiKey );
        when( this.mockHttpRequestBuilder.reset() ).thenReturn( this.mockHttpRequestBuilder );
        when( this.mockHttpRequestBuilder.uri( this.testApiEndpoint )).thenReturn( this.mockHttpRequestBuilder );
        when( this.mockHttpRequestBuilder.header( "Content-Type", "application/json" ))
                .thenReturn( this.mockHttpRequestBuilder );
        when( this.mockHttpRequestBuilder.header( "Authorization", "Bearer " + testApiKey ))
                .thenReturn( this.mockHttpRequestBuilder );
        when( this.mockHttpRequestBuilder.POST( this.requestBodyCaptor.capture() ))
                .thenReturn( this.mockHttpRequestBuilder );
        when( this.mockHttpClient.< String >send( any(), any() )).thenReturn( this.mockHttpResponse );
        when( this.mockHttpResponse.statusCode() ).thenReturn( statusCode );
        when( this.mockHttpResponse.body() ).thenReturn( response );
    }
}
