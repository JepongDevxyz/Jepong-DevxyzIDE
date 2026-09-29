Warning: truncated output (original token count: 5928)
Total output lines: 237

package com.jepongdevxyz.idebuild
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.provider.Settings
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
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
  b.backBtn.setOnClickListener{showScreen("files")}
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
 private fun fileAdapter(items:List<Pair<File,Int>>)=object:BaseAdapter(){
  override fun getCount()=items.size
  override fun getItem(position:Int)=items[position]
  override fun getItemId(position:Int)=position.toLong()
  override fun getView(position:Int,convertView:View?,parent:ViewGroup):View{
   val (file,depth)=items[position]
   val row=(convertView as? LinearLayout)?:LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=android.view.Gravity.CENTER_VERTICAL;minimumHeight=38.dp();isFocusable=false;isClickable=false}
   row.removeAllViews();row.setPadding((8+depth*15).dp(),0,8.dp(),0);row.layoutParams=android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,38.dp())
   row.setBackgroundResource(if(currentFile?.canonicalPath==file.canonicalPath)com.jepongdevxyz.idebuild.R.drawable.tree_row_selected else android.R.color.transparent)
   val arrow=TextView(this@MainActivity).apply{text=if(file.isDirectory){if(file.canonicalPath in expandedDirectories)"⌄" else "›"}else "";setTextColor(getColor(com.jepongdevxyz.idebuild.R.color.muted));textSize=18f;gravity=android.view.Gravity.CENTER}
   row.addView(arrow,LinearLayout.LayoutParams(18.dp(),ViewGroup.LayoutParams.MATCH_PARENT))
   val icon=TextView(this@MainActivity).apply{text=when{file.isDirectory->"📁";file.extension.equals("xml",true)->"▧";file.name.startsWith("build.gradle")->"◧";else->"▣"};setTextColor(if(file.isDirectory)Color.rgb(255,200,87) else if(file.extension.equals("xml",true))Color.rgb(255,128,107) else getColor(com.jepongdevxyz.idebuild.R.color.cyan));textSize=14f;gravity=android.view.Gravity.CENTER}
   row.addView(icon,LinearLayout.LayoutParams(24.dp(),ViewGroup.LayoutParams.MATCH_PARENT))
   val label=TextView(this@MainActivity).apply{text=file.name;setTextColor(getColor(com.jepongdevxyz.idebuild.R.color.text));textSize=13f;gravity=android.view.Gravity.CENTER_VERTICAL;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.MIDDLE}
   row.addView(label,LinearLayout.LayoutParams(0,ViewGrou…928 tokens truncated…undColorSpan(getColor(com.jepongdevxyz.idebuild.R.color.blue)),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)}
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
  b.brandTitle.text=when(screen){"files"->"DevxyzIDE";"code"->currentFile?.name?:"Code Editor";"build"->"Build";"tools"->"Tools";else->"Settings"}
  b.brandTitle.textSize=if(screen=="code")14f else 18f
  b.screenTitle.visibility=View.GONE
  b.brandIcon.visibility=View.GONE
  b.backBtn.visibility=if(screen=="code")View.VISIBLE else View.GONE
  val showActions=screen=="files"||screen=="code"
  b.searchBtn.visibility=if(showActions)View.VISIBLE else View.GONE
  b.importBtn.visibility=if(showActions)View.VISIBLE else View.GONE
  val active=getColor(com.jepongdevxyz.idebuild.R.color.cyan)
  val idle=getColor(com.jepongdevxyz.idebuild.R.color.muted)
  b.filesBtn.setTextColor(if(screen=="files")active else idle)
  b.codeBtn.setTextColor(if(screen=="code")active else idle)
  b.buildBtn.setTextColor(if(screen=="build")active else idle)
  b.toolsBtn.setTextColor(if(screen=="tools")active else idle)
  b.settingsBtn.setTextColor(if(screen=="settings")active else idle)
  if(screen=="files")refreshExplorer()
  if(screen=="build")b.buildLog.text=currentBuildLog.ifBlank{"No build run yet.\n\nTap Build APK to run Gradle tasks. Build output will appear here."}
 }
 private fun showLog(title:String,text:String){currentBuildLog=text;b.buildLog.text=text.ifBlank{"(no output)"};val tv=TextView(this).apply{setText(text.ifBlank{"(no output)"});setTextIsSelectable(true);setPadding(28,20,28,20);typeface=Typeface.MONOSPACE};AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply{addView(tv)}).setPositiveButton("OK",null).show()}
 private fun showTools(){showScreen("tools")}
 private fun showSearch(){showScreen("files");val r=projectRoot?:return toast("Open a project first");val input=EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val hits=r.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.filter{runCatching{it.readText().contains(q,true)}.getOrDefault(false)}.map{it.relativeTo(r).path}.take(50).toList();showLog("Search results",if(hits.isEmpty())"No matches" else hits.joinToString("\n"))}.setNegativeButton("Cancel",null).show()}
 override fun onBackPressed(){if(dirty){AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save before leaving DevxyzIDE?").setPositiveButton("Save"){_,_->saveCurrent();super.onBackPressed()}.setNegativeButton("Discard"){_,_->dirty=false;super.onBackPressed()}.setNeutralButton("Cancel",null).show()}else super.onBackPressed()}
  private fun openProjectFolder(){val root=projectRoot?:return toast("No project open");showLog("Project folder",root.path+"\n\n"+root.listFiles().orEmpty().joinToString("\n"){it.name})}
 private fun previewLayout(){val file=visibleFiles.firstOrNull{it.extension.equals("xml",true)}?:return toast("No layout XML found");showLog("Layout Preview · "+file.name,file.readText().take(12000))}
 private fun showResourceFiles(){val files=visibleFiles.filter{it.path.contains(File.separator+"res"+File.separator)};showLog("Resource Manager",if(files.isEmpty())"No Android resources found" else files.joinToString("\n"){it.relativeTo(projectRoot!!).path})}
 private fun showColorPicker(){val colors=listOf("#12B8FF","#087AF5","#51E279","#FFCC55","#FF6688");AlertDialog.Builder(this).setTitle("Color Picker").setItems(colors.toTypedArray()){_,which->val color=colors[which];val start=b.editor.selectionStart.coerceAtLeast(0);b.editor.text.insert(start,color)}.show()}
 private fun showProjectSettings(){
  val actions=arrayOf("Project information","Export / backup ZIP","Import build toolchain ZIP")
  AlertDialog.Builder(this).setTitle("Project Settings").setItems(actions){_,which->when(which){0->showProjectInformation();1->exportZip();2->toolchainImporter.launch(arrayOf("application/zip","application/octet-stream"))}}.setNegativeButton("Close",null).show()
 }
 private fun showProjectInformation(){showLog("Project Settings","Workspace: "+(projectRoot?.path?:"None")+"\nGradle: "+(projectRoot?.let{File(it,"gradle/wrapper/gradle-wrapper.properties").takeIf{f->f.isFile}?.readText()?.lineSequence()?.firstOrNull{line->line.startsWith("distributionUrl=")}}?:"Not configured")+"\nJDK target: 17\nAndroid SDK target: 35")}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
