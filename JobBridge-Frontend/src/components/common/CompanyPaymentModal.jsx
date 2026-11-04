import React from 'react';
import { loadTossPayments } from '@tosspayments/payment-sdk';
import client from '../../api/client'; // 기존 프로젝트의 axios 클라이언트 경로 유지

// 💡 환경 변수에서 토스 클라이언트 키 불러오기 (Vite 기준)
const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY;

export default function CompanyPaymentModal({ product, companyUser }) {
    const handlePayment = async () => {
        try {
            const res = await client.post('/company/payments/orders', {
                productType: product.type,
                amount: product.price,
            });
            const { orderId, amount, orderName } = res.data;

            const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY);

            await tossPayments.requestPayment('카드', {
                amount: amount,
                orderId: orderId,
                orderName: orderName,
                customerName: companyUser?.companyName || '기업회원',
                successUrl: `${window.location.origin}/company/payment/success`,
                failUrl: `${window.location.origin}/company/payment/fail`,
            });
        } catch (err) {
            console.error(err);
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