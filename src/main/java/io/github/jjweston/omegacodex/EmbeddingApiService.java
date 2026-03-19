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

import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

class EmbeddingApiService
{
    private final String          taskName;
    private final String          apiEndpoint;
    private final String          model;
    private final int             inputLimit;
    private final boolean         debug;
    private final OpenAiApiCaller openAiApiCaller;
    private final OmegaCodexUtil  omegaCodexUtil;

    EmbeddingApiService( OpenAiApiCaller openAiApiCaller )
    {
        this( openAiApiCaller, new OmegaCodexUtil() );
    }

    EmbeddingApiService( OpenAiApiCaller openAiApiCaller, OmegaCodexUtil omegaCodexUtil )
    {
        if ( openAiApiCaller == null ) throw new IllegalArgumentException( "OpenAI API caller must not be null." );

        this.taskName        = "Embedding API Call";
        this.apiEndpoint     = "https://api.openai.com/v1/embeddings";
        this.model           = "text-embedding-3-small";
        this.inputLimit      = 20_000;
        this.debug           = false;
        this.openAiApiCaller = openAiApiCaller;
        this.omegaCodexUtil  = omegaCodexUtil;
    }

    ImmutableDoubleArray getEmbeddingVector( String input )
    {
        if ( input == null ) throw new IllegalArgumentException( "Input must not be null." );
        if ( input.isEmpty() ) throw new IllegalArgumentException( "Input must not be empty." );

        if ( input.length() > this.inputLimit )
        {
            String message = String.format(
                    "Input length must not be greater than %,d. Actual Length: %,d", this.inputLimit, input.length() );
            throw new IllegalArgumentException( message );
        }

        String startMessage = String.format( "Input Length: %,d", input.length() );

        Map< String, String > requestMap = new HashMap<>();
        requestMap.put( "model", this.model );
        requestMap.put( "input", input );

        JsonNode responseNode = this.openAiApiCaller.getResponse(
                this.taskName, this.apiEndpoint, requestMap, startMessage, this.debug );

        int totalTokens = responseNode.path( "usage" ).path( "total_tokens" ).intValue();
        this.omegaCodexUtil.println( String.format( "%s, Tokens: %,d", this.taskName, totalTokens ));

        JsonNode embeddingNode = responseNode.path( "data" ).get( 0 ).path( "embedding" );
        double[] vector = embeddingNode.valueStream().mapToDouble( JsonNode::asDouble ).toArray();
        return new ImmutableDoubleArray( vector );
    }
}
