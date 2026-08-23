package com.pymobileide.domain.models

data class Project(
    val id: String,
    val name: String,
    val path: String,
    val pythonVersion: String = "3.11",
    val entryPoint: String = "main.py",
    val lastRunTime: Long = 0L
)
