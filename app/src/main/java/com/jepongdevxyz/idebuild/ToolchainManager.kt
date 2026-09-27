package com.jepongdevxyz.idebuild
import java.io.File
data class ToolchainCheck(val ready:Boolean,val report:String)
class ToolchainManager(private val filesDir:File){
 private val home=File(filesDir,"toolchain")
 private val javaHome=File(home,"jdk")
 private val sdkHome=File(home,"android-sdk")
 fun inspect(project:File?):ToolchainCheck{
  val lines=mutableListOf<String>();val gradlew=project?.let{File(it,"gradlew")};val java=File(javaHome,"bin/java");val androidJar=File(sdkHome,"platforms/android-35/android.jar");val aapt2=File(sdkHome,"build-tools/35.0.0/aapt2")
  fun mark(name:String,ok:Boolean,path:File?){lines+=(if(ok)"OK  " else "MISS ")+name+(path?.let{"\n  "+it.path}?:"")}
  mark("Project Gradle wrapper",gradlew?.isFile==true,gradlew);mark("Embedded JDK 17 runtime",java.isFile,java);mark("Android SDK platform 35",androidJar.isFile,androidJar);mark("Android build-tools / aapt2",aapt2.isFile,aapt2)
  lines+="Device ABI: "+deviceAbis();lines+="Toolchain root: "+home.path;lines+="Compatible Android-host JDK/SDK binaries must be provisioned before local Gradle builds can run."
  return ToolchainCheck(gradlew?.isFile==true&&java.isFile&&androidJar.isFile&&aapt2.isFile,lines.joinToString("\n"))
 }
 private fun deviceAbis():String=runCatching{android.os.Build.SUPPORTED_ABIS?.joinToString().orEmpty()}.getOrDefault("").ifBlank{"unknown / JVM test"}
 fun environment():Map<String,String>{val old=System.getenv("PATH")?:"";return mapOf("JAVA_HOME" to javaHome.path,"ANDROID_HOME" to sdkHome.path,"ANDROID_SDK_ROOT" to sdkHome.path,"PATH" to (javaHome.path+"/bin:"+sdkHome.path+"/platform-tools:"+sdkHome.path+"/build-tools/35.0.0:"+old),"GRADLE_USER_HOME" to File(home,"gradle-home").path)}
}