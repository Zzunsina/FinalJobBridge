import React, { useEffect, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import axios from 'axios';

export default function CompanyPaymentSuccess() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const isProcessed = useRef(false);

  useEffect(() => {
    if (isProcessed.current) return;

    const paymentKey = searchParams.get('paymentKey');
    const orderId = searchParams.get('orderId');
    const amount = searchParams.get('amount');

    console.log('토스 결제 성공 리다이렉트 진입 - 파라미터:', { paymentKey, orderId, amount });

    if (!paymentKey || !orderId || !amount) {
      alert('결제 승인 정보가 누락되었습니다.');
      navigate('/company/payment/shop');
      return;
    }

    isProcessed.current = true;

    // 💡 명시적인 /api/company/payments/confirm 경로로 승인 요청 전송
    axios.post('/api/company/payments/confirm', {
      paymentKey: paymentKey,
      orderId: orderId,
      amount: Number(amount)
    }, {
      withCredentials: true // 쿠키 및 세션 인증 정보 유지
    })
    .then((res) => {
      console.log('결제 승인 성공:', res.data);
      alert('결제가 성공적으로 완료되었습니다!');
      navigate('/company');
    })
    .catch((err) => {
      console.error('결제 승인 실패 상세:', err.response || err);
      alert('결제 승인 검증 실패: ' + (err.response?.data?.message || err.message));
      navigate('/company/payment/shop');
    });

  }, [searchParams, navigate]);

  return (
    <div style={{ textAlign: 'center', marginTop: '100px' }}>
      <h2>결제 승인을 처리하고 있습니다. 잠시만 기다려 주세요...</h2>
    </div>
  );
}