package com.employment.admin;

import com.employment.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HomePromotionController {
    private final HomePromotionService service;
    private final AuthService auth;
    public HomePromotionController(HomePromotionService service,AuthService auth){this.service=service;this.auth=auth;}
    @GetMapping("/home-promotions") Object published(){return service.list(false);}
    @GetMapping("/admin/home-promotions") Object all(HttpServletRequest req){auth.require(req,"ADMIN");return service.list(true);}
    @PostMapping("/admin/home-promotions") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object create(@RequestBody Map<String,Object> body,HttpServletRequest req){auth.require(req,"ADMIN");return service.create(body);}
    @DeleteMapping("/admin/home-promotions/{key}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void delete(@PathVariable String key,@RequestBody Map<String,Object> body,HttpServletRequest req){auth.require(req,"ADMIN");service.delete(key,body);}
    @PutMapping("/admin/home-promotions/{key}") Object save(@PathVariable String key,@RequestBody Map<String,Object> body,HttpServletRequest req){auth.require(req,"ADMIN");return service.save(key,body);}
    @GetMapping("/home-promotions/{key}/image") Object image(@PathVariable String key){return service.image(key,false);}
    @GetMapping("/admin/home-promotions/{key}/image") Object adminImage(@PathVariable String key,HttpServletRequest req){auth.require(req,"ADMIN");return service.image(key,true);}
}
