The goal of this project is to produce a simple system for building
run-anywhere jar, bundling jni binaries for multiple operating systems
and architectures.

This plugin adds JNILibrarySpec, a subclass of NativeLibrarySpec
that will take the component name of a JVM component in your build,
and the package and name of classes within that component that have
`native` methods, and will generate (or update) javah headers, then
compile your native classes including those headers, then copy the
native libraries back into the jvm component's build path, in an 
architecture-dependant subdirectory, where they can easily be loaded by 
[ArchLoader](https://www.github.com/bgorven/Loader).

See it in action at https://github.com/bgorven/Hello/blob/master/build.gradle