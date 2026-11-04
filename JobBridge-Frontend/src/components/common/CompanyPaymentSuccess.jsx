import React, { useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { client } from '../../api/client';

export default function CompanyPaymentSuccess() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const paymentKey = searchParams.get('paymentKey');
    const orderId = searchParams.get('orderId');
    const amount = searchParams.get('amount');

    // 백엔드로 최종 승인 요청
    client.post('/api/company/payments/confirm', {
      paymentKey,
      orderId,
      amount: Number(amount),
    })
    .then(() => {
      alert('결제가 성공적으로 완료되었습니다!');
      navigate('/company/mypage'); // 기업 마이페이지로 이동
    })
    .catch((err) => {
      alert('결제 승인 검증 실패: ' + (err.response?.data?.message || '다시 시도해 주세요.'));
      navigate('/company/payment/fail');
    });
  }, [searchParams, navigate]);

  return <div>결제 승인을 처리하고 있습니다. 잠시만 기다려 주세요...</div>;
}