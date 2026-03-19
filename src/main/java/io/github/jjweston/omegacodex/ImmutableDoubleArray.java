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

import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;

class ImmutableDoubleArray
{
    private final double[] array;

    ImmutableDoubleArray( double[] array )
    {
        if ( array == null ) throw new IllegalArgumentException( "Array must not be null." );

        this.array = Arrays.copyOf( array, array.length );
    }

    ImmutableDoubleArray( String string )
    {
        if ( string == null ) throw new IllegalArgumentException( "String must not be null." );

        ObjectMapper objectMapper = new ObjectMapper();
        this.array = objectMapper.readValue( string, double[].class );
    }

    public boolean equals( Object obj )
    {
        if ( ! ( obj instanceof ImmutableDoubleArray other )) return false;
        return Arrays.equals( this.array, other.array );
    }

    public int hashCode()
    {
        return Arrays.hashCode( this.array );
    }

    int length()
    {
        return array.length;
    }

    public String toString()
    {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString( this.array );
    }

    float[] toFloatArray()
    {
        float[] floatArray = new float[ this.array.length ];
        for ( int i = 0; i < this.array.length; i++ ) floatArray[ i ] = (float) this.array[ i ];
        return floatArray;
    }

    double[] getArray()
    {
        return Arrays.copyOf( this.array, this.array.length );
    }
}
