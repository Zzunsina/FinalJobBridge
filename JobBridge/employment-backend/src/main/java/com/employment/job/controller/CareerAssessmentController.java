package com.employment.job.controller;
import com.employment.auth.AuthService;
import com.employment.job.service.CareerAssessments;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/career-assessments")
public class CareerAssessmentController {
 private final CareerAssessments service;private final AuthService auth;
 public CareerAssessmentController(CareerAssessments service,AuthService auth){this.service=service;this.auth=auth;}
 @GetMapping Object history(HttpServletRequest req){return service.history(auth.require(req,"JOB_SEEKER"));}
 @GetMapping("/submission") Object submission(HttpServletRequest req){return com.employment.global.common.Database.fields("assessment",service.latestSubmission(auth.require(req,"JOB_SEEKER")));}
 @PostMapping Object start(HttpServletRequest req){return service.start(auth.require(req,"JOB_SEEKER"));}
 @GetMapping("/{id}") Object get(@PathVariable long id,HttpServletRequest req){return service.get(auth.require(req,"JOB_SEEKER"),id);}
 @PutMapping("/{id}/answers") Object save(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return service.save(auth.require(req,"JOB_SEEKER"),id,body,false);}
 @PostMapping("/{id}/submit") Object submit(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return service.save(auth.require(req,"JOB_SEEKER"),id,body,true);}
}
