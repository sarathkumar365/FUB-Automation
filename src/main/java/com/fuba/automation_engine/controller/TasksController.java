package com.fuba.automation_engine.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TasksController {

    private static final Logger log = LoggerFactory.getLogger(TasksController.class);

    @PostMapping("/tasks")
    public String createTask() {
        log.info("Manual task creation endpoint invoked");
        // Logic to create a task
        return "Task created successfully!";
    }
}
