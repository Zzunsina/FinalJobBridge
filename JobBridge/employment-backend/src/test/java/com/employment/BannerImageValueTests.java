package com.employment.job.service;
import static org.assertj.core.api.Assertions.*;
import com.employment.global.common.Problem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
class BannerImageValueTests {
 static String photo() throws Exception {
  var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB);var random=new Random(42);
  for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,random.nextInt());
  var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);
  return "data:image/png;base64,"+Base64.getEncoder().encodeToString(output.toByteArray());
 }
 @Test void uploadedImageLargerThanOldUrlLimitIsAccepted() throws Exception {
  String image=photo();assertThat(image.length()).isGreaterThan(2000);
  assertThat(BannerImageValue.validate(Map.of("bannerImageUrl",image))).isEqualTo(image);
 }
 @Test void emptyBannerAndHttpsUrlAreAccepted() {
  assertThat(BannerImageValue.validate(Map.of())).isEmpty();
  assertThat(BannerImageValue.validate(Map.of("bannerImageUrl","https://example.com/banner.jpg"))).isEqualTo("https://example.com/banner.jpg");
 }
 @Test void executableSchemesAndInvalidImagePayloadsAreRejected() {
  for(String value:List.of("javascript:alert(1)","data:image/svg+xml;base64,PHN2Zz4=","data:image/png;base64,aGVsbG8=","data:image/jpeg;base64,invalid"))
   assertThatThrownBy(()->BannerImageValue.validate(Map.of("bannerImageUrl",value))).isInstanceOf(Problem.class);
 }
}
