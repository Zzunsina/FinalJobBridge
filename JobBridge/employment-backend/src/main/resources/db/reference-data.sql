-- Public reference FAQ examples; preserves existing rows on repeat initialization.
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'ACCOUNT','ALL','회원가입은 어떻게 하나요?','상단 회원가입에서 개인 또는 기업 유형을 선택해 주세요. 아이디, 이메일, 휴대폰번호 등 필수 정보를 입력하고 필수 약관에 동의하면 가입할 수 있습니다.',1 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='회원가입은 어떻게 하나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'ACCOUNT','ALL','비밀번호를 변경하고 싶어요.','로그인 후 내 정보에서 현재 비밀번호와 새 비밀번호를 입력해 변경할 수 있습니다. 변경 후 다시 로그인해 주세요. 비밀번호를 잊은 경우 고객센터에 문의해 주세요.',2 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='비밀번호를 변경하고 싶어요.');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'ACCOUNT','ALL','회원탈퇴는 어디에서 하나요?','내 정보의 회원탈퇴에서 비밀번호를 확인한 뒤 진행할 수 있습니다. 기업회원은 기업 정보에서도 탈퇴할 수 있습니다. 탈퇴 시 로그인할 수 없으며 기존 계정과 활동 기록은 보관됩니다.',3 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='회원탈퇴는 어디에서 하나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'RESUME','JOB_SEEKER','이력서는 어디에서 작성하나요?','로그인 후 이력서 관리에서 새 이력서를 작성할 수 있습니다. 학력·경력·프로젝트·보유 기술 등을 입력하고 미리보기에서 내용을 확인해 주세요.',4 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='이력서는 어디에서 작성하나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'RESUME','JOB_SEEKER','이력서에 프로필 사진을 넣고 싶어요.','내 정보에서 프로필 사진을 선택한 뒤 사진 저장을 눌러 주세요. 저장한 사진은 이력서 작성 화면과 미리보기에 표시됩니다.',5 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='이력서에 프로필 사진을 넣고 싶어요.');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'RESUME','JOB_SEEKER','지원한 뒤 이력서를 수정하면 제출 내용도 바뀌나요?','지원 당시 제출한 이력서와 연락처는 별도로 보관됩니다. 이후 원본 이력서를 수정해도 기존 지원서의 제출 내용은 바뀌지 않습니다.',6 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='지원한 뒤 이력서를 수정하면 제출 내용도 바뀌나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'APPLICATION','JOB_SEEKER','입사지원은 어떻게 하나요?','채용공고 상세에서 지원 가능한 공고인지 확인한 뒤 이력서와 자기소개서를 선택·작성하여 지원해 주세요. 개인회원 로그인이 필요합니다.',7 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='입사지원은 어떻게 하나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'APPLICATION','JOB_SEEKER','지원 결과와 면접 일정은 어디에서 보나요?','지원 내역에서 해당 지원을 열면 진행 상태와 면접 일정을 확인할 수 있습니다. 상태 변경 및 면접 안내는 내 알림에서도 확인해 주세요.',8 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='지원 결과와 면접 일정은 어디에서 보나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'APPLICATION','JOB_SEEKER','입사지원을 취소할 수 있나요?','지원 상세에서 취소 가능한 상태라면 지원 취소 버튼이 표시됩니다. 취소하면 예정된 면접도 취소되며 같은 공고에 재지원할 수 없으니 확인 후 진행해 주세요.',9 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='입사지원을 취소할 수 있나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'JOBS','COMPANY','기업 승인은 언제 확인할 수 있나요?','기업 정보를 등록하면 관리자 심사가 진행됩니다. 기업 정보에서 승인 상태를 확인할 수 있으며 심사 결과는 기업 계정의 내 알림으로 안내됩니다.',10 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='기업 승인은 언제 확인할 수 있나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'JOBS','COMPANY','공고를 저장했는데 공개 목록에 보이지 않아요.','공고는 초안으로 저장됩니다. 심사를 요청하고 관리자가 승인해야 공개됩니다. 기업 승인과 공고 심사 상태를 함께 확인해 주세요.',11 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='공고를 저장했는데 공개 목록에 보이지 않아요.');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'JOBS','COMPANY','기업 대표 이미지는 어디에서 등록하나요?','기업 정보 또는 공고 작성 화면에서 기업 이미지를 선택하고 사진 저장을 눌러 주세요. 승인된 기업의 이미지는 공고 목록·상세와 공개 기업 정보에 표시됩니다.',12 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='기업 대표 이미지는 어디에서 등록하나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'OTHER','JOB_SEEKER','취업연계 서비스는 무엇인가요?','상담과 기업 매칭을 통해 취업 준비를 지원하는 서비스입니다. 개인회원으로 로그인한 뒤 취업연계에서 신청하면 진행 상황과 담당자의 공개 안내를 확인할 수 있습니다.',13 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='취업연계 서비스는 무엇인가요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'OTHER','ALL','문의 내용은 다른 사람에게 공개되나요?','1:1 문의는 공개 게시판이 아닙니다. 작성한 회원과 관리자만 내용을 확인할 수 있습니다. 답변이 등록되면 내 알림으로 안내됩니다.',14 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='문의 내용은 다른 사람에게 공개되나요?');
INSERT INTO customer_faqs(category,audience,question,answer,sort_order)
SELECT 'OTHER','ALL','날짜를 선택했는데 값이 바뀌지 않아요.','달력에서 날짜나 연월, 면접 시간을 선택한 뒤 하단의 선택 완료를 눌러 주세요. 취소하면 기존 값이 유지됩니다.',15 WHERE NOT EXISTS (SELECT 1 FROM customer_faqs WHERE question='날짜를 선택했는데 값이 바뀌지 않아요.');
