import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.file.*
import org.gradle.jvm.*
import org.gradle.language.jvm.tasks.*
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
    void setClasses(List<String> classes)
    List<String> getClasses()
    void setPkg(String dir)
    String getPkg()
    void setLibrary(String library)
    String getLibrary()
}

class JNIRuleSource extends RuleSource {

    class JavahCompile extends Exec {}

    @Mutate void createJavahTask(ModelMap<Task> tasks, ModelMap<JNILibrarySpec> libs, @Path("binaries") ModelMap<JvmBinarySpec> binaries) {
        libs.each { lib ->
            def headerDir = "src/${lib.baseName}/headers"
            def targets = binaries.findAll{ it.library.name == lib.library }
            def classpath = targets.collect{ it.classesDir }.join(File.pathSeparator)

            tasks.create("${lib.baseName}Javah", JavahCompile) { task ->
                commandLine(["javah", "-d", headerDir, "-classpath", classpath] + lib.classes.collect{ "${lib.pkg}.${it}" })

                inputs.dir targets.collect{ it.classesDir }
                outputs.dir headerDir
                targets.each{ 
                    it.tasks.withType(PlatformJavaCompile) { 
                        task.dependsOn it
                    }
                }
            }
        }
    }
    
    @Mutate void createCopyLibsTask(ModelMap<Task> tasks, @Path("binaries") ModelMap<JvmBinarySpec> binaries, @Path("binaries") ModelMap<SharedLibraryBinarySpec> libs) {
        libs.findAll{ it.buildable && (it.library instanceof JNILibrarySpec) }.each{ lib ->
            binaries.findAll{ it.library.name == lib.library.library }.each{ bin ->
                String task = "copy${lib.name.capitalize()}LibsTo${bin.library.name.capitalize()}"
                tasks.create(task, Copy) {
                    into "src/main/resources/lib/${lib.library.pkg}"
                    from lib.sharedLibraryFile, {
                        into "${lib.targetPlatform.name}"
                    }
                    dependsOn lib.tasks
                }
                bin.tasks.withType(ProcessResources) {
                    //Dodgy doing this here; don't think we should be mutating any parameters except the first one.
                    it.dependsOn task
                }
            }
        }
    }
}