import React from 'react';
import { loadTossPayments } from '@tosspayments/payment-sdk';
import { client } from '../../api/client'; // axios 인스턴스

// 토스페이먼츠 개발자센터에서 발급받은 테스트 클라이언트 키
const TOSS_CLIENT_KEY = 'test_ck_docs_Oabc123456789';

export default function CompanyPaymentModal({ product, companyUser }) {
  const handlePayment = async () => {
    try {
      // 1. 서버에 주문 사전 생성 (위변조 방지)
      const res = await client.post('/api/company/orders', {
        productType: product.type, // 예: 'RESUME_READ_50'
        amount: product.price,
      });
      const { orderId, amount, orderName } = res.data;

      // 2. 토스 SDK 로드 및 결제창 호출
      const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY);

      await tossPayments.requestPayment('카드', {
        amount: amount,
        orderId: orderId,
        orderName: orderName,
        customerName: companyUser.companyName,
        successUrl: `${window.location.origin}/company/payment/success`,
        failUrl: `${window.location.origin}/company/payment/fail`,
      });
    } catch (err) {
      alert('결제 준비 중 오류가 발생했습니다: ' + (err.response?.data?.message || err.message));
    }
  };

  return (
    <div className="payment-card">
      <h3>{product.name}</h3>
      <p>{product.price.toLocaleString()}원</p>
      <button type="button" onClick={handlePayment}>
        결제하고 혜택 받기
      </button>
    </div>
  );
}