package com.miku.ray.root

import org.junit.Assert.assertEquals
import org.junit.Test

class RootProxyManagerTest {
    @Test
    fun yamlScalarEscapesSingleQuotes() {
        assertEquals("'user''name'", "user'name".toSingleQuotedYamlScalar())
        assertEquals("'p@ss''word'", "p@ss'word".toSingleQuotedYamlScalar())
    }
}
