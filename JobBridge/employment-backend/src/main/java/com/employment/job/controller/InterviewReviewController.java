package com.employment.job.controller;
import com.employment.auth.AuthService;
import com.employment.global.common.Database;
import com.employment.job.service.InterviewReviews;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api")
public class InterviewReviewController {
 private final InterviewReviews reviews;private final AuthService auth;
 public InterviewReviewController(InterviewReviews reviews,AuthService auth){this.reviews=reviews;this.auth=auth;}
 @GetMapping("/companies/{id}/interview-reviews") Object published(@PathVariable long id){return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(reviews.published(id));}
 @GetMapping("/me/interview-reviews") Object own(HttpServletRequest req){return reviews.own(auth.require(req,"JOB_SEEKER"));}
 @PostMapping("/interviews/{id}/review") Object write(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return reviews.write(auth.require(req,"JOB_SEEKER"),id,body);}
 @PutMapping("/interview-reviews/{id}") void edit(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.edit(auth.require(req,"JOB_SEEKER"),id,body);}
 @DeleteMapping("/interview-reviews/{id}") void delete(@PathVariable long id,HttpServletRequest req){reviews.delete(auth.require(req,"JOB_SEEKER"),id);}
 @PostMapping("/interviews/{id}/attendance-request") Object request(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){return reviews.request(auth.require(req,"JOB_SEEKER"),id,body);}
 @PostMapping("/attendance-requests/{id}/escalate") void escalate(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.escalate(auth.require(req,"JOB_SEEKER"),id,body);}
 @GetMapping("/attendance-requests/{id}/evidence") Object evidence(@PathVariable long id,HttpServletRequest req){var user=auth.user(req);return reviews.evidenceFile(Database.number(user,"userId"),"ADMIN".equals(user.get("role")),id);}
 @GetMapping("/company/attendance-requests") Object companyRequests(HttpServletRequest req){return reviews.companyRequests(auth.require(req,"COMPANY"));}
 @PostMapping("/company/attendance-requests/{id}/resolve") void companyResolve(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.companyRespond(auth.require(req,"COMPANY"),id,body);}
 @GetMapping("/company/interview-reviews") Object companyReviews(HttpServletRequest req){long user=auth.require(req,"COMPANY");return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(reviews.published(companyId(user)));}
 private long companyId(long user){return reviews.companyId(user);}
 @PostMapping("/company/reports") Object report(@RequestBody Map<String,Object> body,HttpServletRequest req){return reviews.report(auth.require(req,"COMPANY"),body);}
 @GetMapping("/company/reports") Object reports(HttpServletRequest req){return reviews.reports(auth.require(req,"COMPANY"),false);}
 @GetMapping("/admin/reports") Object adminReports(HttpServletRequest req){return reviews.reports(auth.require(req,"ADMIN"),true);}
 @PostMapping("/admin/reports/{id}/resolve") void resolve(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.resolve(auth.require(req,"ADMIN"),id,body);}
 @GetMapping("/admin/attendance-requests") Object adminRequests(HttpServletRequest req){auth.require(req,"ADMIN");return reviews.adminRequests();}
 @PostMapping("/admin/attendance-requests/{id}/resolve") void adminResolve(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.adminRespond(auth.require(req,"ADMIN"),id,body);}
 @GetMapping("/admin/interview-reviews") Object moderation(HttpServletRequest req){auth.require(req,"ADMIN");return reviews.moderationList();}
 @PostMapping("/admin/interview-reviews/{id}/moderate") void moderate(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req){reviews.moderate(auth.require(req,"ADMIN"),id,body);}
}
