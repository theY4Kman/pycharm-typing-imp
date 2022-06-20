package com.github.they4kman.pycharmtypingimp.services

import com.intellij.openapi.project.Project
import com.github.they4kman.pycharmtypingimp.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
