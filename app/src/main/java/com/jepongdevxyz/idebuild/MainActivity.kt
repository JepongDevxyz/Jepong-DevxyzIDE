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
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.widget.AdapterView
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.jepongdevxyz.idebuild.databinding.ActivityMainBinding
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity:AppCompatActivity(){
 private lateinit var b:ActivityMainBinding; private var projectRoot:File?=null; private var currentFile:File?=null; private var visibleFiles=listOf<File>(); private var treeRows=listOf<Pair<File,Int>>(); private val expandedDirectories=mutableSetOf<String>(); private var expandedRoot:String?=null; private var dirty=false; private var loadingEditor=false; private var activeScreen="files"; private var currentBuildLog=""
 private val prefs by lazy{getSharedPreferences("devxyzide",MODE_PRIVATE)}
 private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importProject(u)}
 private val toolchainImporter=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importToolchain(u)}
 override fun onCreate(s:Bundle?){
  super.onCreate(s)
  b=ActivityMainBinding.inflate(layoutInflater)
  setContentView(b.root)
  restoreWorkspace()
  showScreen("files")
  b.splashPane.visibility=View.VISIBLE
  b.splashPane.postDelayed({if(!isFinishing)b.splashPane.visibility=View.GONE},1400)

  b.importBtn.setOnClickListener{showScreen("tools")}
  b.projectMenuBtn.setOnClickListener{showScreen("tools")}
  b.fileList.setOnItemClickListener{_,_,position,_->val row=treeRows.getOrNull(position)?:return@setOnItemClickListener;if(row.first.isDirectory){if(!expandedDirectories.add(row.first.canonicalPath))expandedDirectories.remove(row.first.canonicalPath);refreshExplorer()}else requestOpenFile(row.first)}
  b.editor.addTextChangedListener(object:TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(!loadingEditor&&currentFile!=null){dirty=true;updateTabTitle()}}
   override fun afterTextChanged(s:Editable?){if(!loadingEditor){updateLineNumbers();applySyntaxHighlighting()}}
  })
  b.saveBtn.setOnClickListener{saveCurrent()}
  b.buildBtn.setOnClickListener{showScreen("build");buildAndRun()}
  b.runBuildBtn.setOnClickListener{showScreen("build");buildAndRun()}
  b.runCodeBtn.setOnClickListener{showScreen("build");buildAndRun()}
  b.installBuildBtn.setOnClickListener{installLatestApk()}
  b.openFolderBtn.setOnClickListener{openProjectFolder()}
  b.toolsBtn.setOnClickListener{showScreen("tools")}
  b.searchBtn.setOnClickListener{showSearch()}
  b.filesBtn.setOnClickListener{showScreen("files")}
  b.codeBtn.setOnClickListener{showScreen("code")}
  b.settingsBtn.setOnClickListener{showScreen("settings")}
  b.newProjectBtn.setOnClickListener{newProject()}
  b.newProjectBtnTools.setOnClickListener{newProject()}
  b.openProjectBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))}
  b.importProjectBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))}
  b.importToolsBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))}
  b.sourceTabBtn.setOnClickListener{openFileWithExtension("kt","java")}
  b.layoutTabBtn.setOnClickListener{openFileWithExtension("xml")}
  b.exportBtn.setOnClickListener{exportZip()}
  b.terminalBtn.setOnClickListener{terminal()}
  b.gitBtn.setOnClickListener{gitStatus()}
  b.installBtn.setOnClickListener{installLatestApk()}
  b.toolchainBtn.setOnClickListener{toolchainImporter.launch(arrayOf("application/zip","application/octet-stream"))}
  b.backupBtn.setOnClickListener{exportZip()}
  b.projectSettingsBtn.setOnClickListener{showProjectSettings()}
  b.diagnosticsBtn.setOnClickListener{showLog("Toolchain Diagnostics",ToolchainManager(filesDir).inspect(projectRoot).report)}
  b.databaseBtn.setOnClickListener{showLog("Database Viewer","Open a project SQLite database from the Project Explorer.")}
  b.layoutPreviewBtn.setOnClickListener{previewLayout()}
  b.resourceBtn.setOnClickListener{showResourceFiles()}
  b.colorPickerBtn.setOnClickListener{showColorPicker()}

  b.fontSizeSeekBar.progress=prefs.getInt("font_size",14).minus(4).coerceIn(0,24)
  updateFontSize(b.fontSizeSeekBar.progress+4)
  b.fontSizeSeekBar.setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener{
   override fun onProgressChanged(seekBar:android.widget.SeekBar?,progress:Int,fromUser:Boolean){updateFontSize(progress+4)}
   override fun onStartTrackingTouch(seekBar:android.widget.SeekBar?){}
   override fun onStopTrackingTouch(seekBar:android.widget.SeekBar?){}
  })
  b.wordWrapSwitch.isChecked=prefs.getBoolean("word_wrap",true)
  b.wordWrapSwitch.setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("word_wrap",checked).apply();b.editor.setHorizontallyScrolling(!checked)}
  b.editor.setHorizontallyScrolling(!b.wordWrapSwitch.isChecked)
  b.autoCompleteSwitch.isChecked=prefs.getBoolean("auto_complete",true)
  b.autoCompleteSwitch.setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("auto_complete",checked).apply()}
  b.backupSwitch.isChecked=prefs.getBoolean("backup",true)
  b.backupSwitch.setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean("backup",checked).apply()}
  b.themeSpinner.setSelection(prefs.getInt("theme",0).coerceIn(0,2))
  b.themeSpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:AdapterView<*>?){}
   override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){prefs.edit().putInt("theme",position).apply()}
  }
  b.gradleSpinner.setSelection(0)
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
 private fun fileAdapter(paths:List<String>)=object:ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,paths){override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View{val row=super.getView(position,convertView,parent) as TextView;row.setTextColor(getColor(com.jepongdevxyz.idebuild.R.color.text));row.setTextSize(13f);row.setPadding(12,9,8,9);row.setSingleLine(true);return row}}
 private fun refreshExplorer(){
  val root=projectRoot?:run{b.projectName.text="No project open";visibleFiles=emptyList();treeRows=emptyList();b.fileList.adapter=fileAdapter(emptyList());return}
  b.projectName.text=root.name
  visibleFiles=root.walkTopDown().filter{it.isFile&&it.name !in setOf(".DS_Store")}.sortedBy{it.relativeTo(root).path}.toList()
  if(expandedRoot!=root.canonicalPath){
   expandedRoot=root.canonicalPath;expandedDirectories.clear()
   fun expandShallow(directory:File,depth:Int){if(depth>=5)return;expandedDirectories.add(directory.canonicalPath);directory.listFiles()?.filter{it.isDirectory&&it.name !in setOf(".git",".gradle","build",".idea",".kotlin")}?.forEach{expandShallow(it,depth+1)}}
   expandShallow(root,0)
  }
  val rows=mutableListOf<Pair<File,Int>>()
  fun visit(directory:File,depth:Int){
   val children=directory.listFiles()?.sortedWith(compareBy<File>{!it.isDirectory}.thenBy{it.name.lowercase()}).orEmpty()
   children.forEach{child->
    if(child.name !in setOf(".DS_Store")&&!child.name.startsWith(".import-")&&!child.name.startsWith(".new-")){
     rows+=child to depth
     if(child.isDirectory&&child.canonicalPath in expandedDirectories&&depth<10&&child.name !in setOf(".git",".gradle","build",".idea",".kotlin"))visit(child,depth+1)
    }
   }
  }
  visit(root,0)
  treeRows=rows
  b.fileList.adapter=fileAdapter(rows.map{(file,depth)->
   val arrow=if(file.isDirectory){if(file.canonicalPath in expandedDirectories)"▾ " else "▸ "}else if(file.extension.equals("xml",true))"▧ " else "▣ "
   "  ".repeat(depth.coerceAtMost(7))+arrow+file.name
  })
 }
 private fun requestOpenFile(f:File){if(!dirty)return openFile(f);AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save changes to "+(currentFile?.name?:"current file")+" before opening "+f.name+"?").setPositiveButton("Save"){_,_->saveCurrent();openFile(f)}.setNegativeButton("Discard"){_,_->dirty=false;openFile(f)}.setNeutralButton("Cancel",null).show()}
 private fun openFile(f:File){if(f.length()>2_000_000)return toast("File too large");showScreen("code");loadingEditor=true;currentFile=f;b.editor.setText(runCatching{f.readText()}.getOrElse{"Cannot open as text"});dirty=false;loadingEditor=false;updateTabTitle();updateLineNumbers();applySyntaxHighlighting()}
 private fun openFileWithExtension(vararg extensions:String){projectRoot?:return toast("Open a project first");val matches=visibleFiles.filter{it.isFile&&extensions.any{extension->it.extension.equals(extension,true)}};val file=if(extensions.contains("xml"))matches.firstOrNull{it.invariantSeparatorsPath.contains("/res/layout/")}?:matches.firstOrNull() else matches.firstOrNull{it.name=="MainActivity.kt"||it.name=="MainActivity.java"}?:matches.firstOrNull();if(file==null)toast("No "+extensions.joinToString("/")+" file found")else requestOpenFile(file)}
 private fun updateLineNumbers(){val count=(b.editor.text?.toString()?.count{it=='\n'}?:0)+1;b.lineNumbers.text=(1..count).joinToString("\n")}
 private fun updateFontSize(size:Int){val actual=size.coerceIn(8,28);b.fontSizeValue.text=actual.toString();b.editor.textSize=actual.toFloat();prefs.edit().putInt("font_size",actual).apply()}
 private fun applySyntaxHighlighting(){
  val editable=b.editor.text?:return
  val source=editable.toString()
  editable.getSpans(0,editable.length,ForegroundColorSpan::class.java).forEach{editable.removeSpan(it)}
  val pattern=Regex("""\b(package|import|class|interface|public|private|protected|fun|val|var|override|return|new|void|extends|implements|if|else|when|this)\b""")
  pattern.findAll(source).forEach{editable.setSpan(ForegroundColorSpan(getColor(com.jepongdevxyz.idebuild.R.color.cyan)),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)}
  Regex("""\b[0-9]+\b""").findAll(source).forEach{editable.setSpan(ForegroundColorSpan(getColor(com.jepongdevxyz.idebuild.R.color.blue)),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)}
 }
 private fun updateTabTitle(){b.tabTitle.text=(currentFile?.name?:"Welcome")+(if(dirty)" •" else "")}
 private fun saveCurrent(){val f=currentFile?:return toast("No file open");runCatching{f.writeText(b.editor.text.toString())}.onSuccess{dirty=false;updateTabTitle();toast("Saved "+f.name)}.onFailure{toast("Save failed: "+it.message)}}
 private fun newProject(){val input=EditText(this).apply{hint="MyAwesomeApp"};AlertDialog.Builder(this).setTitle("New Android Project").setView(input).setPositiveButton("Create"){_,_->createTemplate(input.text.toString().ifBlank{"MyAwesomeApp"})}.setNegativeButton("Cancel",null).show()}
 private fun createTemplate(name:String){runCatching{val r=ProjectTemplateWriter.create(File(filesDir,"workspace"),name,assets.open("gradle-wrapper.jar"));selectRoot(r);showScreen("files");toast("Project created")}.onFailure{toast("Create failed: "+(it.message?:"Unknown error"))}}
 private fun exportZip(){val r=projectRoot?:return toast("No project open");val out=File(cacheDir,r.name+".zip");ZipOutputStream(out.outputStream()).use{z->r.walkTopDown().filter{it.isFile}.forEach{f->z.putNextEntry(ZipEntry(f.relativeTo(r).invariantSeparatorsPath));f.inputStream().use{it.copyTo(z)};z.closeEntry()}};share(out,"application/zip","Export project")}
 private fun share(f:File,type:String,title:String){val u=FileProvider.getUriForFile(this,"$packageName.files",f);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{this.type=type;putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},title))}
 private fun latestApk():File?=projectRoot?.walkTopDown()?.filter{it.isFile&&it.extension.equals("apk",true)}?.maxByOrNull{it.lastModified()}
 private fun installLatestApk(){val apk=latestApk()?:return toast("No generated APK found");if(!packageManager.canRequestPackageInstalls()){startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")));return};val u=FileProvider.getUriForFile(this,"$packageName.files",apk);startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(u,"application/vnd.android.package-archive");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)})}
 private fun importToolchain(uri:Uri){showLog("Toolchain import","Importing and validating toolchain package…");Thread{val result=ToolchainManager(filesDir).install(contentResolver,uri);runOnUiThread{showLog(if(result.success)"Toolchain ready" else "Toolchain import failed",result.report)}}.start()}
 private fun buildAndRun(){val r=projectRoot?:return toast("No project open");if(!File(r,"settings.gradle").isFile&&!File(r,"settings.gradle.kts").isFile)return showLog("BUILD BLOCKED","No settings.gradle/settings.gradle.kts found at project root. Re-import a complete Android/Gradle project.");val tc=ToolchainManager(filesDir);val check=tc.inspect(r);if(!check.ready)return showLog("Toolchain diagnostics",check.report);val env=tc.environment();val aapt2=tc.aapt2Path();b.buildStatus.text="●  BUILDING…";b.buildLog.text="Starting Gradle build…";currentBuildLog="Starting Gradle build…";runCommand("chmod +x ./gradlew && ./gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=\"$aapt2\" assembleDebug --stacktrace",r,env){code,out->currentBuildLog=out;b.buildStatus.text=if(code==0)"●  BUILD SUCCESSFUL" else "●  BUILD FAILED";b.buildStatus.setTextColor(getColor(if(code==0)com.jepongdevxyz.idebuild.R.color.success else com.jepongdevxyz.idebuild.R.color.cyan));b.buildLog.text=out;if(code==0){refreshExplorer();val apk=latestApk();if(apk!=null)showBuildSuccess(apk,out) else showLog("BUILD FINISHED","Gradle succeeded but no APK was found.\n\n$out")}else showLog("BUILD FAILED",out)}}
 private fun showBuildSuccess(apk:File,log:String){AlertDialog.Builder(this).setTitle("BUILD SUCCESSFUL").setMessage("Generated APK:\n"+apk.relativeTo(projectRoot!!).path+"\n\nSize: "+apk.length()+" bytes").setPositiveButton("Install APK"){_,_->installLatestApk()}.setNeutralButton("Build log"){_,_->showLog("Build log",log)}.setNegativeButton("Close",null).show()}
 private fun terminal(){val input=EditText(this).apply{hint="pwd, ls, find . -name '*.kt'"};AlertDialog.Builder(this).setTitle("Terminal").setView(input).setPositiveButton("Run"){_,_->val r=projectRoot?:filesDir;runCommand(input.text.toString(),r){code,out->showLog("Terminal · exit $code",out)}}.setNegativeButton("Cancel",null).show()}
 private fun gitStatus(){val r=projectRoot?:return toast("No project open");runCommand("git status --short --branch",r){code,out->showLog("Git · exit $code",if(out.isBlank())"No output. Git may not be available in this Android runtime." else out)}}
 private fun runCommand(cmd:String,dir:File,env:Map<String,String> = emptyMap(),done:(Int,String)->Unit){Thread{val result=runCatching{val pb=ProcessBuilder("/system/bin/sh","-c",cmd).directory(dir).redirectErrorStream(true);pb.environment().putAll(env);val p=pb.start();val out=p.inputStream.bufferedReader().readText();p.waitFor() to out}.getOrElse{-1 to ("Execution error: "+it.message)};runOnUiThread{if(!isFinishing&&!isDestroyed)done(result.first,result.second)}}.start()}
 private fun showScreen(screen:String){
  activeScreen=screen
  b.filesPane.visibility=if(screen=="files")View.VISIBLE else View.GONE
  b.editorPane.visibility=if(screen=="code")View.VISIBLE else View.GONE
  b.buildPane.visibility=if(screen=="build")View.VISIBLE else View.GONE
  b.toolsPane.visibility=if(screen=="tools")View.VISIBLE else View.GONE
  b.settingsPane.visibility=if(screen=="settings")View.VISIBLE else View.GONE
  b.screenTitle.text=when(screen){"files"->projectRoot?.name?: "Projects";"code"->"Code Editor";"build"->"Build";"tools"->"Tools";else->"Settings"}
  val active=getColor(com.jepongdevxyz.idebuild.R.color.cyan)
  val idle=getColor(com.jepongdevxyz.idebuild.R.color.muted)
  b.filesBtn.setTextColor(if(screen=="files")active else idle)
  b.codeBtn.setTextColor(if(screen=="code")active else idle)
  b.buildBtn.setTextColor(if(screen=="build")active else idle)
  b.toolsBtn.setTextColor(if(screen=="tools")active else idle)
  b.settingsBtn.setTextColor(if(screen=="settings")active else idle)
  if(screen=="files")refreshExplorer()
  if(screen=="build")b.buildLog.text=currentBuildLog.ifBlank{"› Task :app:compileDebugJavaWithJavac\n› Task :app:mergeDebugResources\n› Task :app:packageDebug\n› Task :app:assembleDebug"}
 }
 private fun showLog(title:String,text:String){currentBuildLog=text;b.buildLog.text=text.ifBlank{"(no output)"};val tv=TextView(this).apply{setText(text.ifBlank{"(no output)"});setTextIsSelectable(true);setPadding(28,20,28,20);typeface=Typeface.MONOSPACE};AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply{addView(tv)}).setPositiveButton("OK",null).show()}
 private fun showTools(){showScreen("tools")}
 private fun showSearch(){showScreen("files");val r=projectRoot?:return toast("Open a project first");val input=EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val hits=r.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.filter{runCatching{it.readText().contains(q,true)}.getOrDefault(false)}.map{it.relativeTo(r).path}.take(50).toList();showLog("Search results",if(hits.isEmpty())"No matches" else hits.joinToString("\n"))}.setNegativeButton("Cancel",null).show()}
 override fun onBackPressed(){if(dirty){AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save before leaving DevxyzIDE?").setPositiveButton("Save"){_,_->saveCurrent();super.onBackPressed()}.setNegativeButton("Discard"){_,_->dirty=false;super.onBackPressed()}.setNeutralButton("Cancel",null).show()}else super.onBackPressed()}
  private fun openProjectFolder(){val root=projectRoot?:return toast("No project open");showLog("Project folder",root.path+"\n\n"+root.listFiles().orEmpty().joinToString("\n"){it.name})}
 private fun previewLayout(){val file=visibleFiles.firstOrNull{it.extension.equals("xml",true)}?:return toast("No layout XML found");showLog("Layout Preview · "+file.name,file.readText().take(12000))}
 private fun showResourceFiles(){val files=visibleFiles.filter{it.path.contains(File.separator+"res"+File.separator)};showLog("Resource Manager",if(files.isEmpty())"No Android resources found" else files.joinToString("\n"){it.relativeTo(projectRoot!!).path})}
 private fun showColorPicker(){val colors=listOf("#12B8FF","#087AF5","#51E279","#FFCC55","#FF6688");AlertDialog.Builder(this).setTitle("Color Picker").setItems(colors.toTypedArray()){_,which->val color=colors[which];val start=b.editor.selectionStart.coerceAtLeast(0);b.editor.text.insert(start,color)}.show()}
 private fun showProjectSettings(){showLog("Project Settings","Workspace: "+(projectRoot?.path?:"None")+"\nGradle: "+(projectRoot?.let{File(it,"gradle/wrapper/gradle-wrapper.properties").takeIf{f->f.isFile}?.readText()?.lineSequence()?.firstOrNull{line->line.startsWith("distributionUrl=")}}?:"Not configured")+"\nJDK target: 17\nAndroid SDK target: 35")}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}