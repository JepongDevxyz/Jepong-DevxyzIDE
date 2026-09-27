package com.jepongdevxyz.idebuild
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.jepongdevxyz.idebuild.databinding.ActivityMainBinding
import java.io.File
import java.util.zip.ZipInputStream

class MainActivity:AppCompatActivity(){
 private lateinit var b:ActivityMainBinding
 private var projectRoot:File?=null
 private var currentFile:File?=null
 private var visibleFiles=listOf<File>()
 private val importer=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)importProject(uri)}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);b=ActivityMainBinding.inflate(layoutInflater);setContentView(b.root)
  b.importBtn.setOnClickListener{importer.launch(arrayOf("application/zip","application/octet-stream"))}
  b.fileList.setOnItemClickListener{_,_,p,_->openFile(visibleFiles[p])};b.saveBtn.setOnClickListener{saveCurrent()};b.buildBtn.setOnClickListener{showBuildPanel()};b.toolsBtn.setOnClickListener{showTools()};b.searchBtn.setOnClickListener{showSearch()};b.filesBtn.setOnClickListener{refreshExplorer()}
 }
 private fun importProject(uri:Uri){try{val root=File(filesDir,"workspace/project-"+System.currentTimeMillis()).apply{mkdirs()};contentResolver.openInputStream(uri)?.use{input->ZipInputStream(input).use{zip->var e=zip.nextEntry;while(e!=null){val out=File(root,e.name).canonicalFile;require(out.path.startsWith(root.canonicalPath+File.separator)||out==root){"Unsafe ZIP path"};if(e.isDirectory)out.mkdirs()else{out.parentFile?.mkdirs();out.outputStream().use{zip.copyTo(it)}};zip.closeEntry();e=zip.nextEntry}}};projectRoot=root;refreshExplorer();toast("Project imported successfully")}catch(e:Exception){toast("Import failed: "+e.message)}}
 private fun refreshExplorer(){val root=projectRoot?:return;visibleFiles=root.walkTopDown().filter{it.isFile}.sortedBy{it.relativeTo(root).path}.toList();b.fileList.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,visibleFiles.map{it.relativeTo(root).path})}
 private fun openFile(file:File){if(file.length()>2_000_000)return toast("File is too large for text editor");currentFile=file;b.tabTitle.text=file.name;b.editor.setText(runCatching{file.readText()}.getOrElse{"Cannot open as text."})}
 private fun saveCurrent(){val f=currentFile?:return toast("No file is open");runCatching{f.writeText(b.editor.text.toString())}.onSuccess{toast("Saved "+f.name)}.onFailure{toast("Save failed: "+it.message)}}
 private fun showBuildPanel(){val root=projectRoot;val gradle=root?.let{File(it,"gradlew")};val msg=if(gradle?.exists()==true)"Gradle project detected. Embedded Android toolchain execution is the next release milestone; project editing is ready." else "No Gradle wrapper detected in the imported project.";AlertDialog.Builder(this).setTitle("Build & Run").setMessage(msg).setPositiveButton("OK",null).show()}
 private fun showTools(){AlertDialog.Builder(this).setTitle("Project Tools").setItems(arrayOf("New Project","Open Project","Import Project","Project Settings","Terminal","Git","APK Signer","Resource Manager")){_,w->if(w==2)importer.launch(arrayOf("application/zip","application/octet-stream"))else toast("Tool module selected")}.show()}
 private fun showSearch(){val root=projectRoot?:return toast("Import a project first");val input=android.widget.EditText(this);AlertDialog.Builder(this).setTitle("Search project").setView(input).setPositiveButton("Search"){_,_->val q=input.text.toString();val count=root.walkTopDown().filter{it.isFile&&it.length()<1_000_000}.count{runCatching{it.readText().contains(q,true)}.getOrDefault(false)};toast(count.toString()+" matching files")}.setNegativeButton("Cancel",null).show()}
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}