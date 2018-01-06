# Copy-on-write Collections

![Current version][version-img]
![Develepment version][snap-version-img]
![Cow][cow-image]
[![MIT License][license-image]][license-url]


The collections in this library are efficient implementations of the
standard JDK collections where mutating one reference of a data
structure (add, remove, etc.) does not affect other references to the
data structure. This is done efficiently through
**[persistent][wiki-colls]** data structures and enables easy
thread-safe, versioned, and immutable programming.

```text
 _(__)_        V
'-e e -'__,--.__)
 (o_o)        )
    \. /___.  |
    ||| _)/_)/
gnv//_(/_(/_(
```

## Getting started

Add the following to your repository

**Apache Maven**

```xml
<dependency>
    <groupId>com.github.grignaak.collections</groupId>
    <artifactId>cow-collections</artifactId>
    <version>0.9.16</version>
</dependency>
```

**Gradle**

```groovy
compile 'com.github.grignaak.collections:cow-collections:0.9.16'
```

## Efficient copy-on-write through versioning

We claim to be both copy-on-write and efficient. This is not a
contradiction.

Some copy-on-write and implementations, such as JDK's
[CopyOnWriteArrayList][jdk-cowlist] and Guava's [Immutable
collections][guava-colls] copy element on every mutation; which is a
waste of time and memory.

Persistent data structures save time and memory by partially sharing
structure with the pre-mutation version of itself. For example, removing
the `nth` element from a list in one version can share the remaining
`0..n-1` elements with the prior version.

Other persistent collection implementations, such as
[pcollections][p-colls] and [clojure's][clojure-colls] return a new
version for *every* mutation; which we think is both confusing and
wasteful. Confusing because the developer must remember to re-assign
the variable after every mutation and wasteful because it doesn't allow
efficient bulk updates. Clojure improves on this with its [transient
collections][clojure-trans], allowing the data structure to switch modes
for bulk-update operations, then switch back for PCollections-style
immutable mode.

Our collections are even simpler. Our collections are by default
mutable, and can be used just like their JDK cohorts. *They can be final
fields* and final variables because they don't need to be reassigned on
updates.

The developer decides when it is time to safely share the collection by
*forking** the data structure. `fork()` returns a new instance of the
collection---sharing structure with the original---where both the new
and original instances can efficiently mutate its own copy. Or the fork
can safely be sent to another thread for reading while the original can
still be updated in the orignal thread.

```text
          (    )
           (oo)
  )\.-----/(O O)
 # ;       / u
   (  .   |} )
    |/ `.;|/;
    "     " "
```

## Example

```java
CowList<String> beatles = new CowList<>()

beatles.add( "john" );
beatles.add( "paul" );
beatles.add( "george" );
beatles.add( "ringo" );

CowList<String> famous = beatles.fork();

beatles.add( "pete" );

famous.add( "peter" );
famous.add( "paul" );
famous.add( "mary" );

System.out.println("beatles: " + beatles);
System.out.println("famous: " + famous);

// [standard out]
//
// beatles: [john, paul, george, ringo, pete]
// famous: [john, paul, george, ringo, peter, paul, mary]
```

## Related Projects and Inspiration

The goal of this project is to strike a balance between performance,
ease-of-use and ease-of-maintenance. We've learned a lot from what has
come before.

* The old [PCollections][p-colls] library.

  PCollections was the de facto standard on the jdk for many years. We
  don't quite like the implementations and API, which led us to make
  this library, but we used it for many projects and it served for a
  long time.

* The [Clojure collections][clojure-colls].

  Clojure's implementations improved a lot on PCollections, bringing
  more efficient lists and hash maps.

  We originally copied Clojure's API split of *immutable* interfaces and
  corresponding [*transient* interfaces][clojure-trans]. The transient
  interfaces gave a way for fast, gc-friendly batch input. But early
  feedback moved us toward the current *fork* API. This made the API
  much simpler, while remaining fast and gc-friendly.

* [clj-ds][clj-ds] is an attempt to extract Clojure's collections into
  their own library. Unfortunately, the collections are highly* coupled
  to the clojure runtime, so large swaths of that are also brought
  along.

* Google [Guava's Immutable collections][guava-colls]. This library
  provides needed type safety, but the shear amount of data copying has
  turned us off towards it.



```text
          .        .
          \'.____.'/
         __'-.  .-'__                         .--.
         '_i:'oo':i_'---...____...----i"""-.-'.-"\\
           /._  _.\       :       /   '._   ;/    ;'-._
          (  o  o  )       '-.__.'       '. '.     '-."
           '-.__.-' _.--.                 '-.:
            : '-'  /     ;   _..--,  /       ;
            :      '-._.-'  ;     ; :       :
             :  `      .'    '-._.' :      /
              \  :    /    ____....--\    :
               '._\  :"""""    '.     !.   :
                : |: :           'www'| \ '|
                | || |              : |  | :
                | || |             .' !  | |
               .' !| |            /__I   | |
              /__I.' !                  .' !
                 /__I                  /__I   fsc
```

[wiki-colls]: https://en.wikipedia.org/wiki/Persistent_data_structure
[p-colls]: https://pcollections.org/
[clojure-colls]: https://clojure.org/reference/data_structures
[clojure-trans]: https://clojure.org/reference/transients
[guava-colls]: https://github.com/google/guava/wiki/ImmutableCollectionsExplained
[clj-ds]: https://github.com/krukow/clj-ds

[license-image]:          http://img.shields.io/badge/license-MIT-blue.svg
[license-url]:            LICENSE
[cow-image]:              https://img.shields.io/badge/Cow-🐄-eeeeee.svg
[version-img]:            https://img.shields.io/badge/Version-0.9.16_(Beta)-yellow.svg
[snap-version-img]:       https://img.shields.io/badge/Development-0.10.0--SNAPSHOT-yellow.svg