import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.*
import org.gradle.api.file.*
import org.gradle.jvm.*
import org.gradle.language.jvm.tasks.*
import org.gradle.language.cpp.tasks.*
import org.gradle.model.*
import org.gradle.nativeplatform.*

class JNI implements Plugin<Project> {
    void apply(Project project) {
        project.apply plugin: JNIRuleSource
    }
}

// Looks like this is the only way to add custom header/lib paths from
// outside the project dir.
// https://discuss.gradle.org/t/native-why-does-gradle-ignore-value-set-for-environment-variable-lib-and-libpath/11427
class JNIDependency implements NativeDependencySet {
    final FileCollection includeRoots
    final FileCollection linkFiles
    final FileCollection runtimeFiles

    JNIDependency(Project project) {
        def javaHome = System.properties.'java.home' + '/..'
        def includes = project.files "${javaHome}/include"
        project.file("${javaHome}/include").eachDir { includes += project.files it }
        this.includeRoots = includes
        this.runtimeFiles = project.files()
        this.linkFiles = project.files()
    }
}

@Managed interface JNILibrarySpec extends NativeLibrarySpec {
    void setNativeClass(String dir)
    String getNativeClass()
    void setLibrary(String library)
    String getLibrary()
}

class JNIRuleSource extends RuleSource {

    static class JavahCompile extends Exec {}

    @Mutate void createJavahTask(ModelMap<Task> tasks, ModelMap<JNILibrarySpec> libs, @Path("binaries") ModelMap<JvmBinarySpec> javaLibs) {
        libs.each { lib ->
            def uncapitalize = { name -> name[0].toLowerCase() + name[1..-1] }
            lib.baseName = uncapitalize(lib.nativeClass.split('\\.')[-1])
            def headerDir = "src/${lib.name}/headers"
            def targets = javaLibs.findAll{ it.library.name == lib.library }
            def classpath = targets.collect{ it.classesDir }.join(File.pathSeparator)
            def taskName = "${lib.baseName}Javah"

            tasks.create(taskName, JavahCompile) { javahTask ->
                commandLine "javah", "-d", headerDir, "-classpath", classpath, lib.nativeClass

                inputs.dir targets.collect{ it.classesDir }
                outputs.dir headerDir
                targets.each{ target ->
                    target.tasks.withType(AbstractCompile) { compile ->
                        javahTask.dependsOn compile
                    }
                }
            }

            lib.binaries*.tasks*.withType(CppCompile){ nativeTask ->
                nativeTask.dependsOn taskName
            }
        }
    }

    @Mutate void createCopyLibsTask(ModelMap<Task> tasks, @Path("binaries") ModelMap<JvmBinarySpec> binaries, @Path("binaries") ModelMap<SharedLibraryBinarySpec> libs) {
        libs.findAll{ it.buildable && (it.library instanceof JNILibrarySpec) }.each{ lib ->
            binaries.findAll{ it.library.name == lib.library.library }.each{ bin ->
                def nativePackage = lib.library.nativeClass
                nativePackage = nativePackage.substring(0, nativePackage.lastIndexOf('.'))
                String taskName = "copy${lib.name.capitalize()}LibsTo${bin.library.name.capitalize()}"
                tasks.create(taskName, Copy) {
                    into "src/${lib.library.library}/resources/lib/${nativePackage}"
                    from lib.sharedLibraryFile, {
                        into "${lib.targetPlatform.name}"
                    }
                    dependsOn lib.tasks
                }
                bin.tasks.withType(ProcessResources) {
                    //Dodgy doing this here; don't think we should be mutating any parameters except the first one.
                    it.dependsOn taskName
                }
            }
        }
    }
}