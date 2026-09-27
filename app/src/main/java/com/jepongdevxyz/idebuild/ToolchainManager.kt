package com.jepongdevxyz.idebuild
import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

data class ToolchainCheck(val ready:Boolean,val report:String)
data class ToolchainInstallResult(val success:Boolean,val report:String)

class ToolchainManager(private val filesDir:File){
 private val home=File(filesDir,"toolchain")
 private val javaHome=File(home,"jdk")
 private val sdkHome=File(home,"android-sdk")
 fun inspect(project:File?):ToolchainCheck{
  val lines=mutableListOf<String>();val gradlew=project?.let{File(it,"gradlew")};val java=File(javaHome,"bin/java");val androidJar=File(sdkHome,"platforms/android-35/android.jar");val aapt2=File(sdkHome,"build-tools/35.0.0/aapt2")
  fun mark(name:String,ok:Boolean,path:File?){lines+=(if(ok)"OK  " else "MISS ")+name+(path?.let{"\n  "+it.path}?:"")}
  val javaOk=java.isFile&&java.canExecute();val aaptOk=aapt2.isFile&&aapt2.canExecute();mark("Project Gradle wrapper",gradlew?.isFile==true,gradlew);mark("Embedded JDK 17 runtime",javaOk,java);mark("Android SDK platform 35",androidJar.isFile,androidJar);mark("Android build-tools / aapt2",aaptOk,aapt2)
  lines+="Device ABI: "+deviceAbis();lines+="Toolchain root: "+home.path
  return ToolchainCheck(gradlew?.isFile==true&&javaOk&&androidJar.isFile&&aaptOk,lines.joinToString("\n"))
 }
 fun install(resolver:ContentResolver,uri:Uri):ToolchainInstallResult=runCatching{
  val staging=File(filesDir,"toolchain-staging-"+System.currentTimeMillis()).apply{mkdirs()}
  try{
   resolver.openInputStream(uri)?.use{input->ZipInputStream(input).use{zip->var entry=zip.nextEntry;var count=0;var total=0L
    while(entry!=null){require(++count<=20000){"Too many ZIP entries"};val out=File(staging,entry.name).canonicalFile;require(out.path.startsWith(staging.canonicalPath+File.separator)){"Unsafe ZIP path"};if(entry.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();out.outputStream().use{o->val buf=ByteArray(64*1024);var n=zip.read(buf);while(n>0){total+=n;require(total<=1500L*1024*1024){"Toolchain package exceeds 1.5 GB"};o.write(buf,0,n);n=zip.read(buf)}}};zip.closeEntry();entry=zip.nextEntry}
   }}?:error("Unable to open selected package")
   val root=normalizeRoot(staging);validatePackage(root)
   val backup=File(filesDir,"toolchain-backup");backup.deleteRecursively();if(home.exists())require(home.renameTo(backup)){"Could not prepare existing toolchain"}
   require(root.renameTo(home)){"Could not activate toolchain"};backup.deleteRecursively()
   executable(File(javaHome,"bin/java"));executable(File(sdkHome,"build-tools/35.0.0/aapt2"));File(sdkHome,"platform-tools/adb").takeIf{it.exists()}?.let(::executable)
   val runtime=runtimeProbe();require(runtime.first){"Toolchain files installed but Android runtime probe failed:\n"+runtime.second}
   ToolchainInstallResult(true,"Toolchain package installed and executable.\n"+runtime.second+"\n"+inspect(null).report)
  }finally{staging.takeIf{it.exists()}?.deleteRecursively()}
 }.getOrElse{ToolchainInstallResult(false,"Toolchain was not changed.\n"+(it.message?:"Unknown error"))}
 private fun normalizeRoot(staging:File):File{val kids=staging.listFiles()?.filter{it.name!="__MACOSX"}.orEmpty();return if(kids.size==1&&kids[0].isDirectory&&File(kids[0],"jdk").isDirectory)kids[0] else staging}
 private fun validatePackage(root:File){require(File(root,"jdk/bin/java").isFile){"Missing jdk/bin/java"};require(File(root,"android-sdk/platforms/android-35/android.jar").isFile){"Missing Android platform 35"};require(File(root,"android-sdk/build-tools/35.0.0/aapt2").isFile){"Missing build-tools 35.0.0/aapt2"}}
 private fun executable(file:File){require(file.setExecutable(true,false)||file.canExecute()){"Could not mark executable: "+file.path}}
 private fun runtimeProbe():Pair<Boolean,String>{fun run(bin:File,vararg args:String):Pair<Int,String>=runCatching{val p=ProcessBuilder(listOf(bin.absolutePath)+args).redirectErrorStream(true).start();val out=p.inputStream.bufferedReader().readText();p.waitFor() to out}.getOrElse{-1 to ("Execution error: "+it.message)};val j=run(File(javaHome,"bin/java"),"-version");val a=run(File(sdkHome,"build-tools/35.0.0/aapt2"),"version");return (j.first==0&&a.first==0) to ("java -version exit="+j.first+"\n"+j.second.trim()+"\naapt2 version exit="+a.first+"\n"+a.second.trim())}
 private fun deviceAbis():String=runCatching{android.os.Build.SUPPORTED_ABIS?.joinToString().orEmpty()}.getOrDefault("").ifBlank{"unknown / JVM test"}
 fun aapt2Path():String=File(sdkHome,"build-tools/35.0.0/aapt2").absolutePath
 fun environment():Map<String,String>{val old=System.getenv("PATH")?:"";return mapOf("JAVA_HOME" to javaHome.path,"ANDROID_HOME" to sdkHome.path,"ANDROID_SDK_ROOT" to sdkHome.path,"PATH" to (javaHome.path+"/bin:"+sdkHome.path+"/platform-tools:"+sdkHome.path+"/build-tools/35.0.0:"+old),"GRADLE_USER_HOME" to File(home,"gradle-home").path)}
}