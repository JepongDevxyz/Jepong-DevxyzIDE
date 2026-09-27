package com.jepongdevxyz.idebuild
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.EditText
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
 private lateinit var b:ActivityMainBinding
 private var projectRoot:File?=null
 private var currentFile:File?=null
 private var visibleFiles=listOf<File>()
 private val prefs by lazy{getSharedPreferences("devxyzide",MODE_PRIVATE)}
 private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)importProject(u)}
 private val folderPicker=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->if(u!=null)toast("Folder access granted")}
 override fun onCreate(s:Bundle?){super.onCreate(s);b=ActivityMainBinding.inflate(layoutInflater);setContentView(b.root);restoreWorkspace()
  b.importBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))};b.fileList.setOnItemClickListener{_,_,p,_->openFile(visibleFiles[p])}
  b.saveBtn.setOnClickListener{saveCurrent()};b.buildBtn.setOnClickListener{showBuildPanel()};b.toolsBtn.setOnClickListener{showTools()};b.searchBtn.setOnClickListener{showSearch()};b.filesBtn.setOnClickListener{refreshExplorer()}
 }
 private fun restoreWorkspace(){prefs.getString("root",null)?.let{File(it)}?.takeIf{it.isDirectory}?.let{projectRoot=it;refreshExplorer()}}
 private fun importProject(uri:Uri){runCatching{val root=File(filesDir,"workspace/project-"+System.currentTimeMillis()).apply{mkdirs()};contentResolver.openInputStream(uri)!!.use{input->ZipInputStream(input).use{z->var e=z.nextEntry;while(e!=null){val out=File(root,e.name).canonicalFile;require(out.path.startsWith(root.canonicalPath+File.separator)||out==root){"Unsafe ZIP path"};if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();out.outputStream().use{z.copyTo(it)}};z.closeEntry();e=z.nextEntry}}};projectRoot=root;prefs.edit().putString("root",root.path).apply();refreshExplorer();toast("Project imported successfully")}.onFailure{toast("Import failed: "+it.message)}}
 private fun refreshExplorer(){val r=projectRoot?:return;visibleFiles=r.walkTopDown().filter{it.isFile}.sortedBy{it.relativeTo(r).path}.toList();b.fileList.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,visibleFiles.map{it.relativeTo(r).path})}
 private fun openFile(f:File){if(f.length()>2_000_000)return toast("File is too large");currentFile=f;b.tabTitle.text=f.name;b.editor.setText(runCatching{f.readText()}.getOrElse{"Cannot open as text"})}
 private fun saveCurrent(){val f=currentFile?:return toast("No file is open");runCatching{f.writeText(b.editor.text.toString())}.onSuccess{toast("Saved "+f.name)}.onFailure{toast("Save failed: "+it.message)}}
 private fun newProject(){val input=EditText(this).apply{hint="MyAwesomeApp"};AlertDialog.Builder(this).setTitle("New Android Project").setView(input).setPositiveButton("Create"){_,_->createTemplate(input.text.toString().ifBlank{"MyAwesomeApp"})}.setNegativeButton("Cancel",null).show()}
 private fun createTemplate(name:String){runCatching{val safe=name.replace(Regex("[^A-Za-z0-9_]"),"");val r=File(filesDir,"workspace/$safe").apply{mkdirs()};File(r,"settings.gradle.kts").writeText("pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\nrootProject.name=\"$safe\"\ninclude(\":app\")");File(r,"app/src/main/java/com/example/app").mkdirs();File(r,"app/src/main/java/com/example/app/MainActivity.kt").writeText("package com.example.app\n\nclass MainActivity");projectRoot=r;prefs.edit().putString("root",r.path).apply();refreshExplorer();toast("Project created")}.onFailure{toast("Create failed: "+it.message)}}
 private fun exportZip(){val r=projectRoot?:return toast("No project open");val out=File(cacheDir,r.name+".zip");ZipOutputStream(out.outputStream()).use{z->r.walkTopDown().filter{it.isFile}.forEach{f->z.putNextEntry(ZipEntry(f.relativeTo(r).invariantSeparatorsPath));f.inputStream().use{it.copyTo(z)};z.closeEntry()}};val u=FileProvider.getUriForFile(this,"$packageName.files",out);startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/zip";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Export project"))}
 private fun showBuildPanel(){val r=projectRoot?:return toast("No project open");val gradle=File(r,"gradlew");val apks=r.walkTopDown().filter{it.isFile&&it.extension=="apk"}.toList();val msg=buildString{append(if(gradle.exists())"Gradle wrapper detected.\n" else "Gradle wrapper not found.\n");append(if(apks.isEmpty())"No APK output found." else "APK outputs: "+apks.joinToString{it.name})};AlertDialog.Builder(this).setTitle("Build & Run").setMessage(msg).setPositiveButton("OK",null).show()}
 private fun showTools(){AlertDialog.Builder(this).setTitle("Project Tools").setItems(arrayOf("New Project","Open Folder","Import ZIP","Export / Backup ZIP","Terminal","Git","APK Installer Permission","Project Settings")){_,w->when(w){0->newProject();1->folderPicker.launch(null);2->importer.launch(arrayOf("application/zip","application/octet-stream"));3->exportZip();6->startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")));else->toast("Module scaffold ready for implementation")}}.show()}
 private fun showSearch(){val r=projectRoot?:return toast("Open a project first");val input=EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val hits=r.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.filter{runCatching{it.readText().contains(q,true)}.getOrDefault(false)}.map{it.relativeTo(r).path}.take(30).toList();AlertDialog.Builder(this).setTitle("Search results").setMessage(if(hits.isEmpty())"No matches" else hits.joinToString("\n")).setPositiveButton("OK",null).show()}.setNegativeButton("Cancel",null).show()}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}