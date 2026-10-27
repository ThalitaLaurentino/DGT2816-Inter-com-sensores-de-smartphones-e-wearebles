package com.example.listadetarefas

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView

class MainActivity : Activity() {

    private lateinit var tasks: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tasks = mutableListOf(
            getString(R.string.task_example_1),
            getString(R.string.task_example_2),
            getString(R.string.task_example_3)
        )

        val listView = findViewById<ListView>(R.id.taskList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, tasks)
        listView.adapter = adapter

        findViewById<Button>(R.id.addButton).setOnClickListener {
            tasks.add(getString(R.string.new_task, tasks.size + 1))
            adapter.notifyDataSetChanged()
            listView.smoothScrollToPosition(tasks.size - 1)
        }
    }
}
