package com.example.mobileapp_calendar_schoolversion

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.TextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var dateTV: TextView
    private lateinit var calendarView: CalendarView
    private lateinit var taskListTextView: TextView
    private lateinit var addTaskButton: Button
    private lateinit var removeTaskButton: Button
    private lateinit var taskManager: TaskManager
    private lateinit var taskListForSelectedDate: TaskListForSelectedDate

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dateTV = findViewById(R.id.idTVDate)
        calendarView = findViewById(R.id.calendarView)
        addTaskButton = findViewById(R.id.addTaskButton)
        taskListTextView = findViewById(R.id.taskListTextView)
        removeTaskButton = findViewById(R.id.removeTaskButton)

        taskManager = TaskManager(this)
        taskListForSelectedDate = TaskListForSelectedDate(this)

        val currentDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        dateTV.text = formatDate(currentDate)
        updateTaskListForSelectedDate(currentDate)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val date = "$dayOfMonth-${month + 1}-$year"
            dateTV.text = formatDate(date)
            updateTaskListForSelectedDate(formatDate(date))
        }

        addTaskButton.setOnClickListener {
            showAddTaskDialog()
        }

        removeTaskButton.setOnClickListener {
            showRemoveTaskDialog()
        }
    }

    private fun formatDate(date: String): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val parsedDate = sdf.parse(date)
        val formattedDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        return formattedDate.format(parsedDate ?: Date())
    }

    private fun updateTaskListForSelectedDate(selectedDate: String) {
        val tasksForDate = taskListForSelectedDate.getTasks(selectedDate)
        showTasksForDate(tasksForDate, selectedDate)
    }

    private fun showAddTaskDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Task")

        val input = EditText(this)
        builder.setView(input)

        builder.setPositiveButton("OK") { dialog, _ ->
            val taskName = input.text.toString()
            dialog.dismiss()
            showTimePickerDialog(taskName)
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    private fun showTimePickerDialog(taskName: String) {
        val timePickerDialog = TimePickerDialog(this, { _, hourOfDay, minute ->
            val selectedDate = dateTV.text.toString()
            val time = String.format("%02d:%02d", hourOfDay, minute)
            taskManager.addTask(selectedDate, taskName, time)
            taskListForSelectedDate.addTask(selectedDate, "$taskName at $time")
            updateTaskListForSelectedDate(selectedDate)
        }, 12, 0, true)

        timePickerDialog.setTitle("Select Time")
        timePickerDialog.show()
    }

    private fun showTasksForDate(tasks: List<String>, date: String) {
        val taskStringBuilder = StringBuilder()
        if (tasks.isNotEmpty()) {
            taskStringBuilder.append("Tasks for $date:\n")
            for (task in tasks) {
                taskStringBuilder.append("- $task\n")
            }
        } else {
            taskStringBuilder.append("No tasks for $date")
        }
        taskListTextView.text = taskStringBuilder.toString()
    }

    private fun showRemoveTaskDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Remove Task")

        val tasksForDate = taskListForSelectedDate.getTasks(dateTV.text.toString())
        val checkedItems = BooleanArray(tasksForDate.size)
        builder.setMultiChoiceItems(tasksForDate.toTypedArray(), checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }

        builder.setPositiveButton("Remove") { dialog, _ ->
            val selectedDate = dateTV.text.toString()
            val tasksToRemove = mutableListOf<String>()
            tasksForDate.forEachIndexed { index, task ->
                if (checkedItems[index]) {
                    tasksToRemove.add(task)
                }
            }
            tasksToRemove.forEach { task ->
                taskManager.removeTask(selectedDate, task)
                taskListForSelectedDate.removeTask(selectedDate, task)
            }
            updateTaskListForSelectedDate(selectedDate)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    data class Task(val description: String, val date: String, var time: String)

    inner class TaskManager(private val context: Context) {
        private val tasks = mutableListOf<Task>()
        private val sharedPreferences = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)

        init {
            loadTasks()
        }

        fun addTask(date: String, taskName: String, time: String) {
            tasks.add(Task(taskName, date, time))
            saveTasks()
        }

        fun getTasksForDate(date: String): List<Task> {
            return tasks.filter { it.date == date }
        }

        private fun saveTasks() {
            val editor = sharedPreferences.edit()
            val gson = Gson()
            val json = gson.toJson(tasks)
            editor.putString("taskList", json)
            editor.apply()
        }

        private fun loadTasks() {
            val gson = Gson()
            val json = sharedPreferences.getString("taskList", null)
            val type = object : TypeToken<List<Task>>() {}.type
            tasks.addAll(gson.fromJson(json, type) ?: emptyList())
        }

        fun removeTask(date: String, taskName: String) {
            tasks.removeAll { it.date == date && it.description == taskName }
            saveTasks()
        }
    }

    inner class TaskListForSelectedDate(private val context: Context) {
        private val tasksMap: MutableMap<String, MutableList<String>> = mutableMapOf()
        private val sharedPreferences = context.getSharedPreferences("tasksMap", Context.MODE_PRIVATE)

        init {
            loadTasksMap()
        }

        fun addTask(date: String, task: String) {
            val tasksForDate = tasksMap.getOrPut(date) { mutableListOf() }
            tasksForDate.add(task)
            saveTasksMap()
        }

        fun getTasks(date: String): List<String> {
            return tasksMap[date] ?: emptyList()
        }

        private fun saveTasksMap() {
            val editor = sharedPreferences.edit()
            val gson = Gson()
            val json = gson.toJson(tasksMap)
            editor.putString("taskMap", json)
            editor.apply()
        }

        private fun loadTasksMap() {
            val gson = Gson()
            val json = sharedPreferences.getString("taskMap", null)
            val type = object : TypeToken<MutableMap<String, MutableList<String>>>() {}.type
            tasksMap.putAll(gson.fromJson(json, type) ?: emptyMap())
        }

        fun removeTask(date: String, task: String) {
            tasksMap[date]?.remove(task)
            saveTasksMap()
        }
    }
}