package com.employment.job.controller;

import com.employment.auth.AuthService;
import com.employment.job.service.EmploymentReferrals;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EmploymentReferralController {
    private final com.employment.job.service.EmploymentInterviewProposals proposals;
    private final com.employment.job.service.EmploymentOnboarding onboarding;
    private final AuthService auth;
    private final EmploymentReferrals referrals;
    public EmploymentReferralController(AuthService auth, EmploymentReferrals referrals, com.employment.job.service.EmploymentInterviewProposals proposals, com.employment.job.service.EmploymentOnboarding onboarding) { this.auth=auth; this.referrals=referrals; this.proposals=proposals; this.onboarding=onboarding; }
    @GetMapping("/admin/employment-links/{id}/referrals")
    Object history(@PathVariable long id,HttpServletRequest req) { auth.require(req,"ADMIN"); return referrals.history(id); }
    @PostMapping("/admin/employment-links/{id}/referrals")
    Object send(@PathVariable long id,HttpServletRequest req) { auth.require(req,"ADMIN"); return referrals.send(id); }
    @PostMapping("/admin/employment-links/{id}/rematch")
    void rematch(@PathVariable long id,HttpServletRequest req) { auth.require(req,"ADMIN"); referrals.cancel(id); }
    @PostMapping("/admin/employment-links/{id}/cancel")
    void cancel(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) {
        referrals.cancelApplication(auth.require(req,"ADMIN"),id,body);
    }
    @GetMapping("/company/employment-referrals/{id}")
    Object detail(@PathVariable long id,HttpServletRequest req) { return referrals.companyDetail(auth.require(req,"COMPANY"),id); }
    @PatchMapping("/company/employment-referrals/{id}/response")
    void respond(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { referrals.respond(auth.require(req,"COMPANY"),id,body); }
    @PostMapping("/company/employment-referrals/{id}/proposals")
    Object propose(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { return proposals.propose(auth.require(req,"COMPANY"),id,body); }
    @PatchMapping("/employment-interview-proposals/{id}/response")
    void proposalResponse(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { proposals.respond(auth.require(req,"JOB_SEEKER"),id,body); }
    @PutMapping("/company/employment-referrals/{id}/onboarding")
    void saveOnboarding(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { onboarding.save(auth.require(req,"COMPANY"),id,body); }
    @PostMapping("/employment-referrals/{id}/onboarding-actions")
    void onboardingAction(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { onboarding.userAction(auth.require(req,"JOB_SEEKER"),id,body); }
    @PatchMapping("/employment-referrals/{id}/participation")
    void participation(@PathVariable long id,@RequestBody Map<String,Object> body,HttpServletRequest req) { referrals.userParticipation(auth.require(req,"JOB_SEEKER"),id,body); }
}
