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
  val lines=mutableListOf<String>();val gradlew=project?.let{File(it,"gradlew")};val java=File(javaHome,"bin/java");val androidJar=File(sdkHome,"platforms/android-35/android.jar");val aapt2=File(sdkHome,"build-tools/35.0.0/aapt2");val gradle=gradleBin()
  fun mark(name:String,ok:Boolean,path:File?){lines+=(if(ok)"OK  " else "MISS ")+name+(path?.let{"\n  "+it.path}?:"")}
  mark("Project Gradle wrapper",gradlew?.isFile==true,gradlew);mark("Embedded JDK 17 runtime",java.isFile,java);mark("Android SDK platform 35",androidJar.isFile,androidJar);mark("Android build-tools / aapt2",aapt2.isFile,aapt2);mark("Toolchain Gradle (gradle/)",gradle!=null,gradle)
  lines+="Device ABI: "+deviceAbis();lines+="Toolchain root: "+home.path
  val toolchainReady=java.isFile&&androidJar.isFile&&aapt2.isFile
  if(!toolchainReady)lines+="\nImport a toolchain ZIP (Build tab → Import Toolchain ZIP) with: jdk/ (JDK 17), android-sdk/ (platform 35 + build-tools 35.0.0). Optional: gradle/ (Gradle distribution)."
  return ToolchainCheck(toolchainReady,lines.joinToString("\n"))
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
   ToolchainInstallResult(true,"Toolchain package installed.\n"+inspect(null).report)
  }finally{staging.takeIf{it.exists()}?.deleteRecursively()}
 }.getOrElse{ToolchainInstallResult(false,"Toolchain was not changed.\n"+(it.message?:"Unknown error"))}
 private fun normalizeRoot(staging:File):File{val kids=staging.listFiles()?.filter{it.name!="__MACOSX"}.orEmpty();return if(kids.size==1&&kids[0].isDirectory&&File(kids[0],"jdk").isDirectory)kids[0] else staging}
 private fun validatePackage(root:File){require(File(root,"jdk/bin/java").isFile){"Missing jdk/bin/java"};require(File(root,"android-sdk/platforms/android-35/android.jar").isFile){"Missing Android platform 35"};require(File(root,"android-sdk/build-tools/35.0.0/aapt2").isFile){"Missing build-tools 35.0.0/aapt2"}}
 private fun executable(file:File){file.setExecutable(true,false)}
 private fun deviceAbis():String=runCatching{android.os.Build.SUPPORTED_ABIS?.joinToString().orEmpty()}.getOrDefault("").ifBlank{"unknown / JVM test"}
 fun aapt2Path():String=File(sdkHome,"build-tools/35.0.0/aapt2").absolutePath
 /** Optional Gradle distribution inside the toolchain ZIP (gradle/bin/gradle). */
 fun gradleBin():File?=File(home,"gradle/bin/gradle").takeIf{it.isFile&&it.canExecute()}
 /** How to invoke Gradle for a project: wrapper, toolchain Gradle, or null if neither exists. */
 fun gradleCommand(project:File):String?=when{
  File(project,"gradlew").isFile->"./gradlew"
  gradleBin()!=null->"\""+gradleBin()!!.absolutePath+"\""
  else->null
 }
 fun environment():Map<String,String>{val old=System.getenv("PATH")?:"";return mapOf("JAVA_HOME" to javaHome.path,"ANDROID_HOME" to sdkHome.path,"ANDROID_SDK_ROOT" to sdkHome.path,"PATH" to (javaHome.path+"/bin:"+sdkHome.path+"/platform-tools:"+sdkHome.path+"/build-tools/35.0.0:"+old),"GRADLE_USER_HOME" to File(home,"gradle-home").path)}
}