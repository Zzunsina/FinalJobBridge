package com.employment.global.common;
import org.springframework.web.bind.annotation.*;
@RestController
public class SiteImageController {
 private final SiteImageAssets images;
 public SiteImageController(SiteImageAssets images){this.images=images;}
 @GetMapping("/api/site-images/{*path}")
 public org.springframework.http.ResponseEntity<byte[]> image(@PathVariable String path){return images.image(path);}
}
