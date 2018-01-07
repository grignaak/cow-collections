/**
 * <h1>Copy-on-write collections.</h1>
 *
 * <p>The collections in this library are efficient implementations of the standard JDK collections where mutating one
 * reference of a data structure (add, remove, etc.) does not affect other references to the data structure. This is
 * done efficiently through persistent data structures and enables easy thread-safe, versioned, and immutable
 * programming.</p>
 *
 * <p>Persistent data structures save time and memory over other immutable data structures by partially sharing
 * structure with the pre-mutation version of itself. For example, removing the `nth` element from a list in one version
 * can share the remaining `0..n-1` elements with the prior version.</p>
 *
 * <p>The collections are by default mutable, and can be used just like their JDK counterparts. *They can be final
 * fields and final variables* because they don't need to be reassigned on updates.</p>
 *
 * <p>The developer decides when it is time to safely share the collection by forking* the data structure. {@linkplain
 * com.github.grignaak.collections.Forkable#fork() fork()} returns a new instance of the collection&mdash;sharing
 * structure with the original&mdash;where both the new and original instances can efficiently mutate its own copy. Or
 * the fork can safely be sent to another thread for reading while the original can still be updated in the original
 * thread.</p>
 */
package com.github.grignaak.collections;