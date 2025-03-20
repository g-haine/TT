package com.example.timetracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

// Classe principale de l'application Android
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeTrackerApp()
        }
    }
}

// Interface principale de l'application
@Composable
fun TimeTrackerApp(viewModel: TimeTrackerViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadPresets(context) // Chargement des presets au démarrage
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Time Tracker") })
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            PresetDropdown(viewModel, context) // Sélection des métiers
            Spacer(modifier = Modifier.height(8.dp))
            TimerList(viewModel) // Liste des timers actifs
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.addTimer() }) {
                Text("Ajouter un compteur")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.resetAndSave(context) }) {
                Text("Remise à zéro et sauvegarde")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.correctTimer() }) {
                Text("Correction du temps")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.addNewPreset(context) }) {
                Text("Créer un nouveau métier")
            }
        }
    }
}

// Menu déroulant pour sélectionner un métier
@Composable
fun PresetDropdown(viewModel: TimeTrackerViewModel, context: Context) {
    var expanded by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(viewModel.presets.keys.firstOrNull() ?: "") }

    Box(modifier = Modifier.fillMaxWidth()) {
        Button(onClick = { expanded = true }) {
            Text(selectedPreset)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            viewModel.presets.forEach { (name, tasks) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    selectedPreset = name
                    viewModel.setPresetTasks(tasks)
                    expanded = false
                })
            }
        }
    }
}

// Liste des timers actifs
@Composable
fun TimerList(viewModel: TimeTrackerViewModel) {
    LazyColumn(modifier = Modifier.fillMaxHeight(0.7f)) {
        items(viewModel.timers.keys.toList()) { timerName ->
            TimerItem(timerName, viewModel)
        }
    }
}

// Affichage individuel d'un timer
@Composable
fun TimerItem(timerName: String, viewModel: TimeTrackerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { viewModel.toggleTimer(timerName) },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "$timerName: ${viewModel.getFormattedTime(timerName)}")
        Button(onClick = { viewModel.removeTimer(timerName) }) {
            Text("Supprimer")
        }
    }
}

// ViewModel gérant la logique métier de l'application
class TimeTrackerViewModel : androidx.lifecycle.ViewModel() {
    var timers = mutableStateMapOf<String, Long>()
    var presets = mutableMapOf<String, List<String>>()
    private var activeTimer: String? = null
    private var startTime: Long = 0L
    private val log = mutableListOf<String>()

    // Chargement des presets depuis un fichier JSON
    fun loadPresets(context: Context) {
        val file = File(context.filesDir, "presets.json")
        if (file.exists()) {
            val jsonContent = file.readText()
            val jsonObject = JSONObject(jsonContent)
            presets = jsonObject.keys().asSequence().associateWith { jsonObject.getJSONArray(it).let { arr ->
                List(arr.length()) { i -> arr.getString(i) }
            }}.toMutableMap()
        } else {
            presets["Enseignant-Chercheur"] = listOf("Code", "Administration", "Enseignement", "Recherche", "Biblio", "Montage Projet")
            presets["Développeur"] = listOf("Développement", "Tests", "Documentation", "Réunions")
            presets["Consultant"] = listOf("Client", "Préparation", "Rapports", "Réunions")
            savePresets(context)
        }
    }

    // Sauvegarde des presets dans un fichier JSON
    fun savePresets(context: Context) {
        val file = File(context.filesDir, "presets.json")
        val jsonObject = JSONObject(presets)
        file.writeText(jsonObject.toString())
    }

    // Ajout d'un nouveau métier
    fun addNewPreset(context: Context) {
        val newPresetName = "Nouveau Métier ${presets.size + 1}"
        presets[newPresetName] = listOf("Tâche 1", "Tâche 2")
        savePresets(context)
    }

    // Mise en place des tâches associées à un métier
    fun setPresetTasks(tasks: List<String>) {
        timers.clear()
        tasks.forEach { timers[it] = 0L }
    }

    // Ajout d'un compteur personnalisé
    fun addTimer() {
        val newTimer = "Tâche ${timers.size + 1}"
        timers[newTimer] = 0L
    }

    // Suppression d'un timer
    fun removeTimer(timerName: String) {
        if (activeTimer == timerName) {
            stopTimer()
        }
        timers.remove(timerName)
    }

    // Démarrage ou arrêt d'un timer
    fun toggleTimer(timerName: String) {
        if (activeTimer == timerName) {
            stopTimer()
        } else {
            startTimer(timerName)
        }
    }

    private fun startTimer(timerName: String) {
        if (activeTimer != null) return
        activeTimer = timerName
        startTime = System.currentTimeMillis()
        log.add("Start $timerName at ${getCurrentTime()}")
    }

    private fun stopTimer() {
        if (activeTimer == null) return
        val elapsed = System.currentTimeMillis() - startTime
        timers[activeTimer!!] = timers[activeTimer!!]!! + elapsed
        log.add("Stop $activeTimer at ${getCurrentTime()} (Duration: ${elapsed / 1000} sec)")
        activeTimer = null
    }

    fun resetAndSave(context: Context) {
        timers.clear()
        log.clear()
    }
    
    fun correctTimer() {}
    
    fun getFormattedTime(timerName: String): String {
        val time = timers[timerName] ?: 0L
        return "${time / 1000} sec"
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}

