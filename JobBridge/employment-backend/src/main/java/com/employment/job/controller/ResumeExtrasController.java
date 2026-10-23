package com.employment.job.controller;

import com.employment.auth.AuthService;
import com.employment.global.common.*;
import com.employment.job.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ResumeExtrasController {
    private final EmploymentService service;
    private final ResumeFiles files;
    private final Assessments assessments;
    private final AuthService auth;
    private final ObjectMapper json;
    public ResumeExtrasController(EmploymentService service,ResumeFiles files,Assessments assessments,AuthService auth,ObjectMapper json) {
        this.service=service;this.files=files;this.assessments=assessments;this.auth=auth;this.json=json;
    }
    @GetMapping("/assessments/questions")
    Object questions() { return assessments.questions(); }
    @GetMapping("/resumes/{id}/attachment")
    ResponseEntity<byte[]> resume(@PathVariable long id,HttpServletRequest req) {
        var resume=service.resume(auth.require(req,"JOB_SEEKER"),id);
        if (!(resume.get("attachment") instanceof Map<?,?> attachment)) throw new Problem(404,"첨부파일을 찾을 수 없습니다.");
        return download(((Number)attachment.get("fileId")).longValue());
    }
    @GetMapping("/applications/{id}/attachment")
    ResponseEntity<byte[]> application(@PathVariable long id,HttpServletRequest req) {
        var user=auth.user(req);
        long owner=Database.number(user,"userId");
        Map<String,Object> app;
        if("COMPANY".equals(user.get("role"))) app=service.companyApplication(owner,id);
        else if("JOB_SEEKER".equals(user.get("role"))) app=service.application(owner,id);
        else throw new Problem(403,"첨부파일을 조회할 권한이 없습니다.");
        var snapshot=json.valueToTree(app.get("snapshot"));
        var file=snapshot.path("resume").path("attachment").path("fileId");
        if(!file.isIntegralNumber()) throw new Problem(404,"제출된 첨부파일이 없습니다.");
        return download(file.asLong());
    }
    private ResponseEntity<byte[]> download(long id) {
        var file=files.read(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PDF)
            .header("X-Content-Type-Options","nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.name(),StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }
}
