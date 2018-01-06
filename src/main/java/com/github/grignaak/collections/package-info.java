/**
 * <h1>Copy-on-write collections.</h1>
 *
 * <p>The collections in this package are <a href="https://en.wikipedia.org/wiki/Persistent_data_structure">persistent</a>
 * (a.k.a immutable). Mutating the collection gives a new version of the collection, leaving the old instance intact.
 * Most implementations share data between the new and old versions of the data-structure to reduce overhead from memory,
 * copying, and garbage collection.</p>
 */
package com.github.grignaak.collections;