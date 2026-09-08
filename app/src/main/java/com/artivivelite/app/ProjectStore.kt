package com.artivivelite.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProjectStore(private val context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val jsonFile = File(root, "projects.json")

    fun load(): MutableList<Project> {
        if (!jsonFile.exists()) return mutableListOf()
        val a = JSONArray(jsonFile.readText())
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            Project(o.getString("id"), o.getString("name"), o.getString("targetPath"),
                o.getDouble("targetWidthMeters").toFloat(), o.getString("contentPath"), o.getString("contentType"))
        }
    }

    fun save(list: List<Project>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("targetPath", it.targetPath)
                put("targetWidthMeters", it.targetWidthMeters); put("contentPath", it.contentPath)
                put("contentType", it.contentType)
            })
        }
        jsonFile.writeText(a.toString())
    }

    fun copyIntoProject(id: String, source: android.net.Uri, name: String): String {
        val dir = File(root, id).apply { mkdirs() }
        val out = File(dir, name)
        context.contentResolver.openInputStream(source)!!.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out.absolutePath
    }
}
