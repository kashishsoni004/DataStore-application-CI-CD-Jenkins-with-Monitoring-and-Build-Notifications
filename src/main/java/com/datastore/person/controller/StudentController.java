package com.datastore.person.controller;

import com.datastore.person.pojo.Student;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class StudentController {

    private List<Student> studentList = new ArrayList<>();

    @RequestMapping(method = RequestMethod.POST, path = "/student/post")
    private ResponseEntity<String> postStudent(@RequestBody Student student, HttpServletRequest request) {
        System.out.println(request.getHeader("test"));
        studentList.add(student);
        return ResponseEntity.status(HttpStatus.OK).body("Student successfully posted.");
    }

    @RequestMapping(method = RequestMethod.GET, path = "/student/get/{name}")
    private ResponseEntity<Student> getStudent(@PathVariable("name") String name) {
        List<Student> stuList = studentList.stream().filter(stu ->
                stu.getName().equals(name)
        ).collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.OK).body(stuList.get(0));
    }
}