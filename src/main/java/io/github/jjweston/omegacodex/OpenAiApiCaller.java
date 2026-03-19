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

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class OpenAiApiCaller
{
    private final String             apiKeyVarName;
    private final Environment        environment;
    private final HttpRequestBuilder httpRequestBuilder;
    private final HttpClient         httpClient;
    private final OmegaCodexUtil     omegaCodexUtil;
    private final TaskRunner         taskRunner;

    OpenAiApiCaller()
    {
        this( "OMEGACODEX_OPENAI_API_KEY",
              new Environment(),
              new HttpRequestBuilder(),
              HttpClient.newHttpClient(),
              new OmegaCodexUtil(),
              new TaskRunner( 200 ));
    }

    OpenAiApiCaller( String apiKeyVarName, Environment environment, HttpRequestBuilder httpRequestBuilder,
                     HttpClient httpClient, OmegaCodexUtil omegaCodexUtil, TaskRunner taskRunner )
    {
        this.apiKeyVarName      = apiKeyVarName;
        this.environment        = environment;
        this.httpRequestBuilder = httpRequestBuilder;
        this.httpClient         = httpClient;
        this.omegaCodexUtil     = omegaCodexUtil;
        this.taskRunner         = taskRunner;
    }

    JsonNode getResponse(
            String taskName, String apiEndpoint, Map< String, ? > requestMap, String startMessage, boolean debug )
    {
        if ( taskName == null ) throw new IllegalArgumentException( "Task name must not be null." );
        if ( apiEndpoint == null ) throw new IllegalArgumentException( "API endpoint must not be null." );
        if ( requestMap == null ) throw new IllegalArgumentException( "Request map must not be null." );

        ObjectMapper objectMapper = new ObjectMapper();

        String requestString = objectMapper.writeValueAsString( requestMap );

        if ( debug )
        {
            String debugRequestString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString( requestMap );

            omegaCodexUtil.println( "----------------------------------------------------------------------" );
            omegaCodexUtil.println( "Request:" );
            omegaCodexUtil.println( debugRequestString );
            omegaCodexUtil.println( "----------------------------------------------------------------------" );
        }

        HttpRequest request = this.httpRequestBuilder.reset()
                .uri( apiEndpoint )
                .header( "Content-Type", "application/json" )
                .header( "Authorization", "Bearer " + this.environment.getString( this.apiKeyVarName ))
                .POST( requestString )
                .build();

        HttpResponse< String > response = this.taskRunner.get( taskName, startMessage, () ->
                this.httpClient.send( request, HttpResponse.BodyHandlers.ofString() ));

        int statusCode = response.statusCode();
        String responseString = response.body();

        if ( debug )
        {
            omegaCodexUtil.println( "----------------------------------------------------------------------" );
            omegaCodexUtil.println( "Status Code: " + statusCode );
            omegaCodexUtil.println( "Response:" );
            omegaCodexUtil.println( responseString );
            omegaCodexUtil.println( "----------------------------------------------------------------------" );
        }

        JsonNode responseNode;
        try { responseNode = objectMapper.readTree( responseString ); }
        catch ( JacksonException e )
        {
            throw new OmegaCodexException(
                    String.format( "%s, Failed to deserialize response. Status Code: %d, Response:%n%s",
                                   taskName, statusCode, responseString ), e );
        }

        if ( statusCode != 200 )
        {
            String errorMessage = responseNode.path( "error" ).path( "message" ).asString();
            String exceptionMessage = taskName + ", Error Returned, Status Code: " + statusCode;
            if ( !errorMessage.isEmpty() ) exceptionMessage += ", Error Message: " + errorMessage;
            throw new OmegaCodexException( exceptionMessage );
        }

        return responseNode;
    }
}
