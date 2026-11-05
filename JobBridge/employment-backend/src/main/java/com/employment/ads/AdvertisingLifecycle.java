package com.employment.ads;

import com.employment.global.common.Database;
import com.employment.global.common.Problem;
import static com.employment.global.common.Database.*;
import java.time.*;

/** Keeps advertising eligibility separate from the actual payment/refund ledger. */
public final class AdvertisingLifecycle {
 private AdvertisingLifecycle() {}
 public static void validateDates(Database db,long job,LocalDate start,LocalDate end) {
  var row=db.one("SELECT MIN(start_date) AS first_day,MAX(end_date) AS last_day FROM ad_orders WHERE job_id=? AND review_status IN ('PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','PAUSED') AND payment_status IN ('READY','FAILED','PAID') AND refund_status='NONE' AND end_date>=?",job,LocalDate.now(ZoneId.of("Asia/Seoul")));
  if(row!=null&&row.get("firstDay")!=null&&(start.isAfter(LocalDate.parse(row.get("firstDay").toString()))||end.isBefore(LocalDate.parse(row.get("lastDay").toString()))))
   throw new Problem(409,"광고 신청 기간이 포함되도록 공고 접수 기간을 설정해 주세요. 광고 기간보다 공고 날짜를 줄일 수 없습니다.","endDate");
 }
 public static void refresh(Database db) {
  var today=LocalDate.now(ZoneId.of("Asia/Seoul"));
  var rows=db.list("SELECT o.order_id,o.company_id,o.review_status,o.payment_status FROM ad_orders o LEFT JOIN job_postings j ON j.job_id=o.job_id WHERE o.job_id IS NOT NULL AND (o.review_status IN ('PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','PAUSED') OR (o.review_status='JOB_CLOSED' AND o.payment_status='PAID')) AND o.payment_status IN ('READY','FAILED','PAID') AND o.refund_status='NONE' AND o.end_date>=? AND (j.job_id IS NULL OR j.status='CLOSED' OR j.end_date<? OR j.start_date>o.start_date OR j.end_date<o.end_date)",today,today);
  for(var o:rows){
   boolean paid="PAID".equals(o.get("paymentStatus"));String next=paid?"JOB_STOPPED":"JOB_CLOSED";
   String message=paid?"연결 공고가 마감되거나 접수 기간이 변경되어 광고 노출이 중단되었습니다. 환불 검토가 필요합니다.":"연결 공고가 마감되거나 접수 기간이 변경되어 광고 신청이 취소되었습니다. 결제는 진행되지 않습니다.";
   int changed=db.update("UPDATE ad_orders SET review_status=?,review_memo=?,updated_at=CURRENT_TIMESTAMP WHERE order_id=? AND review_status=? AND payment_status=?",next,message,o.get("orderId"),o.get("reviewStatus"),o.get("paymentStatus"));
   if(changed==0)continue;
   var owner=db.one("SELECT user_id FROM companies WHERE company_id=?",o.get("companyId"));
   if(owner!=null)notice(db,number(owner,"userId"),message);
   if(paid)for(var admin:db.list("SELECT user_id FROM users WHERE role='ADMIN' AND status='ACTIVE'"))notice(db,number(admin,"userId"),"공고 마감으로 유료 광고가 중단되었습니다. 주문 "+o.get("orderId")+"의 환불 여부를 검토해 주세요.");
  }
 }
 private static void notice(Database db,long user,String text){db.insert("notifications",fields("user_id",user,"title","공고 변경에 따른 광고 상태 안내","content",text,"notification_type","ADVERTISEMENT"));}
}
