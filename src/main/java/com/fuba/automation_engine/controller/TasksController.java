package com.fuba.automation_engine.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TasksController {
    
    @PostMapping("/tasks")
    public String createTask() {
        // Logic to create a task
        return "Task created successfully!";    
    }
}
