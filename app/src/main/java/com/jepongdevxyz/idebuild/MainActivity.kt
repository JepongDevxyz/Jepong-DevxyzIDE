package com.jepongdevxyz.idebuild
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.jepongdevxyz.idebuild.databinding.ActivityMainBinding
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity:AppCompatActivity(){
 private lateinit var b:ActivityMainBinding; private var projectRoot:File?=null; private var currentFile:File?=null; private var visibleFiles=listOf<File>()
 private val prefs by lazy{getSharedPreferences("devxyzide",MODE_PRIVATE)}
 private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importProject(u)}
 override fun onCreate(s:Bundle?){super.onCreate(s);b=ActivityMainBinding.inflate(layoutInflater);setContentView(b.root);restoreWorkspace()
  b.importBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))};b.fileList.setOnItemClickListener{_,_,p,_->openFile(visibleFiles[p])}
  b.saveBtn.setOnClickListener{saveCurrent()};b.buildBtn.setOnClickListener{buildAndRun()};b.toolsBtn.setOnClickListener{showTools()};b.searchBtn.setOnClickListener{showSearch()};b.filesBtn.setOnClickListener{refreshExplorer()}
 }
 private fun restoreWorkspace(){prefs.getString("root",null)?.let(::File)?.takeIf{it.isDirectory}?.let{projectRoot=it;refreshExplorer()}}
 private fun importProject(uri:Uri){runCatching{val root=File(filesDir,"workspace/project-"+System.currentTimeMillis()).apply{mkdirs()};contentResolver.openInputStream(uri)!!.use{input->ZipInputStream(input).use{z->var e=z.nextEntry;while(e!=null){val out=File(root,e.name).canonicalFile;require(out.path.startsWith(root.canonicalPath+File.separator)||out==root){"Unsafe ZIP path"};if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();out.outputStream().use{z.copyTo(it)}};z.closeEntry();e=z.nextEntry}}};selectRoot(root);toast("Project imported")}.onFailure{toast("Import failed: "+it.message)}}
 private fun selectRoot(r:File){projectRoot=r;prefs.edit().putString("root",r.path).apply();refreshExplorer()}
 private fun refreshExplorer(){val r=projectRoot?:return;visibleFiles=r.walkTopDown().filter{it.isFile}.sortedBy{it.relativeTo(r).path}.toList();b.fileList.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,visibleFiles.map{it.relativeTo(r).path})}
 private fun openFile(f:File){if(f.length()>2_000_000)return toast("File too large");currentFile=f;b.tabTitle.text=f.name;b.editor.setText(runCatching{f.readText()}.getOrElse{"Cannot open as text"})}
 private fun saveCurrent(){val f=currentFile?:return toast("No file open");runCatching{f.writeText(b.editor.text.toString())}.onSuccess{toast("Saved "+f.name)}.onFailure{toast("Save failed: "+it.message)}}
 private fun newProject(){val input=EditText(this).apply{hint="MyAwesomeApp"};AlertDialog.Builder(this).setTitle("New Android Project").setView(input).setPositiveButton("Create"){_,_->createTemplate(input.text.toString().ifBlank{"MyAwesomeApp"})}.setNegativeButton("Cancel",null).show()}
 private fun createTemplate(name:String){runCatching{val safe=name.replace(Regex("[^A-Za-z0-9_]"),"").ifBlank{"MyAwesomeApp"};val r=File(filesDir,"workspace/$safe").apply{mkdirs()};File(r,"settings.gradle.kts").writeText("pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name=\"$safe\"\ninclude(\":app\")");File(r,"build.gradle.kts").writeText("plugins { id(\"com.android.application\") version \"8.8.2\" apply false; id(\"org.jetbrains.kotlin.android\") version \"2.1.10\" apply false }");val app=File(r,"app").apply{mkdirs()};File(app,"build.gradle.kts").writeText("plugins { id(\"com.android.application\"); id(\"org.jetbrains.kotlin.android\") }\nandroid { namespace=\"com.example.app\"; compileSdk=35; defaultConfig { applicationId=\"com.example.app\"; minSdk=26; targetSdk=35; versionCode=1; versionName=\"1.0\" } }");val src=File(app,"src/main/java/com/example/app").apply{mkdirs()};File(src,"MainActivity.kt").writeText("package com.example.app\nimport android.app.Activity\nclass MainActivity:Activity()");selectRoot(r);toast("Project created")}.onFailure{toast("Create failed: "+it.message)}}
 private fun exportZip(){val r=projectRoot?:return toast("No project open");val out=File(cacheDir,r.name+".zip");ZipOutputStream(out.outputStream()).use{z->r.walkTopDown().filter{it.isFile}.forEach{f->z.putNextEntry(ZipEntry(f.relativeTo(r).invariantSeparatorsPath));f.inputStream().use{it.copyTo(z)};z.closeEntry()}};share(out,"application/zip","Export project")}
 private fun share(f:File,type:String,title:String){val u=FileProvider.getUriForFile(this,"$packageName.files",f);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{this.type=type;putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},title))}
 private fun latestApk():File?=projectRoot?.walkTopDown()?.filter{it.isFile&&it.extension.equals("apk",true)}?.maxByOrNull{it.lastModified()}
 private fun installLatestApk(){val apk=latestApk()?:return toast("No generated APK found");if(!packageManager.canRequestPackageInstalls()){startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")));return};val u=FileProvider.getUriForFile(this,"$packageName.files",apk);startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(u,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)})}
 private fun buildAndRun(){val r=projectRoot?:return toast("No project open");val gradlew=File(r,"gradlew");if(!gradlew.exists())return showLog("Build diagnostics","No gradlew in project. Import a Gradle project containing its wrapper, or use a provisioned local toolchain.");runCommand("chmod +x ./gradlew && ./gradlew assembleDebug --stacktrace",r){code,out->showLog(if(code==0)"BUILD SUCCESSFUL" else "BUILD FAILED",out);if(code==0)refreshExplorer()}}
 private fun terminal(){val input=EditText(this).apply{hint="pwd, ls, find . -name '*.kt'"};AlertDialog.Builder(this).setTitle("Terminal").setView(input).setPositiveButton("Run"){_,_->val r=projectRoot?:filesDir;runCommand(input.text.toString(),r){code,out->showLog("Terminal · exit $code",out)}}.setNegativeButton("Cancel",null).show()}
 private fun gitStatus(){val r=projectRoot?:return toast("No project open");runCommand("git status --short --branch",r){code,out->showLog("Git · exit $code",if(out.isBlank())"No output. Git may not be available in this Android runtime." else out)}}
 private fun runCommand(cmd:String,dir:File,done:(Int,String)->Unit){Thread{val result=runCatching{val p=ProcessBuilder("/system/bin/sh","-c",cmd).directory(dir).redirectErrorStream(true).start();val out=p.inputStream.bufferedReader().readText();p.waitFor() to out}.getOrElse{-1 to ("Execution error: "+it.message)};runOnUiThread{done(result.first,result.second)}}.start()}
 private fun showLog(title:String,text:String){val tv=TextView(this).apply{setText(text.ifBlank{"(no output)"});setTextIsSelectable(true);setPadding(28,20,28,20);typeface=android.graphics.Typeface.MONOSPACE};AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply{addView(tv)}).setPositiveButton("OK",null).show()}
 private fun showTools(){AlertDialog.Builder(this).setTitle("Built-in Tools").setItems(arrayOf("New Project","Import ZIP","Export / Backup ZIP","Terminal","Git Status","Install Latest APK","Project Settings")){_,w->when(w){0->newProject();1->importer.launch(arrayOf("application/zip","application/octet-stream"));2->exportZip();3->terminal();4->gitStatus();5->installLatestApk();else->showLog("Project Settings","Workspace: "+(projectRoot?.path?:"None")+"\nJDK target: 17\nAndroid SDK target: 35")}}.show()}
 private fun showSearch(){val r=projectRoot?:return toast("Open a project first");val input=EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val hits=r.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.filter{runCatching{it.readText().contains(q,true)}.getOrDefault(false)}.map{it.relativeTo(r).path}.take(50).toList();showLog("Search results",if(hits.isEmpty())"No matches" else hits.joinToString("\n"))}.setNegativeButton("Cancel",null).show()}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}