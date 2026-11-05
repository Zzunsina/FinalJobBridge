package com.employment.job.service;

import com.employment.global.common.Problem;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class ReviewPrivacy {
 private ReviewPrivacy() {}
 private static final Pattern EMAIL=Pattern.compile("[A-Z0-9._%+\\-]+\\s*@\\s*[A-Z0-9\\-]+(?:\\s*\\.\\s*[A-Z0-9\\-]+)+",Pattern.CASE_INSENSITIVE);
 private static final Pattern PHONE=Pattern.compile("(?<![0-9])(?:0(?:1[016789]|2|[3-6][1-5]|70)|\\+?82[\\s().\\-]*(?:1[016789]|2|[3-6][1-5]|70))[\\s().\\-]*[0-9]{3,4}[\\s().\\-]*[0-9]{4}(?![0-9])");
 public static void validate(String field,Object value){String text=Normalizer.normalize(String.valueOf(value),Normalizer.Form.NFKC).replaceAll("[\\u200B-\\u200D\\uFEFF]","");if(EMAIL.matcher(text).find()||PHONE.matcher(text).find())throw new Problem(400,"전화번호나 이메일이 포함되어 있습니다. 개인정보를 지운 뒤 다시 요청해 주세요.",field);}
}
