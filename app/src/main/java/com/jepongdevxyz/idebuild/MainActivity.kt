package com.jepongdevxyz.idebuild
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.jepongdevxyz.idebuild.databinding.ActivityMainBinding
import java.io.File
import java.util.zip.ZipOutputStream

class MainActivity:AppCompatActivity(){
 private lateinit var b:ActivityMainBinding; private var projectRoot:File?=null; private var currentFile:File?=null; private var visibleFiles=listOf<File>(); private var dirty=false; private var loadingEditor=false; private var activeScreen="files"; private var currentBuildLog=""
 private val prefs by lazy{getSharedPreferences("devxyzide",MODE_PRIVATE)}
 private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importProject(u)}
 private val toolchainImporter=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importToolchain(u)}
 override fun onCreate(s:Bundle?){super.onCreate(s);b=ActivityMainBinding.inflate(layoutInflater);setContentView(b.root);restoreWorkspace();showScreen("files")
  b.importBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))};b.fileList.setOnItemClickListener{_,_,p,_->requestOpenFile(visibleFiles[p])};b.editor.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,bf:Int,c:Int){if(!loadingEditor&&currentFile!=null){dirty=true;updateTabTitle()}};override fun afterTextChanged(e:Editable?){}})
  b.saveBtn.setOnClickListener{saveCurrent()};b.buildBtn.setOnClickListener{showScreen("build");buildAndRun()};b.toolsBtn.setOnClickListener{showScreen("tools")};b.searchBtn.setOnClickListener{showSearch()};b.filesBtn.setOnClickListener{showScreen("files")};b.codeBtn.setOnClickListener{showScreen("code")};b.settingsBtn.setOnClickListener{showScreen("settings")};b.newProjectBtn.setOnClickListener{newProject()};b.importProjectBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))};b.exportBtn.setOnClickListener{exportZip()};b.terminalBtn.setOnClickListener{terminal()};b.gitBtn.setOnClickListener{gitStatus()};b.installBtn.setOnClickListener{installLatestApk()};b.toolchainBtn.setOnClickListener{toolchainImporter.launch(arrayOf("application/zip","application/octet-stream"))};b.backupBtn.setOnClickListener{exportZip()};b.projectSettingsBtn.setOnClickListener{showLog("Project Settings","Workspace: "+(projectRoot?.path?:"None")+"\\nJDK target: 17\\nAndroid SDK target: 35")};b.diagnosticsBtn.setOnClickListener{showLog("Toolchain Diagnostics",ToolchainManager(filesDir).inspect(projectRoot).report)};b.runBuildBtn.setOnClickListener{showScreen("build");buildAndRun()};b.installBuildBtn.setOnClickListener{installLatestApk()}
 }
 private fun restoreWorkspace(){prefs.getString("root",null)?.let(::File)?.takeIf{it.isDirectory}?.let{projectRoot=it;refreshExplorer()}}
 private fun importProject(uri:Uri){
  toast("Importing project…")
  Thread{
   var staging:File?=null
   val result:Result<File> = try{
    val workspace=File(filesDir,"workspace").apply{require(mkdirs()||isDirectory){"Cannot create workspace"}}
    staging=File(workspace,".import-"+System.currentTimeMillis()).apply{require(mkdirs()){"Cannot create import staging directory"}}
    val input=contentResolver.openInputStream(uri)?:error("Cannot open selected ZIP")
    Result.success(input.use{ProjectArchiveImporter.extract(it,staging!!)})
   }catch(t:Throwable){Result.failure(t)}
   runOnUiThread{
    result.onSuccess{stagedRoot->
     runCatching{
      val finalRoot=File(File(filesDir,"workspace"),"project-"+System.currentTimeMillis())
      require(stagedRoot.renameTo(finalRoot)){"Could not activate imported project"}
      staging?.deleteRecursively();staging=null
      selectRoot(finalRoot);showScreen("files");toast("Project imported successfully")
     }.onFailure{staging?.deleteRecursively();toast("Import failed: "+(it.message?:"Unknown error"))}
    }.onFailure{staging?.deleteRecursively();toast("Import failed: "+(it.message?:"Invalid project archive"))}
   }
  }.start()
 }
 private fun selectRoot(r:File){projectRoot=r;prefs.edit().putString("root",r.path).apply();refreshExplorer()}
 private fun fileAdapter(paths:List<String>)=object:ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,paths){override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View{val row=super.getView(position,convertView,parent) as TextView;row.setTextColor(getColor(com.jepongdevxyz.idebuild.R.color.text));row.setPadding(12,12,8,12);return row}}
 private fun refreshExplorer(){val r=projectRoot?:run{b.projectName.text="No project open";b.fileList.adapter=fileAdapter(emptyList());return};b.projectName.text=r.name;visibleFiles=r.walkTopDown().filter{it.isFile}.sortedBy{it.relativeTo(r).path}.toList();b.fileList.adapter=fileAdapter(visibleFiles.map{it.relativeTo(r).path})}
 private fun requestOpenFile(f:File){if(!dirty)return openFile(f);AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save changes to "+(currentFile?.name?:"current file")+" before opening "+f.name+"?").setPositiveButton("Save"){_,_->saveCurrent();openFile(f)}.setNegativeButton("Discard"){_,_->dirty=false;openFile(f)}.setNeutralButton("Cancel",null).show()}
 private fun openFile(f:File){if(f.length()>2_000_000)return toast("File too large");showScreen("code");loadingEditor=true;currentFile=f;b.editor.setText(runCatching{f.readText()}.getOrElse{"Cannot open as text"});dirty=false;loadingEditor=false;updateTabTitle()}
 private fun updateTabTitle(){b.tabTitle.text=(currentFile?.name?:"Welcome")+(if(dirty)" •" else "")}
 private fun saveCurrent(){val f=currentFile?:return toast("No file open");runCatching{f.writeText(b.editor.text.toString())}.onSuccess{dirty=false;updateTabTitle();toast("Saved "+f.name)}.onFailure{toast("Save failed: "+it.message)}}
 private fun newProject(){val input=EditText(this).apply{hint="MyAwesomeApp"};AlertDialog.Builder(this).setTitle("New Android Project").setView(input).setPositiveButton("Create"){_,_->createTemplate(input.text.toString().ifBlank{"MyAwesomeApp"})}.setNegativeButton("Cancel",null).show()}
 private fun createTemplate(name:String){runCatching{val r=ProjectTemplateWriter.create(File(filesDir,"workspace"),name,assets.open("gradle-wrapper.jar"));selectRoot(r);showScreen("files");toast("Project created")}.onFailure{toast("Create failed: "+(it.message?:"Unknown error"))}}
 private fun exportZip(){val r=projectRoot?:return toast("No project open");val out=File(cacheDir,r.name+".zip");ZipOutputStream(out.outputStream()).use{z->r.walkTopDown().filter{it.isFile}.forEach{f->z.putNextEntry(ZipEntry(f.relativeTo(r).invariantSeparatorsPath));f.inputStream().use{it.copyTo(z)};z.closeEntry()}};share(out,"application/zip","Export project")}
 private fun share(f:File,type:String,title:String){val u=FileProvider.getUriForFile(this,"$packageName.files",f);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{this.type=type;putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},title))}
 private fun latestApk():File?=projectRoot?.walkTopDown()?.filter{it.isFile&&it.extension.equals("apk",true)}?.maxByOrNull{it.lastModified()}
 private fun installLatestApk(){val apk=latestApk()?:return toast("No generated APK found");if(!packageManager.canRequestPackageInstalls()){startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")));return};val u=FileProvider.getUriForFile(this,"$packageName.files",apk);startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(u,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)})}
 private fun importToolchain(uri:Uri){showLog("Toolchain import","Importing and validating toolchain package…");Thread{val result=ToolchainManager(filesDir).install(contentResolver,uri);runOnUiThread{showLog(if(result.success)"Toolchain ready" else "Toolchain import failed",result.report)}}.start()}
 private fun buildAndRun(){val r=projectRoot?:return toast("No project open");if(!File(r,"settings.gradle").isFile&&!File(r,"settings.gradle.kts").isFile)return showLog("BUILD BLOCKED","No settings.gradle/settings.gradle.kts found at project root. Re-import a complete Android/Gradle project.");val tc=ToolchainManager(filesDir);val check=tc.inspect(r);if(!check.ready)return showLog("Toolchain diagnostics",check.report);val env=tc.environment();val aapt2=tc.aapt2Path();runCommand("chmod +x ./gradlew && ./gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=\"$aapt2\" assembleDebug --stacktrace",r,env){code,out->currentBuildLog=out;b.buildStatus.text=if(code==0)"BUILD SUCCESSFUL" else "BUILD FAILED";b.buildLog.text=out;showLog(if(code==0)"BUILD SUCCESSFUL" else "BUILD FAILED",out);if(code==0){refreshExplorer();val apk=latestApk();if(apk!=null)showBuildSuccess(apk,out) else showLog("BUILD FINISHED","Gradle succeeded but no APK was found.\n\n$out")}}}
 private fun showBuildSuccess(apk:File,log:String){AlertDialog.Builder(this).setTitle("BUILD SUCCESSFUL").setMessage("Generated APK:\n"+apk.relativeTo(projectRoot!!).path+"\n\nSize: "+apk.length()+" bytes").setPositiveButton("Install APK"){_,_->installLatestApk()}.setNeutralButton("Build log"){_,_->showLog("Build log",log)}.setNegativeButton("Close",null).show()}
 private fun terminal(){val input=EditText(this).apply{hint="pwd, ls, find . -name '*.kt'"};AlertDialog.Builder(this).setTitle("Terminal").setView(input).setPositiveButton("Run"){_,_->val r=projectRoot?:filesDir;runCommand(input.text.toString(),r){code,out->showLog("Terminal · exit $code",out)}}.setNegativeButton("Cancel",null).show()}
 private fun gitStatus(){val r=projectRoot?:return toast("No project open");runCommand("git status --short --branch",r){code,out->showLog("Git · exit $code",if(out.isBlank())"No output. Git may not be available in this Android runtime." else out)}}
 private fun runCommand(cmd:String,dir:File,env:Map<String,String> = emptyMap(),done:(Int,String)->Unit){Thread{val result=runCatching{val pb=ProcessBuilder("/system/bin/sh","-c",cmd).directory(dir).redirectErrorStream(true);pb.environment().putAll(env);val p=pb.start();val out=p.inputStream.bufferedReader().readText();p.waitFor() to out}.getOrElse{-1 to ("Execution error: "+it.message)};runOnUiThread{if(!isFinishing&&!isDestroyed)done(result.first,result.second)}}.start()}
 private fun showScreen(screen:String){
  activeScreen=screen
  b.filesPane.visibility=if(screen=="files")android.view.View.VISIBLE else android.view.View.GONE
  b.editorPane.visibility=if(screen=="code")android.view.View.VISIBLE else android.view.View.GONE
  b.buildPane.visibility=if(screen=="build")android.view.View.VISIBLE else android.view.View.GONE
  b.toolsPane.visibility=if(screen=="tools")android.view.View.VISIBLE else android.view.View.GONE
  b.settingsPane.visibility=if(screen=="settings")android.view.View.VISIBLE else android.view.View.GONE
  b.screenTitle.text=screen.replaceFirstChar{it.uppercase()}
  b.filesBtn.isSelected=screen=="files";b.codeBtn.isSelected=screen=="code";b.buildBtn.isSelected=screen=="build";b.toolsBtn.isSelected=screen=="tools";b.settingsBtn.isSelected=screen=="settings"
  if(screen=="files")refreshExplorer()
  if(screen=="build")b.buildLog.text=currentBuildLog.ifBlank{"Ready to build\\n\\nOpen a Gradle project and tap Build APK."}
 }
 private fun showLog(title:String,text:String){currentBuildLog=text;b.buildLog.text=text.ifBlank{"(no output)"};val tv=TextView(this).apply{setText(text.ifBlank{"(no output)"});setTextIsSelectable(true);setPadding(28,20,28,20);typeface=Typeface.MONOSPACE};AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply{addView(tv)}).setPositiveButton("OK",null).show()}
 private fun showTools(){AlertDialog.Builder(this).setTitle("Built-in Tools").setItems(arrayOf("New Project","Import ZIP","Export / Backup ZIP","Terminal","Git Status","Install Latest APK","Import Toolchain ZIP","Toolchain Diagnostics","Project Settings")){_,w->when(w){0->newProject();1->importer.launch(arrayOf("application/zip","application/octet-stream"));2->exportZip();3->terminal();4->gitStatus();5->installLatestApk();6->toolchainImporter.launch(arrayOf("application/zip","application/octet-stream"));7->{val r=projectRoot;showLog("Toolchain Diagnostics",ToolchainManager(filesDir).inspect(r).report)};else->showLog("Project Settings","Workspace: "+(projectRoot?.path?:"None")+"\nJDK target: 17\nAndroid SDK target: 35")}}.show()}
 private fun showSearch(){showScreen("files");val r=projectRoot?:return toast("Open a project first");val input=EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val hits=r.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.filter{runCatching{it.readText().contains(q,true)}.getOrDefault(false)}.map{it.relativeTo(r).path}.take(50).toList();showLog("Search results",if(hits.isEmpty())"No matches" else hits.joinToString("\n"))}.setNegativeButton("Cancel",null).show()}
 override fun onBackPressed(){if(dirty){AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save before leaving DevxyzIDE?").setPositiveButton("Save"){_,_->saveCurrent();super.onBackPressed()}.setNegativeButton("Discard"){_,_->dirty=false;super.onBackPressed()}.setNeutralButton("Cancel",null).show()}else super.onBackPressed()}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}